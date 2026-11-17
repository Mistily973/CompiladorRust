package Optimizacion;

import Analizador_sintactico.AST;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimización 2: CONSTANT FOLDING (plegado de constantes) sobre el AST.
 *
 * Calcula en tiempo de compilación las expresiones cuyos operandos son todos
 * constantes y las reemplaza por su resultado:
 *     (2 + 3) * 4        ->  20
 *     10 > 3             ->  true
 *     7 as f64           ->  7.0
 *     (2 + 3).pow(2)     ->  25
 *     !false && 1 < 2    ->  true
 *
 * Además se hace PROPAGACIÓN DE CONSTANTES: una variable inmutable inicializada con
 * una constante (let a = 2;) se sustituye por su valor donde se use. Así
 * (a + b) * c con a = 2, b = 3, c = 4 también se pliega a 20. Se puede desactivar
 * con new PlegadoConstantes(false).
 *
 * Reglas de seguridad:
 *  - Un resultado entero solo se pliega si cabe en su tipo (i32 si el literal no
 *    lleva sufijo o anotación de tipo); si no, se deja para que falle en ejecución.
 *  - La división o módulo entre 0 no se pliega.
 *  - No se pliegan valores f32 (se calculan en double y el resultado podría diferir).
 *  - Las variables mutables nunca se propagan.
 *  - Una variable propagada conserva su tipo con sufijo (let x: u8 = 5 -> 5u8).
 *
 * Modifica el AST que recibe.
 */
public class PlegadoConstantes {

    // ======================================================================
    // Valores constantes
    // ======================================================================
    private enum T { INT, FLOAT, BOOL }

    private static class Const {
        final T tipo;
        final long i;
        final double d;
        final boolean b;
        final String sufijo;   // i64, u8, f64... o null

        private Const(T t, long i, double d, boolean b, String s) { tipo = t; this.i = i; this.d = d; this.b = b; sufijo = s; }
        static Const entero(long v, String s) { return new Const(T.INT, v, 0, false, s); }
        static Const real(double v, String s) { return new Const(T.FLOAT, 0, v, false, s); }
        static Const bool(boolean v) { return new Const(T.BOOL, 0, 0, v, null); }
    }

    private final boolean propagar;
    private final List<Cambio> cambios = new ArrayList<>();
    private final Set<String> variablesPropagadas = new HashSet<>();
    private final GeneradorCodigoRust gen = new GeneradorCodigoRust();
    private final List<Map<String, Const>> ambitos = new ArrayList<>();   // valor null = variable no constante (o sombreada)

    public PlegadoConstantes() { this(true); }
    public PlegadoConstantes(boolean propagar) { this.propagar = propagar; }

    /** Nombres de variables cuyo valor constante se usó en lugar de leerlas. */
    public Set<String> getVariablesPropagadas() { return variablesPropagadas; }

    public List<Cambio> aplicar(AST.Programa programa) {
        for (AST.DeclFuncion f : programa.funciones) {
            ambitos.clear();
            ambitos.add(new HashMap<>());
            for (AST.Parametro p : f.parametros) declarar(p.nombre, null);

            // Valor que devuelve la función ANTES de plegar (para guardar el resultado en una variable)
            Set<String> letsDelCuerpo = new HashSet<>();
            for (AST.Sentencia st : f.cuerpo.sentencias) if (st instanceof AST.DeclLet) letsDelCuerpo.add(((AST.DeclLet) st).nombre);
            AST.Expr finalOriginal = f.cuerpo.exprFinal;
            AST.SentReturn returnFinal = ultimoReturn(f.cuerpo);
            AST.Expr returnOriginal = returnFinal == null ? null : returnFinal.valor;

            bloque(f.cuerpo);

            if (f.tipoRetornoTexto != null) {
                if (finalOriginal != null && f.cuerpo.exprFinal != finalOriginal && esLiteralSimple(f.cuerpo.exprFinal)
                        && !esLiteralSimple(finalOriginal)) {
                    String nombre = nombreResultado(finalOriginal, letsDelCuerpo, f.nombre);
                    AST.Expr valor = f.cuerpo.exprFinal;
                    f.cuerpo.sentencias.add(declaracion(nombre, valor, finalOriginal.linea));
                    f.cuerpo.exprFinal = identificador(nombre, finalOriginal.linea);
                    registrarResultado(f, nombre, valor, finalOriginal.linea);
                } else if (returnFinal != null && returnOriginal != null && returnFinal.valor != returnOriginal
                        && esLiteralSimple(returnFinal.valor) && !esLiteralSimple(returnOriginal)) {
                    String nombre = nombreResultado(returnOriginal, letsDelCuerpo, f.nombre);
                    AST.Expr valor = returnFinal.valor;
                    f.cuerpo.sentencias.add(f.cuerpo.sentencias.size() - 1, declaracion(nombre, valor, returnOriginal.linea));
                    returnFinal.valor = identificador(nombre, returnOriginal.linea);
                    registrarResultado(f, nombre, valor, returnOriginal.linea);
                }
            }
        }
        cambios.sort(Comparator.comparingInt(c -> c.linea));
        return cambios;
    }

    // ----------------------------------------------------------------------
    // Resultado final de la función guardado en una variable declarada
    //    fn f() -> i32 { let a = 4; let b = 5; let area = a * b; area + 5 }
    //    ->  fn f() -> i32 { let area = 25; area }
    // ----------------------------------------------------------------------
    private AST.SentReturn ultimoReturn(AST.Bloque b) {
        if (b.exprFinal != null || b.sentencias.isEmpty()) return null;
        AST.Sentencia ultima = b.sentencias.get(b.sentencias.size() - 1);
        return ultima instanceof AST.SentReturn ? (AST.SentReturn) ultima : null;
    }

    /**
     * Nombre de la variable que guardará el resultado: la variable local de la expresión original
     * cuyo nombre aparece en el de la función (area en calcular_area); si ninguna coincide, "resultado".
     */
    private String nombreResultado(AST.Expr original, Set<String> letsDelCuerpo, String nombreFuncion) {
        List<String> usadas = new ArrayList<>();
        recolectarIdentificadores(original, letsDelCuerpo, usadas);
        for (String v : usadas) if (nombreFuncion.contains(v)) return v;
        return "resultado";
    }

    private void recolectarIdentificadores(AST.Expr e, Set<String> validos, List<String> out) {
        if (e instanceof AST.Identificador) {
            String n = ((AST.Identificador) e).nombre;
            if (validos.contains(n) && !out.contains(n)) out.add(n);
        } else if (e instanceof AST.Unario) recolectarIdentificadores(((AST.Unario) e).operando, validos, out);
        else if (e instanceof AST.Cast) recolectarIdentificadores(((AST.Cast) e).expr, validos, out);
        else if (e instanceof AST.Binario) {
            recolectarIdentificadores(((AST.Binario) e).izquierda, validos, out);
            recolectarIdentificadores(((AST.Binario) e).derecha, validos, out);
        } else if (e instanceof AST.LlamadaMetodo) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            recolectarIdentificadores(m.receptor, validos, out);
            for (AST.Expr a : m.argumentos) recolectarIdentificadores(a, validos, out);
        }
    }

    private AST.DeclLet declaracion(String nombre, AST.Expr valor, int linea) {
        AST.DeclLet d = new AST.DeclLet();
        d.nombre = nombre;
        d.esMutable = false;
        d.tipoTexto = null;
        d.inicializador = valor;
        d.linea = linea;
        return d;
    }

    private AST.Identificador identificador(String nombre, int linea) {
        AST.Identificador id = new AST.Identificador();
        id.nombre = nombre;
        id.linea = linea;
        return id;
    }

    private void registrarResultado(AST.DeclFuncion f, String nombre, AST.Expr valor, int linea) {
        registrar(linea, "Resultado en variable",
            "El resultado final de la función '" + f.nombre + "' (" + gen.expresion(valor)
            + ") se guarda en la variable declarada `" + nombre + "`.");
    }

    private void registrar(int linea, String tipo, String detalle) {
        cambios.add(new Cambio(linea, Optimizador.PLEGADO, tipo, detalle));
    }

    // ======================================================================
    // Ámbitos
    // ======================================================================
    private void declarar(String n, Const c) { ambitos.get(ambitos.size() - 1).put(n, c); }

    private Const buscar(String n) {
        for (int i = ambitos.size() - 1; i >= 0; i--) {
            Map<String, Const> m = ambitos.get(i);
            if (m.containsKey(n)) return m.get(n);
        }
        return null;
    }

    // ======================================================================
    // Recorrido
    // ======================================================================
    private void bloque(AST.Bloque b) {
        ambitos.add(new HashMap<>());
        for (AST.Sentencia s : b.sentencias) {
            if (s instanceof AST.DeclLet) {
                AST.DeclLet d = (AST.DeclLet) s;
                if (d.inicializador != null) d.inicializador = plegar(d.inicializador, false);
                declarar(d.nombre, constanteDeLet(d));
            } else if (s instanceof AST.SentExpr) {
                AST.SentExpr se = (AST.SentExpr) s;
                se.expr = plegar(se.expr, false);
            } else if (s instanceof AST.SentReturn) {
                AST.SentReturn r = (AST.SentReturn) s;
                if (r.valor != null) r.valor = plegar(r.valor, false);
            } else if (s instanceof AST.SentSalto) {
                AST.SentSalto j = (AST.SentSalto) s;
                if (j.valor != null) j.valor = plegar(j.valor, false);
            }
        }
        if (b.exprFinal != null) b.exprFinal = plegar(b.exprFinal, false);
        ambitos.remove(ambitos.size() - 1);
    }

    /** ¿Esta declaración define una constante propagable? (inmutable, con inicializador constante) */
    private Const constanteDeLet(AST.DeclLet d) {
        if (!propagar || d.esMutable || d.inicializador == null) return null;
        Const c = evaluar(d.inicializador);
        if (c == null) return null;
        String tipo = d.tipoTexto;
        if (tipo == null) return c;
        if (tipo.equals("bool")) return c.tipo == T.BOOL ? c : null;
        if (tipo.equals("f32")) return null;
        if (tipo.equals("f64")) return c.tipo == T.FLOAT ? c : null;
        if (ENTEROS.contains(tipo)) {
            if (c.tipo != T.INT) return null;
            if (c.sufijo != null && !c.sufijo.equals(tipo)) return null;
            return Const.entero(c.i, tipo.equals("i32") ? null : tipo);   // el tipo anotado se conserva como sufijo
        }
        return null;
    }

    private static final Set<String> ENTEROS = new HashSet<>(Arrays.asList(
        "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize"));

    /**
     * @param mantenerIdent true si este nodo no debe sustituirse por su valor
     *        (receptor de método, operando de '&', destino de asignación...)
     */
    private AST.Expr plegar(AST.Expr e, boolean mantenerIdent) {
        if (e == null || esLiteralSimple(e)) return e;

        // 1) ¿Toda la expresión es constante?
        if (!(mantenerIdent && e instanceof AST.Identificador)) {
            Const c = evaluar(e);
            if (c != null) {
                AST.Expr nuevo = construir(c, e.linea);
                if (nuevo != null) {
                    if (e instanceof AST.Identificador) {
                        registrar(e.linea, "Propagación de constante",
                            "`" + ((AST.Identificador) e).nombre + "` se reemplaza por su valor constante " + gen.expresion(nuevo) + ".");
                    } else {
                        registrar(e.linea, "Expresión constante",
                            "`" + gen.expresion(e) + "` se calcula en compilación: " + gen.expresion(nuevo) + ".");
                    }
                    return nuevo;
                }
            }
        }

        // 2) Si no, se pliegan sus partes
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            u.operando = plegar(u.operando, u.operador.equals("&") || u.operador.equals("&mut"));
        } else if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            b.izquierda = plegar(b.izquierda, false);
            b.derecha = plegar(b.derecha, false);
        } else if (e instanceof AST.Cast) {
            AST.Cast c = (AST.Cast) e;
            c.expr = plegar(c.expr, false);
        } else if (e instanceof AST.Asignacion) {
            AST.Asignacion a = (AST.Asignacion) e;
            a.objetivo = plegar(a.objetivo, true);
            a.valor = plegar(a.valor, false);
        } else if (e instanceof AST.Llamada) {
            lista(((AST.Llamada) e).argumentos);
        } else if (e instanceof AST.LlamadaMacro) {
            lista(((AST.LlamadaMacro) e).argumentos);
        } else if (e instanceof AST.LlamadaMetodo) {
            // El receptor no se toca: "5.pow(2)" no compila en Rust (tipo numérico ambiguo)
            lista(((AST.LlamadaMetodo) e).argumentos);
        } else if (e instanceof AST.AccesoCampo) {
            ((AST.AccesoCampo) e).objeto = plegar(((AST.AccesoCampo) e).objeto, true);
        } else if (e instanceof AST.AccesoIndice) {
            AST.AccesoIndice a = (AST.AccesoIndice) e;
            a.objeto = plegar(a.objeto, true);
            a.indice = plegar(a.indice, false);
        } else if (e instanceof AST.Tupla) {
            lista(((AST.Tupla) e).elementos);
        } else if (e instanceof AST.ArregloLiteral) {
            lista(((AST.ArregloLiteral) e).elementos);
        } else if (e instanceof AST.InstanciaStruct) {
            for (AST.CampoInit c : ((AST.InstanciaStruct) e).campos) c.valor = plegar(c.valor, false);
        } else if (e instanceof AST.Rango) {
            AST.Rango r = (AST.Rango) e;
            r.inicio = plegar(r.inicio, false);
            r.fin = plegar(r.fin, false);
        } else if (e instanceof AST.ExprIf) {
            AST.ExprIf i = (AST.ExprIf) e;
            i.condicion = plegar(i.condicion, false);
            bloque(i.entonces);
            i.sino = plegar(i.sino, false);
        } else if (e instanceof AST.ExprMatch) {
            AST.ExprMatch m = (AST.ExprMatch) e;
            m.sujeto = plegar(m.sujeto, false);
            for (AST.BrazoMatch br : m.brazos) {
                ambitos.add(new HashMap<>());
                if ("IDENTIFICADOR".equals(br.patron.tokenTipo)) declarar(br.patron.texto, null);
                br.cuerpo = plegar(br.cuerpo, false);
                ambitos.remove(ambitos.size() - 1);
            }
        } else if (e instanceof AST.ExprBloque) {
            bloque(((AST.ExprBloque) e).bloque);
        } else if (e instanceof AST.ExprLoop) {
            bloque(((AST.ExprLoop) e).cuerpo);
        } else if (e instanceof AST.ExprWhile) {
            AST.ExprWhile w = (AST.ExprWhile) e;
            w.condicion = plegar(w.condicion, false);
            bloque(w.cuerpo);
        } else if (e instanceof AST.ExprFor) {
            AST.ExprFor f = (AST.ExprFor) e;
            f.iterable = plegar(f.iterable, false);
            ambitos.add(new HashMap<>());
            declarar(f.variable, null);
            bloque(f.cuerpo);
            ambitos.remove(ambitos.size() - 1);
        }
        return e;
    }

    private void lista(List<AST.Expr> es) {
        for (int i = 0; i < es.size(); i++) es.set(i, plegar(es.get(i), false));
    }

    /** Un literal numérico/booleano (o su negativo) ya está en su forma final. */
    private boolean esLiteralSimple(AST.Expr e) {
        if (e instanceof AST.Literal) return true;
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            return u.operador.equals("-") && u.operando instanceof AST.Literal;
        }
        return false;
    }

    // ======================================================================
    // Evaluación en compilación (no modifica nada)
    // ======================================================================
    private static final Pattern SUFIJO_ENTERO = Pattern.compile("(i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize)$");
    private static final Pattern SUFIJO_FLOAT = Pattern.compile("(f32|f64)$");

    private Const evaluar(AST.Expr e) {
        if (e instanceof AST.Literal) return literal((AST.Literal) e);

        if (e instanceof AST.Identificador) {
            if (!propagar) return null;
            Const c = buscar(((AST.Identificador) e).nombre);
            if (c != null) variablesPropagadas.add(((AST.Identificador) e).nombre);
            return c;
        }

        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            Const c = evaluar(u.operando);
            if (c == null) return null;
            if (u.operador.equals("-")) {
                if (c.tipo == T.INT) return entero(-c.i, c.sufijo);
                if (c.tipo == T.FLOAT) return real(-c.d, c.sufijo);
            }
            if (u.operador.equals("!") && c.tipo == T.BOOL) return Const.bool(!c.b);
            return null;
        }

        if (e instanceof AST.Binario) return binario((AST.Binario) e);

        if (e instanceof AST.Cast) {
            AST.Cast c = (AST.Cast) e;
            Const v = evaluar(c.expr);
            return v == null ? null : castear(v, c.tipoTexto);
        }

        if (e instanceof AST.LlamadaMetodo) return metodo((AST.LlamadaMetodo) e);
        return null;
    }

    private Const literal(AST.Literal l) {
        String t = l.texto.replace("_", "");
        if ("BOOLEANO".equals(l.tokenTipo)) return Const.bool(t.equals("true"));
        if ("FLOTANTE".equals(l.tokenTipo) || ("ENTERO".equals(l.tokenTipo) && SUFIJO_FLOAT.matcher(t).find())) {
            Matcher m = SUFIJO_FLOAT.matcher(t);
            String suf = null;
            if (m.find()) { suf = m.group(1); t = t.substring(0, m.start()); }
            if ("f32".equals(suf)) return null;
            try {
                double d = Double.parseDouble(t);
                return Double.isFinite(d) ? Const.real(d, suf) : null;
            } catch (NumberFormatException ex) { return null; }
        }
        if ("ENTERO".equals(l.tokenTipo)) {
            Matcher m = SUFIJO_ENTERO.matcher(t);
            String suf = null;
            if (m.find()) { suf = m.group(1); t = t.substring(0, m.start()); }
            try { return Const.entero(Long.parseLong(t), suf); }
            catch (NumberFormatException ex) { return null; }
        }
        return null;
    }

    // ---- límites de cada tipo entero (sin sufijo se asume i32) ----
    private long min(String suf) {
        if (suf == null) return Integer.MIN_VALUE;
        switch (suf) {
            case "i8": return Byte.MIN_VALUE;
            case "i16": return Short.MIN_VALUE;
            case "i32": return Integer.MIN_VALUE;
            case "i64": case "i128": case "isize": return Long.MIN_VALUE + 1;
            default: return 0; // sin signo
        }
    }

    private long max(String suf) {
        if (suf == null) return Integer.MAX_VALUE;
        switch (suf) {
            case "i8": return Byte.MAX_VALUE;
            case "i16": return Short.MAX_VALUE;
            case "i32": return Integer.MAX_VALUE;
            case "u8": return 255;
            case "u16": return 65535;
            case "u32": return 4294967295L;
            default: return Long.MAX_VALUE;
        }
    }

    /** Entero solo si cabe en su tipo. */
    private Const entero(long v, String suf) {
        return (v >= min(suf) && v <= max(suf)) ? Const.entero(v, suf) : null;
    }

    private Const real(double d, String suf) {
        return Double.isFinite(d) ? Const.real(d, suf) : null;
    }

    private Const binario(AST.Binario b) {
        String op = b.operador;
        Const l = evaluar(b.izquierda);

        // Cortocircuito: false && x  /  true || x
        if (op.equals("&&") || op.equals("||")) {
            if (l == null || l.tipo != T.BOOL) return null;
            if (op.equals("&&") && !l.b) return Const.bool(false);
            if (op.equals("||") && l.b) return Const.bool(true);
            Const r = evaluar(b.derecha);
            return (r != null && r.tipo == T.BOOL) ? r : null;
        }

        Const r = evaluar(b.derecha);
        if (l == null || r == null || l.tipo != r.tipo) return null;
        String suf = l.sufijo != null ? l.sufijo : r.sufijo;

        if (l.tipo == T.BOOL) {
            if (op.equals("==")) return Const.bool(l.b == r.b);
            if (op.equals("!=")) return Const.bool(l.b != r.b);
            return null;
        }

        if (l.tipo == T.INT) {
            long a = l.i, c = r.i;
            try {
                switch (op) {
                    case "+": return entero(Math.addExact(a, c), suf);
                    case "-": return entero(Math.subtractExact(a, c), suf);
                    case "*": return entero(Math.multiplyExact(a, c), suf);
                    case "/": return c == 0 ? null : entero(a / c, suf);
                    case "%": return c == 0 ? null : entero(a % c, suf);
                    case "==": return Const.bool(a == c);
                    case "!=": return Const.bool(a != c);
                    case "<":  return Const.bool(a < c);
                    case ">":  return Const.bool(a > c);
                    case "<=": return Const.bool(a <= c);
                    case ">=": return Const.bool(a >= c);
                }
            } catch (ArithmeticException ex) { return null; }
            return null;
        }

        // FLOAT
        double a = l.d, c = r.d;
        switch (op) {
            case "+": return real(a + c, suf);
            case "-": return real(a - c, suf);
            case "*": return real(a * c, suf);
            case "/": return c == 0 ? null : real(a / c, suf);
            case "==": return Const.bool(a == c);
            case "!=": return Const.bool(a != c);
            case "<":  return Const.bool(a < c);
            case ">":  return Const.bool(a > c);
            case "<=": return Const.bool(a <= c);
            case ">=": return Const.bool(a >= c);
        }
        return null;
    }

    private Const castear(Const v, String destino) {
        if (destino.equals("f64")) {
            if (v.tipo == T.INT) return Const.real((double) v.i, null);
            if (v.tipo == T.FLOAT) return Const.real(v.d, null);
            return null;
        }
        if (!ENTEROS.contains(destino)) return null;   // f32, char, etc. no se pliegan

        long x;
        if (v.tipo == T.INT) x = v.i;
        else if (v.tipo == T.BOOL) x = v.b ? 1 : 0;
        else {   // FLOAT -> entero: trunca y satura
            if (Double.isNaN(v.d)) return null;
            double t = v.d < 0 ? Math.ceil(v.d) : Math.floor(v.d);
            x = (long) t;
            x = Math.max(min(destino), Math.min(max(destino), x));
        }
        // Un entero que no cabe en el destino se "envuelve" en Rust: solo se pliega si cabe tal cual
        if (x < min(destino) || x > max(destino)) return null;
        return Const.entero(x, destino.equals("i32") ? null : destino);
    }

    private Const metodo(AST.LlamadaMetodo m) {
        Const r = evaluar(m.receptor);
        if (r == null) return null;
        List<Const> args = new ArrayList<>();
        for (AST.Expr a : m.argumentos) {
            Const c = evaluar(a);
            if (c == null) return null;
            args.add(c);
        }
        String n = m.nombreMetodo;

        if (r.tipo == T.INT) {
            switch (n) {
                case "abs": if (args.isEmpty()) return entero(Math.abs(r.i), r.sufijo); break;
                case "min": if (args.size() == 1 && args.get(0).tipo == T.INT) return entero(Math.min(r.i, args.get(0).i), r.sufijo); break;
                case "max": if (args.size() == 1 && args.get(0).tipo == T.INT) return entero(Math.max(r.i, args.get(0).i), r.sufijo); break;
                case "pow":
                    if (args.size() == 1 && args.get(0).tipo == T.INT && args.get(0).i >= 0 && args.get(0).i <= 64) {
                        try {
                            long res = 1;
                            for (long k = 0; k < args.get(0).i; k++) res = Math.multiplyExact(res, r.i);
                            return entero(res, r.sufijo);
                        } catch (ArithmeticException ex) { return null; }
                    }
                    break;
            }
            return null;
        }
        if (r.tipo == T.FLOAT && args.isEmpty()) {
            switch (n) {
                case "abs": return real(Math.abs(r.d), r.sufijo);
                case "sqrt": return r.d >= 0 ? real(Math.sqrt(r.d), r.sufijo) : null;
                case "floor": return real(Math.floor(r.d), r.sufijo);
                case "ceil": return real(Math.ceil(r.d), r.sufijo);
                case "round": return real(r.d < 0 ? -Math.floor(-r.d + 0.5) : Math.floor(r.d + 0.5), r.sufijo);
            }
        }
        return null;
    }

    // ======================================================================
    // Construcción del literal resultante
    // ======================================================================
    private AST.Expr construir(Const c, int linea) {
        switch (c.tipo) {
            case BOOL: return literal(c.b ? "true" : "false", "BOOLEANO", linea);
            case INT: {
                if (c.i == Long.MIN_VALUE) return null;
                String suf = c.sufijo == null ? "" : c.sufijo;
                return conSigno(c.i < 0, literal(Math.abs(c.i) + suf, "ENTERO", linea), linea);
            }
            default: {
                String suf = c.sufijo == null ? "" : c.sufijo;
                boolean neg = c.d < 0 || (c.d == 0 && 1 / c.d < 0);
                return conSigno(neg, literal(textoFloat(Math.abs(c.d)) + suf, "FLOTANTE", linea), linea);
            }
        }
    }

    private AST.Literal literal(String texto, String tipo, int linea) {
        AST.Literal l = new AST.Literal();
        l.texto = texto;
        l.tokenTipo = tipo;
        l.linea = linea;
        return l;
    }

    private AST.Expr conSigno(boolean negativo, AST.Literal l, int linea) {
        if (!negativo) return l;
        AST.Unario u = new AST.Unario();
        u.operador = "-";
        u.operando = l;
        u.linea = linea;
        return u;
    }

    private String textoFloat(double a) {
        if (a == Math.rint(a) && a < 1e15) return (long) a + ".0";
        String s = new BigDecimal(Double.toString(a)).toPlainString();
        return s.contains(".") ? s : s + ".0";
    }
}
