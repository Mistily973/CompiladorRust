package Optimizacion;

import Analizador_sintactico.AST;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimización 1: ELIMINACIÓN DE CÓDIGO MUERTO (sobre el AST).
 *
 * Transformaciones que aplica:
 *  1. Código inalcanzable: sentencias después de return / break / continue / panic!
 *     (o después de un if/else/match/loop donde todos los caminos terminan así).
 *  2. Condiciones constantes: if true/false, if 2 > 1, if con una variable inmutable
 *     inicializada con un literal... se conserva solo la rama que se ejecuta.
 *  3. while false { ... } nunca se ejecuta -> se elimina.
 *  4. Variables sin uso: 'let' cuya variable nunca se lee (y sus asignaciones). Si el
 *     inicializador tiene efectos secundarios (llamada a función, macro...) se conserva
 *     solo la expresión.
 *  5. Expresiones sin efecto (sentencias como "a + b;") y bloques / if / for vacíos.
 *  6. Brazos de match inalcanzables (los que siguen a un brazo '_').
 *  7. Funciones que nunca se alcanzan desde main, y structs que nadie usa.
 *
 * Se repite hasta que ya no haya cambios (punto fijo), porque quitar una cosa suele
 * dejar otra sin uso (por ejemplo, al borrar un if constante la variable que usaba
 * queda muerta).
 *
 * Suposiciones: la división/módulo se considera pura salvo que el divisor sea el
 * literal 0, y el acceso por índice (v[i]) se considera con efectos (puede entrar en
 * pánico), por lo que no se elimina.
 *
 * IMPORTANTE: modifica el AST que recibe. Pásale una copia recién parseada.
 */
public class EliminadorCodigoMuerto {

    // ======================================================================
    // Estado
    // ======================================================================
    private final List<Cambio> cambios = new ArrayList<>();
    private boolean huboCambio;
    private Set<String> variablesPropagadas = Collections.emptySet();   // las sustituyó el plegado de constantes
    private boolean conservarResultado = false;        // proteger el 'let' final de funciones sin retorno
    private int iteracion;                              // 1 = primera pasada sobre el programa recibido
    private Set<Object> muertas;                       // DeclLet cuya variable nunca se lee
    private final List<Map<String, Object>> ambitos = new ArrayList<>();

    private static final Pattern FORMATO_INLINE = Pattern.compile("(?<!\\{)\\{([A-Za-z_][A-Za-z0-9_]*)[^}]*\\}");
    private static final Set<String> MACROS_DIVERGENTES = new HashSet<>(Arrays.asList("panic", "unreachable", "todo", "unimplemented"));

    // ======================================================================
    // Punto de entrada
    // ======================================================================
    public void setVariablesPropagadas(Set<String> nombres) { variablesPropagadas = nombres; }

    /**
     * Si es true, en las funciones sin valor de retorno cuyo último paso es un
     * 'let' (p. ej. let r = (a + b) * c;) esa variable se considera el RESULTADO
     * FINAL de la función y no se elimina aunque nadie la lea.
     */
    public void setConservarResultado(boolean conservar) { conservarResultado = conservar; }

    private AST.DeclLet variableResultado(AST.DeclFuncion f) {
        if (f.nombre.equals("main") || f.tipoRetornoTexto != null) return null;
        AST.Bloque b = f.cuerpo;
        if (b.exprFinal != null || b.sentencias.isEmpty()) return null;
        AST.Sentencia ultima = b.sentencias.get(b.sentencias.size() - 1);
        return ultima instanceof AST.DeclLet ? (AST.DeclLet) ultima : null;
    }

    public List<Cambio> aplicar(AST.Programa programa) {
        boolean hayMain = programa.funciones.stream().anyMatch(f -> f.nombre.equals("main"));
        int guarda = 0;
        iteracion = 0;
        do {
            iteracion++;
            huboCambio = false;
            Analisis an = new Analisis(programa);
            an.analizar();
            muertas = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Map.Entry<Object, Integer> en : an.lecturas.entrySet()) {
                if (en.getKey() instanceof AST.DeclLet && en.getValue() == 0) muertas.add(en.getKey());
            }
            if (conservarResultado) {
                for (AST.DeclFuncion f : programa.funciones) {
                    AST.DeclLet resultado = variableResultado(f);
                    if (resultado != null) muertas.remove(resultado);
                }
            }
            if (hayMain) quitarFuncionesNoUsadas(programa, an);
            for (AST.DeclFuncion f : programa.funciones) transformarFuncion(f);
        } while (huboCambio && ++guarda < 100);

        if (hayMain) quitarEstructurasNoUsadas(programa);

        cambios.sort(Comparator.comparingInt(c -> c.linea));
        return cambios;
    }

    private void registrar(int linea, String tipo, String detalle) {
        huboCambio = true;
        cambios.add(new Cambio(linea, Optimizador.CODIGO_MUERTO, tipo, detalle));
    }

    // ======================================================================
    // FASE DE ANÁLISIS: cuenta lecturas de cada variable y llamadas entre funciones
    // ======================================================================
    private static class Analisis {
        final AST.Programa programa;
        final Map<Object, Integer> lecturas = new IdentityHashMap<>();
        final Map<String, Set<String>> llamadas = new HashMap<>();
        final Set<String> nombresFunciones = new HashSet<>();
        final List<Map<String, Object>> ambitos = new ArrayList<>();
        Set<String> actual;

        Analisis(AST.Programa p) {
            programa = p;
            for (AST.DeclFuncion f : p.funciones) nombresFunciones.add(f.nombre);
        }

        void analizar() {
            for (AST.DeclFuncion f : programa.funciones) {
                actual = new HashSet<>();
                llamadas.put(f.nombre, actual);
                ambitos.clear();
                ambitos.add(new HashMap<>());
                for (AST.Parametro p : f.parametros) declarar(p.nombre, p);
                bloque(f.cuerpo);
            }
        }

        void declarar(String n, Object d) { ambitos.get(ambitos.size() - 1).put(n, d); }

        Object buscar(String n) {
            for (int i = ambitos.size() - 1; i >= 0; i--) {
                Object d = ambitos.get(i).get(n);
                if (d != null) return d;
            }
            return null;
        }

        void usar(String n) {
            Object d = buscar(n);
            if (d != null) lecturas.merge(d, 1, Integer::sum);
            else if (nombresFunciones.contains(n)) actual.add(n);
        }

        void bloque(AST.Bloque b) {
            ambitos.add(new HashMap<>());
            for (AST.Sentencia s : b.sentencias) sentencia(s);
            if (b.exprFinal != null) expr(b.exprFinal);
            ambitos.remove(ambitos.size() - 1);
        }

        void sentencia(AST.Sentencia s) {
            if (s instanceof AST.DeclLet) {
                AST.DeclLet d = (AST.DeclLet) s;
                if (d.inicializador != null) expr(d.inicializador);
                declarar(d.nombre, d);
                lecturas.putIfAbsent(d, 0);
            } else if (s instanceof AST.SentExpr) {
                expr(((AST.SentExpr) s).expr);
            } else if (s instanceof AST.SentReturn) {
                if (((AST.SentReturn) s).valor != null) expr(((AST.SentReturn) s).valor);
            } else if (s instanceof AST.SentSalto) {
                if (((AST.SentSalto) s).valor != null) expr(((AST.SentSalto) s).valor);
            }
        }

        void exprs(List<AST.Expr> es) { for (AST.Expr e : es) expr(e); }

        void expr(AST.Expr e) {
            if (e == null) return;
            if (e instanceof AST.Identificador) { usar(((AST.Identificador) e).nombre); }
            else if (e instanceof AST.Unario) expr(((AST.Unario) e).operando);
            else if (e instanceof AST.Binario) { expr(((AST.Binario) e).izquierda); expr(((AST.Binario) e).derecha); }
            else if (e instanceof AST.Cast) expr(((AST.Cast) e).expr);
            else if (e instanceof AST.Asignacion) {
                AST.Asignacion a = (AST.Asignacion) e;
                // Asignar a una variable NO es leerla.
                if (!(a.objetivo instanceof AST.Identificador)) expr(a.objetivo);
                expr(a.valor);
            }
            else if (e instanceof AST.Llamada) { expr(((AST.Llamada) e).callee); exprs(((AST.Llamada) e).argumentos); }
            else if (e instanceof AST.LlamadaMacro) {
                AST.LlamadaMacro m = (AST.LlamadaMacro) e;
                exprs(m.argumentos);
                for (AST.Expr a : m.argumentos) {   // println!("{x}") lee x desde la cadena
                    if (a instanceof AST.Literal && "STRING".equals(((AST.Literal) a).tokenTipo)) {
                        Matcher mt = FORMATO_INLINE.matcher(((AST.Literal) a).texto);
                        while (mt.find()) usar(mt.group(1));
                    }
                }
            }
            else if (e instanceof AST.LlamadaMetodo) { expr(((AST.LlamadaMetodo) e).receptor); exprs(((AST.LlamadaMetodo) e).argumentos); }
            else if (e instanceof AST.AccesoCampo) expr(((AST.AccesoCampo) e).objeto);
            else if (e instanceof AST.AccesoIndice) { expr(((AST.AccesoIndice) e).objeto); expr(((AST.AccesoIndice) e).indice); }
            else if (e instanceof AST.Tupla) exprs(((AST.Tupla) e).elementos);
            else if (e instanceof AST.ArregloLiteral) exprs(((AST.ArregloLiteral) e).elementos);
            else if (e instanceof AST.InstanciaStruct) { for (AST.CampoInit c : ((AST.InstanciaStruct) e).campos) expr(c.valor); }
            else if (e instanceof AST.Rango) { expr(((AST.Rango) e).inicio); expr(((AST.Rango) e).fin); }
            else if (e instanceof AST.ExprIf) {
                AST.ExprIf i = (AST.ExprIf) e;
                expr(i.condicion); bloque(i.entonces); expr(i.sino);
            }
            else if (e instanceof AST.ExprMatch) {
                AST.ExprMatch m = (AST.ExprMatch) e;
                expr(m.sujeto);
                for (AST.BrazoMatch b : m.brazos) {
                    ambitos.add(new HashMap<>());
                    if ("IDENTIFICADOR".equals(b.patron.tokenTipo)) declarar(b.patron.texto, b);
                    expr(b.cuerpo);
                    ambitos.remove(ambitos.size() - 1);
                }
            }
            else if (e instanceof AST.ExprBloque) bloque(((AST.ExprBloque) e).bloque);
            else if (e instanceof AST.ExprLoop) bloque(((AST.ExprLoop) e).cuerpo);
            else if (e instanceof AST.ExprWhile) { expr(((AST.ExprWhile) e).condicion); bloque(((AST.ExprWhile) e).cuerpo); }
            else if (e instanceof AST.ExprFor) {
                AST.ExprFor f = (AST.ExprFor) e;
                expr(f.iterable);
                ambitos.add(new HashMap<>());
                declarar(f.variable, f);
                bloque(f.cuerpo);
                ambitos.remove(ambitos.size() - 1);
            }
        }
    }

    // ======================================================================
    // FUNCIONES Y STRUCTS SIN USO
    // ======================================================================
    private void quitarFuncionesNoUsadas(AST.Programa p, Analisis an) {
        Set<String> alcanzables = new HashSet<>();
        Deque<String> pendientes = new ArrayDeque<>();
        pendientes.add("main");
        while (!pendientes.isEmpty()) {
            String f = pendientes.poll();
            if (!alcanzables.add(f)) continue;
            for (String g : an.llamadas.getOrDefault(f, Collections.emptySet())) pendientes.add(g);
        }
        Iterator<AST.DeclFuncion> it = p.funciones.iterator();
        while (it.hasNext()) {
            AST.DeclFuncion f = it.next();
            if (!alcanzables.contains(f.nombre)) {
                it.remove();
                registrar(f.linea, "Función no utilizada",
                    "La función '" + f.nombre + "' nunca se llama desde main (ni directa ni indirectamente): se elimina.");
            }
        }
    }

    private void quitarEstructurasNoUsadas(AST.Programa p) {
        Set<String> usados = new HashSet<>();
        for (AST.DeclFuncion f : p.funciones) {
            for (AST.Parametro prm : f.parametros) nombresDeTipo(prm.tipoTexto, usados);
            nombresDeTipo(f.tipoRetornoTexto, usados);
            recolectarNombresBloque(f.cuerpo, usados);
        }
        boolean crece = true;           // un struct usado puede usar a otros en sus campos
        while (crece) {
            crece = false;
            for (AST.DeclStruct s : p.estructuras) {
                if (usados.contains(s.nombre)) {
                    for (AST.CampoDecl c : s.campos) {
                        Set<String> tmp = new HashSet<>();
                        nombresDeTipo(c.tipoTexto, tmp);
                        if (usados.addAll(tmp)) crece = true;
                    }
                }
            }
        }
        Iterator<AST.DeclStruct> it = p.estructuras.iterator();
        while (it.hasNext()) {
            AST.DeclStruct s = it.next();
            if (!usados.contains(s.nombre)) {
                it.remove();
                registrar(s.linea, "Estructura no utilizada", "La estructura '" + s.nombre + "' nunca se usa: se elimina.");
            }
        }
    }

    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private void nombresDeTipo(String tipo, Set<String> out) {
        if (tipo == null) return;
        Matcher m = IDENT.matcher(tipo);
        while (m.find()) out.add(m.group());
    }

    private void recolectarNombresBloque(AST.Bloque b, Set<String> out) {
        for (AST.Sentencia s : b.sentencias) {
            if (s instanceof AST.DeclLet) {
                nombresDeTipo(((AST.DeclLet) s).tipoTexto, out);
                recolectarNombresExpr(((AST.DeclLet) s).inicializador, out);
            } else if (s instanceof AST.SentExpr) recolectarNombresExpr(((AST.SentExpr) s).expr, out);
            else if (s instanceof AST.SentReturn) recolectarNombresExpr(((AST.SentReturn) s).valor, out);
            else if (s instanceof AST.SentSalto) recolectarNombresExpr(((AST.SentSalto) s).valor, out);
        }
        recolectarNombresExpr(b.exprFinal, out);
    }

    private void recolectarNombresExpr(AST.Expr e, Set<String> out) {
        if (e == null) return;
        if (e instanceof AST.InstanciaStruct) {
            AST.InstanciaStruct is = (AST.InstanciaStruct) e;
            out.add(is.nombreStruct);
            for (AST.CampoInit c : is.campos) recolectarNombresExpr(c.valor, out);
        } else if (e instanceof AST.Ruta) out.add(((AST.Ruta) e).segmentos.get(0));
        else if (e instanceof AST.Cast) { nombresDeTipo(((AST.Cast) e).tipoTexto, out); recolectarNombresExpr(((AST.Cast) e).expr, out); }
        else if (e instanceof AST.Unario) recolectarNombresExpr(((AST.Unario) e).operando, out);
        else if (e instanceof AST.Binario) { recolectarNombresExpr(((AST.Binario) e).izquierda, out); recolectarNombresExpr(((AST.Binario) e).derecha, out); }
        else if (e instanceof AST.Asignacion) { recolectarNombresExpr(((AST.Asignacion) e).objetivo, out); recolectarNombresExpr(((AST.Asignacion) e).valor, out); }
        else if (e instanceof AST.Llamada) { recolectarNombresExpr(((AST.Llamada) e).callee, out); for (AST.Expr a : ((AST.Llamada) e).argumentos) recolectarNombresExpr(a, out); }
        else if (e instanceof AST.LlamadaMacro) { for (AST.Expr a : ((AST.LlamadaMacro) e).argumentos) recolectarNombresExpr(a, out); }
        else if (e instanceof AST.LlamadaMetodo) { recolectarNombresExpr(((AST.LlamadaMetodo) e).receptor, out); for (AST.Expr a : ((AST.LlamadaMetodo) e).argumentos) recolectarNombresExpr(a, out); }
        else if (e instanceof AST.AccesoCampo) recolectarNombresExpr(((AST.AccesoCampo) e).objeto, out);
        else if (e instanceof AST.AccesoIndice) { recolectarNombresExpr(((AST.AccesoIndice) e).objeto, out); recolectarNombresExpr(((AST.AccesoIndice) e).indice, out); }
        else if (e instanceof AST.Tupla) { for (AST.Expr a : ((AST.Tupla) e).elementos) recolectarNombresExpr(a, out); }
        else if (e instanceof AST.ArregloLiteral) { for (AST.Expr a : ((AST.ArregloLiteral) e).elementos) recolectarNombresExpr(a, out); }
        else if (e instanceof AST.Rango) { recolectarNombresExpr(((AST.Rango) e).inicio, out); recolectarNombresExpr(((AST.Rango) e).fin, out); }
        else if (e instanceof AST.ExprIf) { AST.ExprIf i = (AST.ExprIf) e; recolectarNombresExpr(i.condicion, out); recolectarNombresBloque(i.entonces, out); recolectarNombresExpr(i.sino, out); }
        else if (e instanceof AST.ExprMatch) { recolectarNombresExpr(((AST.ExprMatch) e).sujeto, out); for (AST.BrazoMatch b : ((AST.ExprMatch) e).brazos) recolectarNombresExpr(b.cuerpo, out); }
        else if (e instanceof AST.ExprBloque) recolectarNombresBloque(((AST.ExprBloque) e).bloque, out);
        else if (e instanceof AST.ExprLoop) recolectarNombresBloque(((AST.ExprLoop) e).cuerpo, out);
        else if (e instanceof AST.ExprWhile) { recolectarNombresExpr(((AST.ExprWhile) e).condicion, out); recolectarNombresBloque(((AST.ExprWhile) e).cuerpo, out); }
        else if (e instanceof AST.ExprFor) { recolectarNombresExpr(((AST.ExprFor) e).iterable, out); recolectarNombresBloque(((AST.ExprFor) e).cuerpo, out); }
    }

    // ======================================================================
    // FASE DE TRANSFORMACIÓN
    // ======================================================================
    private void declarar(String n, Object d) { ambitos.get(ambitos.size() - 1).put(n, d); }

    private Object buscar(String n) {
        for (int i = ambitos.size() - 1; i >= 0; i--) {
            Object d = ambitos.get(i).get(n);
            if (d != null) return d;
        }
        return null;
    }

    private void transformarFuncion(AST.DeclFuncion f) {
        ambitos.clear();
        ambitos.add(new HashMap<>());
        for (AST.Parametro p : f.parametros) declarar(p.nombre, p);
        f.cuerpo = bloque(f.cuerpo);
    }

    private AST.Bloque bloque(AST.Bloque b) {
        ambitos.add(new HashMap<>());
        List<AST.Sentencia> salida = new ArrayList<>();
        boolean diverge = false;

        for (AST.Sentencia s : b.sentencias) {
            if (diverge) {
                registrar(s.linea, "Código inalcanzable",
                    "Esta sentencia nunca se ejecuta: el flujo ya terminó antes (return, break, continue o pánico).");
                continue;
            }
            int antes = salida.size();
            procesarSentencia(s, salida);
            for (int i = antes; i < salida.size(); i++) {
                if (divergeSentencia(salida.get(i))) diverge = true;
            }
        }

        if (b.exprFinal != null) {
            if (diverge) {
                registrar(b.exprFinal.linea, "Código inalcanzable",
                    "La expresión final del bloque nunca se alcanza: el flujo ya terminó antes.");
                b.exprFinal = null;
            } else {
                b.exprFinal = expr(b.exprFinal);
                // Un 'if' sin else y sin cuerpo vale () igual que no poner nada: se elimina
                if (b.exprFinal instanceof AST.ExprIf) {
                    AST.ExprIf i = (AST.ExprIf) b.exprFinal;
                    if (i.sino == null && vacio(i.entonces)) {
                        registrar(i.linea, "Condicional vacío", "El 'if' no tiene cuerpo: se elimina.");
                        if (!esPura(i.condicion)) salida.add(sentExpr(i.condicion, i.linea));
                        b.exprFinal = null;
                    }
                }
            }
        }
        b.sentencias = salida;
        ambitos.remove(ambitos.size() - 1);
        return b;
    }

    /** Primera pasada: la variable no se usa en el programa. Pasadas siguientes: se quedó sin uso por otra eliminación. */
    private String textoSinUso(String nombre) {
        if (variablesPropagadas.contains(nombre))
            return "La variable '" + nombre + "' ya no se lee (el plegado de constantes sustituyó su valor donde se usaba)";
        return iteracion == 1
            ? "La variable '" + nombre + "' no se usa en ninguna parte del programa"
            : "La variable '" + nombre + "' quedó sin uso al eliminar el código que la leía";
    }

    private void procesarSentencia(AST.Sentencia s, List<AST.Sentencia> out) {
        if (s instanceof AST.DeclLet) {
            AST.DeclLet d = (AST.DeclLet) s;
            if (d.inicializador != null) d.inicializador = expr(d.inicializador);
            declarar(d.nombre, d);
            if (muertas.contains(d)) {
                if (d.inicializador == null || esPura(d.inicializador)) {
                    registrar(d.linea, "Variable sin uso",
                        textoSinUso(d.nombre) + ": se elimina su declaración.");
                } else {
                    registrar(d.linea, "Variable sin uso",
                        textoSinUso(d.nombre) + ": se elimina la variable, pero se conserva su expresión por sus efectos secundarios.");
                    out.add(sentExpr(d.inicializador, d.linea));
                }
                return;
            }
            out.add(d);
        } else if (s instanceof AST.SentReturn) {
            AST.SentReturn r = (AST.SentReturn) s;
            if (r.valor != null) r.valor = expr(r.valor);
            out.add(r);
        } else if (s instanceof AST.SentSalto) {
            AST.SentSalto j = (AST.SentSalto) s;
            if (j.valor != null) j.valor = expr(j.valor);
            out.add(j);
        } else if (s instanceof AST.SentExpr) {
            sentenciaExpr((AST.SentExpr) s, out);
        }
    }

    private void sentenciaExpr(AST.SentExpr se, List<AST.Sentencia> out) {
        AST.Expr e = se.expr;

        // Asignación a una variable que nunca se lee
        if (e instanceof AST.Asignacion) {
            AST.Asignacion a = (AST.Asignacion) e;
            if (a.objetivo instanceof AST.Identificador) {
                Object d = buscar(((AST.Identificador) a.objetivo).nombre);
                if (d != null && muertas.contains(d)) {
                    a.valor = expr(a.valor);
                    registrar(se.linea, "Asignación sin efecto",
                        "Se asigna a '" + ((AST.Identificador) a.objetivo).nombre + "', una variable que nunca se lee: se elimina la asignación.");
                    if (!esPura(a.valor)) out.add(sentExpr(a.valor, se.linea));
                    return;
                }
            }
        }

        AST.Expr n = expr(e);
        boolean cambio = n != e;

        // Un if constante que quedó convertido en bloque: se inserta su contenido directamente
        if (cambio && n instanceof AST.ExprBloque) {
            AST.Bloque bl = ((AST.ExprBloque) n).bloque;
            if (bl.sentencias.stream().noneMatch(x -> x instanceof AST.DeclLet)) {
                out.addAll(bl.sentencias);
                if (bl.exprFinal != null && !esPura(bl.exprFinal)) out.add(sentExpr(bl.exprFinal, se.linea));
                return;
            }
        }

        if (n instanceof AST.ExprBloque && vacio(((AST.ExprBloque) n).bloque)) {
            if (!cambio) registrar(se.linea, "Bloque vacío", "Se elimina un bloque sin contenido.");
            return;
        }
        if (n instanceof AST.ExprIf) {
            AST.ExprIf i = (AST.ExprIf) n;
            if (i.sino == null && vacio(i.entonces)) {
                registrar(se.linea, "Condicional vacío", "El 'if' no tiene cuerpo: se elimina.");
                if (!esPura(i.condicion)) out.add(sentExpr(i.condicion, se.linea));
                return;
            }
        }
        if (n instanceof AST.ExprFor) {
            AST.ExprFor f = (AST.ExprFor) n;
            if (vacio(f.cuerpo) && esPura(f.iterable)) {
                registrar(se.linea, "Ciclo vacío", "El 'for' no tiene cuerpo: se elimina.");
                return;
            }
        }
        if (esPura(n)) {
            if (!cambio) registrar(se.linea, "Expresión sin efecto", "La expresión no produce ningún efecto: se elimina.");
            return;
        }
        se.expr = n;
        out.add(se);
    }

    private AST.SentExpr sentExpr(AST.Expr e, int linea) {
        AST.SentExpr s = new AST.SentExpr();
        s.linea = linea;
        s.expr = e;
        return s;
    }

    private boolean vacio(AST.Bloque b) { return b.sentencias.isEmpty() && b.exprFinal == null; }

    private AST.Tupla unidad(int linea) {
        AST.Tupla t = new AST.Tupla();
        t.linea = linea;
        t.elementos = new ArrayList<>();
        return t;
    }

    /** Convierte un bloque en expresión: { x } -> x ; {} -> () */
    private AST.Expr valorBloque(AST.Bloque b) {
        if (b.sentencias.isEmpty() && b.exprFinal != null) return b.exprFinal;
        if (b.sentencias.isEmpty()) return unidad(b.linea);
        AST.ExprBloque eb = new AST.ExprBloque();
        eb.linea = b.linea;
        eb.bloque = b;
        return eb;
    }

    // ----------------------------------------------------------------------
    // Transformación de expresiones
    // ----------------------------------------------------------------------
    private AST.Expr expr(AST.Expr e) {
        if (e == null) return null;
        if (e instanceof AST.Unario) { ((AST.Unario) e).operando = expr(((AST.Unario) e).operando); }
        else if (e instanceof AST.Binario) { AST.Binario b = (AST.Binario) e; b.izquierda = expr(b.izquierda); b.derecha = expr(b.derecha); }
        else if (e instanceof AST.Cast) { ((AST.Cast) e).expr = expr(((AST.Cast) e).expr); }
        else if (e instanceof AST.Asignacion) { AST.Asignacion a = (AST.Asignacion) e; a.objetivo = expr(a.objetivo); a.valor = expr(a.valor); }
        else if (e instanceof AST.Llamada) { AST.Llamada l = (AST.Llamada) e; l.callee = expr(l.callee); lista(l.argumentos); }
        else if (e instanceof AST.LlamadaMacro) { lista(((AST.LlamadaMacro) e).argumentos); }
        else if (e instanceof AST.LlamadaMetodo) { AST.LlamadaMetodo m = (AST.LlamadaMetodo) e; m.receptor = expr(m.receptor); lista(m.argumentos); }
        else if (e instanceof AST.AccesoCampo) { ((AST.AccesoCampo) e).objeto = expr(((AST.AccesoCampo) e).objeto); }
        else if (e instanceof AST.AccesoIndice) { AST.AccesoIndice a = (AST.AccesoIndice) e; a.objeto = expr(a.objeto); a.indice = expr(a.indice); }
        else if (e instanceof AST.Tupla) { lista(((AST.Tupla) e).elementos); }
        else if (e instanceof AST.ArregloLiteral) { lista(((AST.ArregloLiteral) e).elementos); }
        else if (e instanceof AST.InstanciaStruct) { for (AST.CampoInit c : ((AST.InstanciaStruct) e).campos) c.valor = expr(c.valor); }
        else if (e instanceof AST.Rango) { AST.Rango r = (AST.Rango) e; r.inicio = expr(r.inicio); r.fin = expr(r.fin); }
        else if (e instanceof AST.ExprIf) return exprIf((AST.ExprIf) e);
        else if (e instanceof AST.ExprMatch) exprMatch((AST.ExprMatch) e);
        else if (e instanceof AST.ExprBloque) { AST.ExprBloque eb = (AST.ExprBloque) e; eb.bloque = bloque(eb.bloque); }
        else if (e instanceof AST.ExprLoop) { AST.ExprLoop l = (AST.ExprLoop) e; l.cuerpo = bloque(l.cuerpo); }
        else if (e instanceof AST.ExprWhile) {
            AST.ExprWhile w = (AST.ExprWhile) e;
            w.condicion = expr(w.condicion);
            Boolean c = constante(w.condicion, true);
            if (Boolean.FALSE.equals(c)) {
                registrar(w.linea, "Ciclo que nunca se ejecuta", "La condición del 'while' siempre es false: se elimina el ciclo completo.");
                return unidad(w.linea);
            }
            w.cuerpo = bloque(w.cuerpo);
        }
        else if (e instanceof AST.ExprFor) {
            AST.ExprFor f = (AST.ExprFor) e;
            f.iterable = expr(f.iterable);
            ambitos.add(new HashMap<>());
            declarar(f.variable, f);
            f.cuerpo = bloque(f.cuerpo);
            ambitos.remove(ambitos.size() - 1);
        }
        return e;
    }

    private void lista(List<AST.Expr> es) {
        for (int i = 0; i < es.size(); i++) es.set(i, expr(es.get(i)));
    }

    private AST.Expr exprIf(AST.ExprIf i) {
        i.condicion = expr(i.condicion);
        Boolean c = constante(i.condicion, true);
        if (c != null) {
            String motivo = "La condición del 'if' siempre es " + c;
            if (c) registrar(i.linea, "Condición constante", motivo + ": se conserva solo el cuerpo y se quita el 'if'.");
            else if (i.sino != null) registrar(i.linea, "Condición constante", motivo + ": se conserva solo la rama 'else'.");
            else registrar(i.linea, "Condición constante", motivo + " y no tiene 'else': se elimina el 'if' completo.");
            if (c) return valorBloque(bloque(i.entonces));
            if (i.sino == null) return unidad(i.linea);
            AST.Expr resto = expr(i.sino);
            return resto;
        }
        i.entonces = bloque(i.entonces);
        if (i.sino != null) {
            i.sino = expr(i.sino);
            if (i.sino instanceof AST.ExprBloque && vacio(((AST.ExprBloque) i.sino).bloque)) {
                registrar(i.sino.linea, "Bloque vacío", "Se elimina un 'else' sin contenido.");
                i.sino = null;
            }
        }
        return i;
    }

    private void exprMatch(AST.ExprMatch m) {
        m.sujeto = expr(m.sujeto);
        List<AST.BrazoMatch> nuevos = new ArrayList<>();
        boolean comodin = false;
        for (AST.BrazoMatch b : m.brazos) {
            if (comodin) {
                registrar(b.linea, "Brazo inalcanzable", "El brazo '" + b.patron.texto + "' nunca se evalúa: va después de un brazo '_'.");
                continue;
            }
            ambitos.add(new HashMap<>());
            if ("IDENTIFICADOR".equals(b.patron.tokenTipo)) declarar(b.patron.texto, b);
            b.cuerpo = expr(b.cuerpo);
            ambitos.remove(ambitos.size() - 1);
            nuevos.add(b);
            if ("_".equals(b.patron.texto) || "IDENTIFICADOR".equals(b.patron.tokenTipo)) comodin = true;
        }
        m.brazos = nuevos;
    }

    // ----------------------------------------------------------------------
    // Pureza (¿se puede descartar sin cambiar el comportamiento?)
    // ----------------------------------------------------------------------
    private boolean esPura(AST.Expr e) {
        if (e == null) return true;
        if (e instanceof AST.Literal || e instanceof AST.Identificador || e instanceof AST.Ruta) return true;
        if (e instanceof AST.Unario) return esPura(((AST.Unario) e).operando);
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            if ((b.operador.equals("/") || b.operador.equals("%")) && esCero(b.derecha)) return false;
            return esPura(b.izquierda) && esPura(b.derecha);
        }
        if (e instanceof AST.Cast) return esPura(((AST.Cast) e).expr);
        if (e instanceof AST.AccesoCampo) return esPura(((AST.AccesoCampo) e).objeto);
        if (e instanceof AST.Tupla) return todasPuras(((AST.Tupla) e).elementos);
        if (e instanceof AST.ArregloLiteral) return todasPuras(((AST.ArregloLiteral) e).elementos);
        if (e instanceof AST.InstanciaStruct) {
            for (AST.CampoInit c : ((AST.InstanciaStruct) e).campos) if (!esPura(c.valor)) return false;
            return true;
        }
        if (e instanceof AST.Rango) return esPura(((AST.Rango) e).inicio) && esPura(((AST.Rango) e).fin);
        return false; // llamadas, macros, asignaciones, índices, ciclos, if, match...
    }

    private boolean todasPuras(List<AST.Expr> es) {
        for (AST.Expr x : es) if (!esPura(x)) return false;
        return true;
    }

    private boolean esCero(AST.Expr e) {
        Double n = numero(e, false);
        return n != null && n == 0.0;
    }

    // ----------------------------------------------------------------------
    // Evaluación de condiciones constantes
    //   resolver = true  -> permite usar variables inmutables inicializadas con literales
    // ----------------------------------------------------------------------
    private Boolean constante(AST.Expr e, boolean resolver) {
        if (e instanceof AST.Literal) {
            AST.Literal l = (AST.Literal) e;
            if ("BOOLEANO".equals(l.tokenTipo)) return l.texto.equals("true");
            return null;
        }
        if (e instanceof AST.Identificador && resolver) {
            Object d = buscar(((AST.Identificador) e).nombre);
            if (d instanceof AST.DeclLet) {
                AST.DeclLet dl = (AST.DeclLet) d;
                if (!dl.esMutable && dl.inicializador != null) return constante(dl.inicializador, false);
            }
            return null;
        }
        if (e instanceof AST.Unario && ((AST.Unario) e).operador.equals("!")) {
            Boolean v = constante(((AST.Unario) e).operando, resolver);
            return v == null ? null : !v;
        }
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            if (b.operador.equals("&&")) {
                Boolean l = constante(b.izquierda, resolver);
                if (Boolean.FALSE.equals(l)) return false;
                if (Boolean.TRUE.equals(l)) return constante(b.derecha, resolver);
                return null;
            }
            if (b.operador.equals("||")) {
                Boolean l = constante(b.izquierda, resolver);
                if (Boolean.TRUE.equals(l)) return true;
                if (Boolean.FALSE.equals(l)) return constante(b.derecha, resolver);
                return null;
            }
            if ("OP_COMP".equals(b.tokenTipo)) {
                Double l = numero(b.izquierda, resolver), r = numero(b.derecha, resolver);
                if (l == null || r == null) return null;
                switch (b.operador) {
                    case "==": return l.doubleValue() == r.doubleValue();
                    case "!=": return l.doubleValue() != r.doubleValue();
                    case "<":  return l < r;
                    case ">":  return l > r;
                    case "<=": return l <= r;
                    case ">=": return l >= r;
                }
            }
        }
        return null;
    }

    private static final Pattern SUFIJO_NUM = Pattern.compile("(i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize|f32|f64)$");

    private Double numero(AST.Expr e, boolean resolver) {
        if (e instanceof AST.Literal) {
            AST.Literal l = (AST.Literal) e;
            if (!"ENTERO".equals(l.tokenTipo) && !"FLOTANTE".equals(l.tokenTipo)) return null;
            String t = l.texto.replace("_", "");
            try {
                if (t.startsWith("0x")) return (double) Long.parseLong(t.substring(2), 16);
                if (t.startsWith("0b")) return (double) Long.parseLong(t.substring(2), 2);
                if (t.startsWith("0o")) return (double) Long.parseLong(t.substring(2), 8);
                return Double.parseDouble(SUFIJO_NUM.matcher(t).replaceFirst(""));
            } catch (NumberFormatException ex) { return null; }
        }
        if (e instanceof AST.Identificador && resolver) {
            Object d = buscar(((AST.Identificador) e).nombre);
            if (d instanceof AST.DeclLet) {
                AST.DeclLet dl = (AST.DeclLet) d;
                if (!dl.esMutable && dl.inicializador != null) return numero(dl.inicializador, false);
            }
            return null;
        }
        if (e instanceof AST.Unario && ((AST.Unario) e).operador.equals("-")) {
            Double v = numero(((AST.Unario) e).operando, resolver);
            return v == null ? null : -v;
        }
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            Double l = numero(b.izquierda, resolver), r = numero(b.derecha, resolver);
            if (l == null || r == null) return null;
            switch (b.operador) {
                case "+": return l + r;
                case "-": return l - r;
                case "*": return l * r;
            }
        }
        return null;
    }

    // ----------------------------------------------------------------------
    // Divergencia (el flujo nunca continúa después de esta sentencia)
    // ----------------------------------------------------------------------
    private boolean divergeSentencia(AST.Sentencia s) {
        if (s instanceof AST.SentReturn || s instanceof AST.SentSalto) return true;
        if (s instanceof AST.SentExpr) return divergeExpr(((AST.SentExpr) s).expr);
        return false;
    }

    private boolean divergeBloque(AST.Bloque b) {
        for (AST.Sentencia s : b.sentencias) if (divergeSentencia(s)) return true;
        return b.exprFinal != null && divergeExpr(b.exprFinal);
    }

    private boolean divergeExpr(AST.Expr e) {
        if (e instanceof AST.LlamadaMacro) return MACROS_DIVERGENTES.contains(((AST.LlamadaMacro) e).nombre);
        if (e instanceof AST.ExprBloque) return divergeBloque(((AST.ExprBloque) e).bloque);
        if (e instanceof AST.ExprIf) {
            AST.ExprIf i = (AST.ExprIf) e;
            return i.sino != null && divergeBloque(i.entonces) && divergeExpr(i.sino);
        }
        if (e instanceof AST.ExprMatch) {
            AST.ExprMatch m = (AST.ExprMatch) e;
            if (m.brazos.isEmpty()) return false;
            for (AST.BrazoMatch b : m.brazos) if (!divergeExpr(b.cuerpo)) return false;
            return true;
        }
        if (e instanceof AST.ExprLoop) return !contieneBreak(((AST.ExprLoop) e).cuerpo);
        return false;
    }

    /** ¿Hay un 'break' que pertenezca a este loop? (no se mira dentro de loops anidados) */
    private boolean contieneBreak(AST.Bloque b) {
        for (AST.Sentencia s : b.sentencias) {
            if (s instanceof AST.SentSalto && ((AST.SentSalto) s).tipo.equals("break")) return true;
            if (s instanceof AST.SentSalto && contieneBreakExpr(((AST.SentSalto) s).valor)) return true;
            if (s instanceof AST.SentExpr && contieneBreakExpr(((AST.SentExpr) s).expr)) return true;
            if (s instanceof AST.DeclLet && contieneBreakExpr(((AST.DeclLet) s).inicializador)) return true;
            if (s instanceof AST.SentReturn && contieneBreakExpr(((AST.SentReturn) s).valor)) return true;
        }
        return contieneBreakExpr(b.exprFinal);
    }

    private boolean contieneBreakExpr(AST.Expr e) {
        if (e == null) return false;
        if (e instanceof AST.ExprBloque) return contieneBreak(((AST.ExprBloque) e).bloque);
        if (e instanceof AST.ExprIf) {
            AST.ExprIf i = (AST.ExprIf) e;
            return contieneBreakExpr(i.condicion) || contieneBreak(i.entonces) || contieneBreakExpr(i.sino);
        }
        if (e instanceof AST.ExprMatch) {
            for (AST.BrazoMatch b : ((AST.ExprMatch) e).brazos) if (contieneBreakExpr(b.cuerpo)) return true;
            return false;
        }
        if (e instanceof AST.ExprLoop || e instanceof AST.ExprWhile || e instanceof AST.ExprFor) return false;
        if (e instanceof AST.Asignacion) return contieneBreakExpr(((AST.Asignacion) e).valor);
        return false;
    }
}
