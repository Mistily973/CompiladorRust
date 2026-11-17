package Analizador_semantico;

import Analizador_sintactico.AST;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Analizador semántico. Hace un recorrido en dos pasadas sobre el AST:
 *   Pase 1: registra todas las funciones y estructuras declaradas en el
 *           ámbito global (para soportar referencias hacia adelante).
 *   Pase 2: analiza el cuerpo de cada función, construyendo la Tabla de
 *           Símbolos y la Tabla de Direcciones, y validando:
 *           (a) compatibilidad semántica en asignaciones,
 *           (b) tipo y número de parámetros en llamadas a función/método.
 */
public class AnalizadorSemantico {

    public static class EstructuraSemantica {
        public int linea;
        public String tipoEstructura;
        public String detalle;
        public String ambito;
        public String resultado; // "OK" | "ERROR" | "SIN VALIDAR"

        public EstructuraSemantica(int linea, String tipoEstructura, String detalle, String ambito, String resultado) {
            this.linea = linea;
            this.tipoEstructura = tipoEstructura;
            this.detalle = detalle;
            this.ambito = ambito;
            this.resultado = resultado;
        }
    }

    public static class SemanticError {
        public int linea;
        public String mensaje;
        public SemanticError(int linea, String mensaje) { this.linea = linea; this.mensaje = mensaje; }
    }

    public static class ResultadoSemantico {
        public List<EstructuraSemantica> estructuras = new ArrayList<>();
        public List<SemanticError> errores = new ArrayList<>();
        public List<Simbolo> simbolos = new ArrayList<>();
        public List<TablaDirecciones.Entrada> direcciones = new ArrayList<>();
    }

    private final TablaSimbolos tabla = new TablaSimbolos();
    private final TablaDirecciones direcciones = new TablaDirecciones();
    private final ResultadoSemantico resultado = new ResultadoSemantico();
    private final Map<String, AST.DeclStruct> structs = new HashMap<>();

    private static class ContextoBucle {
        boolean hayBreak = false;
        Tipo valor = Tipo.NUNCA;
    }

    public ResultadoSemantico analizar(AST.Programa programa) {
        if (programa == null) return resultado;

        registrarEstructuras(programa.estructuras);
        for (AST.DeclFuncion f : programa.funciones) {
            registrarFuncion(f);
        }
        for (AST.DeclFuncion f : programa.funciones) {
            analizarFuncion(f);
        }

        resultado.simbolos = tabla.getHistorial();
        resultado.direcciones = direcciones.entradas;
        return resultado;
    }

    // ---------------- Registro (pase 1) ----------------

    private void registrarEstructuras(List<AST.DeclStruct> lista) {
        for (AST.DeclStruct s : lista) {
            structs.put(s.nombre, s);
        }
        for (AST.DeclStruct s : lista) {
            List<String> nombres = new ArrayList<>();
            List<Tipo> tipos = new ArrayList<>();
            for (AST.CampoDecl c : s.campos) {
                nombres.add(c.nombre);
                tipos.add(Tipo.parsear(c.tipoTexto));
            }
            direcciones.registrarEstructura(s.nombre, nombres, tipos, s.linea);
            resultado.estructuras.add(new EstructuraSemantica(s.linea, "Estructura",
                "Declaración de struct '" + s.nombre + "' (" + s.campos.size() + " campos)", "Global", "OK"));
        }
    }

    private void registrarFuncion(AST.DeclFuncion f) {
        Simbolo sim = new Simbolo(f.nombre, Tipo.parsear(f.tipoRetornoTexto), false, Simbolo.TipoSimbolo.FUNCION, f.linea, true);
        sim.parametros = new ArrayList<>();
        for (AST.Parametro p : f.parametros) {
            sim.parametros.add(new Simbolo.ParametroInfo(p.nombre, Tipo.parsear(p.tipoTexto)));
        }
        sim.tipoRetorno = Tipo.parsear(f.tipoRetornoTexto);
        tabla.insertar(sim);
        TablaDirecciones.Entrada e = direcciones.asignarFuncion(f.nombre, f.linea);
        sim.direccion = e.direccion;
        resultado.estructuras.add(new EstructuraSemantica(f.linea, "Función",
            "Declaración de función '" + sim.firma() + "'", "Global", "OK"));
    }

    // ---------------- Análisis de funciones (pase 2) ----------------

    private void analizarFuncion(AST.DeclFuncion f) {
        tabla.entrarAmbitoFuncion("fn " + f.nombre);
        direcciones.abrirMarco(f.nombre);

        for (AST.Parametro p : f.parametros) {
            if (p.nombre.equals("self")) continue;
            Tipo t = Tipo.parsear(p.tipoTexto);
            Simbolo sim = new Simbolo(p.nombre, t, p.esMut, Simbolo.TipoSimbolo.PARAMETRO, p.linea, true);
            tabla.insertar(sim);
            TablaDirecciones.Entrada e = direcciones.asignarLocal(p.nombre, t, tabla.ambitoActualNombre(), p.linea);
            sim.direccion = e.direccion;
            sim.tamano = e.tamano;
            resultado.estructuras.add(new EstructuraSemantica(p.linea, "Parámetro",
                "Parámetro '" + p.nombre + ": " + t + "'", tabla.ambitoActualNombre(), "OK"));
        }

        Tipo tipoCuerpo = analizarBloque(f.cuerpo, false);
        Tipo tipoRetornoDeclarado = Tipo.parsear(f.tipoRetornoTexto);
        if (!Tipo.asignable(tipoRetornoDeclarado, tipoCuerpo) && !tipoCuerpo.esNunca()) {
            error(f.cuerpo.linea, "La función '" + f.nombre + "' debe retornar '" + tipoRetornoDeclarado
                + "' pero el cuerpo produce '" + tipoCuerpo + "'");
        }

        direcciones.cerrarMarco();
        tabla.salirAmbito();
    }

    private Tipo analizarBloque(AST.Bloque b, boolean nuevoAmbito) {
        if (nuevoAmbito) tabla.entrarAmbito();
        for (AST.Sentencia s : b.sentencias) {
            analizarSentencia(s);
        }
        Tipo tipoFinal = Tipo.UNIT;
        if (b.exprFinal != null) {
            tipoFinal = tipo(b.exprFinal);
        } else if (!b.sentencias.isEmpty() && b.sentencias.get(b.sentencias.size() - 1) instanceof AST.SentReturn) {
            // Un bloque cuya última sentencia es 'return' nunca "cae" al final;
            // su tipo es NUNCA (diverge), por lo que es compatible con cualquier
            // tipo de retorno esperado (igual que en Rust real).
            tipoFinal = Tipo.NUNCA;
        }
        if (nuevoAmbito) tabla.salirAmbito();
        return tipoFinal;
    }

    private void analizarSentencia(AST.Sentencia s) {
        if (s instanceof AST.DeclLet) {
            analizarDeclLet((AST.DeclLet) s);
        } else if (s instanceof AST.SentExpr) {
            tipo(((AST.SentExpr) s).expr);
        } else if (s instanceof AST.SentReturn) {
            if (((AST.SentReturn) s).valor != null) tipo(((AST.SentReturn) s).valor);
        } else if (s instanceof AST.SentSalto) {
            if (((AST.SentSalto) s).valor != null) tipo(((AST.SentSalto) s).valor);
        }
    }

    private void analizarDeclLet(AST.DeclLet d) {
        Tipo tipoInferido = Tipo.DESCONOCIDO;
        boolean inicializado = d.inicializador != null;
        if (d.inicializador != null) {
            tipoInferido = tipo(d.inicializador);
        }
        Tipo tipoFinal = d.tipoTexto != null ? Tipo.parsear(d.tipoTexto) : tipoInferido;

        if (d.tipoTexto != null && d.inicializador != null && !Tipo.asignable(tipoFinal, tipoInferido)) {
            error(d.linea, "No se puede asignar un valor de tipo '" + tipoInferido + "' a la variable '"
                + d.nombre + "' declarada como '" + tipoFinal + "'");
        }

        Simbolo sim = new Simbolo(d.nombre, tipoFinal, d.esMutable, Simbolo.TipoSimbolo.VARIABLE, d.linea, inicializado);
        tabla.insertar(sim);
        TablaDirecciones.Entrada e = direcciones.asignarLocal(d.nombre, tipoFinal, tabla.ambitoActualNombre(), d.linea);
        sim.direccion = e.direccion;
        sim.tamano = e.tamano;

        resultado.estructuras.add(new EstructuraSemantica(d.linea, "Declaración Variable",
            "Variable '" + d.nombre + "' (" + (d.esMutable ? "mutable" : "inmutable") + "): " + tipoFinal,
            tabla.ambitoActualNombre(), "OK"));
    }

    // ---------------- Dispatcher de tipos de expresión ----------------

    private Tipo tipo(AST.Expr e) {
        if (e == null) return Tipo.UNIT;

        if (e instanceof AST.Literal) return tipoLiteral((AST.Literal) e);
        if (e instanceof AST.Identificador) return tipoIdentificador((AST.Identificador) e);
        if (e instanceof AST.Ruta) return Tipo.DESCONOCIDO;
        if (e instanceof AST.Unario) return tipoUnario((AST.Unario) e);
        if (e instanceof AST.Binario) return tipoBinario((AST.Binario) e);
        if (e instanceof AST.Cast) { tipo(((AST.Cast) e).expr); return Tipo.parsear(((AST.Cast) e).tipoTexto); }
        if (e instanceof AST.Asignacion) return analizarAsignacion((AST.Asignacion) e);
        if (e instanceof AST.Llamada) return analizarLlamada((AST.Llamada) e);
        if (e instanceof AST.LlamadaMetodo) return analizarLlamadaMetodo((AST.LlamadaMetodo) e);
        if (e instanceof AST.LlamadaMacro) return analizarMacro((AST.LlamadaMacro) e);
        if (e instanceof AST.AccesoCampo) return tipoAccesoCampo((AST.AccesoCampo) e);
        if (e instanceof AST.AccesoIndice) return tipoAccesoIndice((AST.AccesoIndice) e);
        if (e instanceof AST.Tupla) return tipoTupla((AST.Tupla) e);
        if (e instanceof AST.ArregloLiteral) return tipoArreglo((AST.ArregloLiteral) e);
        if (e instanceof AST.InstanciaStruct) return tipoInstanciaStruct((AST.InstanciaStruct) e);
        if (e instanceof AST.Rango) { if (((AST.Rango) e).inicio != null) tipo(((AST.Rango) e).inicio);
            if (((AST.Rango) e).fin != null) tipo(((AST.Rango) e).fin); return Tipo.nominal("Range"); }
        if (e instanceof AST.ExprIf) return tipoIf((AST.ExprIf) e);
        if (e instanceof AST.ExprMatch) return tipoMatch((AST.ExprMatch) e);
        if (e instanceof AST.ExprBloque) return analizarBloque(((AST.ExprBloque) e).bloque, true);
        if (e instanceof AST.ExprLoop) return tipoLoop((AST.ExprLoop) e);
        if (e instanceof AST.ExprWhile) { tipo(((AST.ExprWhile) e).condicion); analizarBloque(((AST.ExprWhile) e).cuerpo, true); return Tipo.UNIT; }
        if (e instanceof AST.ExprFor) return tipoFor((AST.ExprFor) e);

        return Tipo.DESCONOCIDO;
    }

    private Tipo tipoLiteral(AST.Literal l) {
        switch (l.tokenTipo) {
            case "ENTERO": return Tipo.ENTERO_LIT;
            case "FLOTANTE": return Tipo.FLOTANTE_LIT;
            case "STRING": return Tipo.STR_REF;
            case "CARACTER": return Tipo.CHAR;
            case "BOOLEANO": return Tipo.BOOL;
            default: return Tipo.DESCONOCIDO;
        }
    }

    private Tipo tipoIdentificador(AST.Identificador id) {
        Simbolo sim = tabla.buscar(id.nombre);
        if (sim == null) {
            error(id.linea, "No se encontró el identificador '" + id.nombre + "' en este ámbito");
            return Tipo.DESCONOCIDO;
        }
        sim.usos++;
        return sim.tipo;
    }

    private Tipo tipoUnario(AST.Unario u) {
        Tipo t = tipo(u.operando);
        switch (u.operador) {
            case "-":
                if (!t.esNumerico()) error(u.linea, "El operador unario '-' no se puede aplicar a '" + t + "'");
                return t;
            case "!":
                if (!(t.esNominal() && t.nombre.equals("bool")) && !t.esEntero()) {
                    error(u.linea, "El operador '!' no se puede aplicar a '" + t + "'");
                }
                return t;
            case "&": return Tipo.referencia(t, false);
            case "&mut": return Tipo.referencia(t, true);
            case "*": return t.esReferencia() ? t.interno : t;
            default: return t;
        }
    }

    private Tipo tipoBinario(AST.Binario b) {
        Tipo izq = tipo(b.izquierda);
        Tipo der = tipo(b.derecha);
        Tipo res = TablaAsociaciones.resultado(b.operador, izq, der);
        if (res == null) {
            error(b.linea, "Operador '" + b.operador + "' no está definido para los tipos '" + izq + "' y '" + der + "'");
            return Tipo.DESCONOCIDO;
        }
        return res;
    }

    private Tipo tipoTupla(AST.Tupla t) {
        List<Tipo> tipos = new ArrayList<>();
        for (AST.Expr e : t.elementos) tipos.add(tipo(e));
        if (tipos.isEmpty()) return Tipo.UNIT;
        return Tipo.tupla(tipos);
    }

    private Tipo tipoArreglo(AST.ArregloLiteral a) {
        Tipo comun = null;
        for (AST.Expr e : a.elementos) {
            Tipo t = tipo(e);
            comun = (comun == null) ? t : Tipo.unir(comun, t);
        }
        return Tipo.arreglo(comun != null ? comun : Tipo.DESCONOCIDO);
    }

    private Tipo tipoAccesoCampo(AST.AccesoCampo ac) {
        Tipo objeto = tipo(ac.objeto);
        Tipo base = objeto.sinReferencias();
        if (base.esNominal() && structs.containsKey(base.nombre)) {
            AST.DeclStruct s = structs.get(base.nombre);
            for (AST.CampoDecl c : s.campos) {
                if (c.nombre.equals(ac.campo)) return Tipo.parsear(c.tipoTexto);
            }
            error(ac.linea, "La estructura '" + base.nombre + "' no tiene el campo '" + ac.campo + "'");
            return Tipo.DESCONOCIDO;
        }
        if (base.clase == Tipo.Clase.TUPLA) {
            try {
                int idx = Integer.parseInt(ac.campo);
                if (idx < base.elementos.size()) return base.elementos.get(idx);
            } catch (NumberFormatException ignored) { }
        }
        return Tipo.DESCONOCIDO;
    }

    private Tipo tipoAccesoIndice(AST.AccesoIndice ai) {
        Tipo objeto = tipo(ai.objeto).sinReferencias();
        Tipo indice = tipo(ai.indice);
        if (!indice.esEntero()) {
            error(ai.linea, "El índice debe ser de tipo entero, se encontró '" + indice + "'");
        }
        if (objeto.clase == Tipo.Clase.ARREGLO || objeto.clase == Tipo.Clase.SLICE) return objeto.interno;
        if (objeto.esNominal() && objeto.nombre.equals("Vec")) return objeto.interno != null ? objeto.interno : Tipo.DESCONOCIDO;
        return Tipo.DESCONOCIDO;
    }

    private Tipo tipoInstanciaStruct(AST.InstanciaStruct is) {
        AST.DeclStruct s = structs.get(is.nombreStruct);
        if (s == null) {
            error(is.linea, "No existe la estructura '" + is.nombreStruct + "'");
            for (AST.CampoInit ci : is.campos) tipo(ci.valor);
            return Tipo.DESCONOCIDO;
        }
        for (AST.CampoInit ci : is.campos) {
            Tipo valTipo = tipo(ci.valor);
            AST.CampoDecl encontrado = null;
            for (AST.CampoDecl c : s.campos) if (c.nombre.equals(ci.nombre)) encontrado = c;
            if (encontrado == null) {
                error(ci.linea, "La estructura '" + is.nombreStruct + "' no tiene el campo '" + ci.nombre + "'");
            } else {
                Tipo esperado = Tipo.parsear(encontrado.tipoTexto);
                if (!Tipo.asignable(esperado, valTipo)) {
                    error(ci.linea, "El campo '" + ci.nombre + "' de '" + is.nombreStruct + "' espera '" + esperado
                        + "' pero se recibió '" + valTipo + "'");
                }
            }
        }
        return Tipo.nominal(is.nombreStruct);
    }

    private Tipo tipoIf(AST.ExprIf eif) {
        Tipo cond = tipo(eif.condicion);
        if (!(cond.esNominal() && cond.nombre.equals("bool"))) {
            error(eif.linea, "La condición de 'if' debe ser de tipo 'bool', se encontró '" + cond + "'");
        }
        Tipo tEntonces = analizarBloque(eif.entonces, true);
        Tipo tSino = eif.sino != null ? tipo(eif.sino) : Tipo.UNIT;
        return Tipo.unir(tEntonces, tSino);
    }

    private Tipo tipoMatch(AST.ExprMatch m) {
        tipo(m.sujeto);
        Tipo comun = null;
        for (AST.BrazoMatch brazo : m.brazos) {
            Tipo t = tipo(brazo.cuerpo);
            comun = (comun == null) ? t : Tipo.unir(comun, t);
        }
        return comun != null ? comun : Tipo.UNIT;
    }

    private Tipo tipoLoop(AST.ExprLoop loop) {
        analizarBloque(loop.cuerpo, true);
        return Tipo.NUNCA;
    }

    private Tipo tipoFor(AST.ExprFor f) {
        tipo(f.iterable);
        tabla.entrarAmbito();
        Simbolo sim = new Simbolo(f.variable, Tipo.ENTERO_LIT, false, Simbolo.TipoSimbolo.VARIABLE, f.linea, true);
        tabla.insertar(sim);
        analizarBloque(f.cuerpo, false);
        tabla.salirAmbito();
        return Tipo.UNIT;
    }

    // ---------------- Validación #1: asignaciones ----------------

    private Tipo analizarAsignacion(AST.Asignacion a) {
        Tipo tipoValor = tipo(a.valor);
        Tipo tipoObjetivo;

        if (a.objetivo instanceof AST.Identificador) {
            String nombre = ((AST.Identificador) a.objetivo).nombre;
            Simbolo sim = tabla.buscar(nombre);
            if (sim == null) {
                error(a.linea, "No se encontró el identificador '" + nombre + "' en este ámbito");
                return Tipo.DESCONOCIDO;
            }
            if (!sim.inicializado) {
                // primera asignación de 'let x;' sin inicializar: no requiere mut
                sim.inicializado = true;
                if (sim.tipo.esDesconocido()) sim.tipo = tipoValor;
            } else if (!sim.esMutable) {
                error(a.linea, "No se puede asignar dos veces a la variable inmutable '" + nombre
                    + "' (declárela con 'let mut' para permitirlo)");
            }
            tipoObjetivo = sim.tipo;
            sim.usos++;
        } else if (a.objetivo instanceof AST.AccesoCampo || a.objetivo instanceof AST.AccesoIndice) {
            verificarMutableAtravesando(a.objetivo, a.linea, "asignar a través de");
            tipoObjetivo = tipo(a.objetivo);
        } else if (a.objetivo instanceof AST.Unario && ((AST.Unario) a.objetivo).operador.equals("*")) {
            tipoObjetivo = tipo(a.objetivo);
        } else {
            tipoObjetivo = tipo(a.objetivo);
        }

        if (a.operador.equals("=")) {
            if (!Tipo.asignable(tipoObjetivo, tipoValor)) {
                error(a.linea, "No se puede asignar un valor de tipo '" + tipoValor + "' a un destino de tipo '" + tipoObjetivo + "'");
            }
        } else {
            String opBase = a.operador.substring(0, 1);
            Tipo res = TablaAsociaciones.resultado(opBase, tipoObjetivo, tipoValor);
            if (res == null) {
                error(a.linea, "El operador '" + a.operador + "' no está definido para los tipos '" + tipoObjetivo + "' y '" + tipoValor + "'");
            }
        }
        return Tipo.UNIT;
    }

    private void verificarMutableAtravesando(AST.Expr objeto, int linea, String accion) {
        AST.Expr raiz = objeto;
        while (true) {
            if (raiz instanceof AST.AccesoCampo) { raiz = ((AST.AccesoCampo) raiz).objeto; }
            else if (raiz instanceof AST.AccesoIndice) { raiz = ((AST.AccesoIndice) raiz).objeto; }
            else if (raiz instanceof AST.Unario && ((AST.Unario) raiz).operador.equals("*")) { raiz = ((AST.Unario) raiz).operando; }
            else break;
        }
        if (raiz instanceof AST.Identificador) {
            String nombre = ((AST.Identificador) raiz).nombre;
            Simbolo sim = tabla.buscar(nombre);
            if (sim != null && !sim.esMutable && !sim.tipo.esReferencia()) {
                error(linea, "No se puede " + accion + " la variable inmutable '" + nombre + "'");
            }
        }
    }

    // ---------------- Validación #2: llamadas (función/método) ----------------

    private Tipo analizarLlamada(AST.Llamada c) {
        List<Tipo> tiposArgs = new ArrayList<>();
        for (AST.Expr arg : c.argumentos) tiposArgs.add(tipo(arg));

        if (c.callee instanceof AST.Identificador) {
            String nombre = ((AST.Identificador) c.callee).nombre;
            Simbolo sim = tabla.buscar(nombre);
            if (sim == null || sim.categoria != Simbolo.TipoSimbolo.FUNCION) {
                error(c.linea, "No se encontró la función '" + nombre + "'");
                return Tipo.DESCONOCIDO;
            }
            validarArgumentos(nombre, sim.parametros, tiposArgs, c.argumentos, c.linea);
            return sim.tipoRetorno;
        }

        if (c.callee instanceof AST.Ruta) {
            List<String> seg = ((AST.Ruta) c.callee).segmentos;
            BibliotecaEstandar.Firma firma = BibliotecaEstandar.funcion(seg);
            if (firma == null) {
                error(c.linea, "No se encontró la función de biblioteca '" + String.join("::", seg) + "'");
                return Tipo.DESCONOCIDO;
            }
            validarArgumentosFirma(String.join("::", seg), firma, tiposArgs, c.argumentos, c.linea);
            return firma.retorno;
        }

        return Tipo.DESCONOCIDO;
    }

    private Tipo analizarLlamadaMetodo(AST.LlamadaMetodo m) {
        Tipo tipoReceptor = tipo(m.receptor);
        List<Tipo> tiposArgs = new ArrayList<>();
        for (AST.Expr arg : m.argumentos) tiposArgs.add(tipo(arg));

        BibliotecaEstandar.Firma firma = BibliotecaEstandar.metodo(tipoReceptor, m.nombreMetodo);
        if (firma == null) {
            error(m.linea, "El tipo '" + tipoReceptor + "' no tiene un método '" + m.nombreMetodo + "'");
            return Tipo.DESCONOCIDO;
        }
        if (firma.mutaReceptor) {
            verificarMutableAtravesando(m.receptor, m.linea, "llamar un método mutable sobre");
        }
        validarArgumentosFirma(m.nombreMetodo, firma, tiposArgs, m.argumentos, m.linea);
        return firma.retorno;
    }

    private void validarArgumentos(String nombre, List<Simbolo.ParametroInfo> params, List<Tipo> args, List<AST.Expr> argsExpr, int linea) {
        if (params.size() != args.size()) {
            error(linea, "La función '" + nombre + "' espera " + params.size() + " argumento(s) pero se le pasaron " + args.size());
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            Tipo esperado = params.get(i).tipo;
            Tipo recibido = args.get(i);
            if (!Tipo.asignable(esperado, recibido)) {
                error(argsExpr.get(i).linea, "El argumento " + (i + 1) + " de '" + nombre + "' (" + params.get(i).nombre
                    + ") espera tipo '" + esperado + "' pero se recibió '" + recibido + "'");
            }
        }
    }

    private void validarArgumentosFirma(String nombre, BibliotecaEstandar.Firma firma, List<Tipo> args, List<AST.Expr> argsExpr, int linea) {
        if (firma.parametros.size() != args.size()) {
            error(linea, "'" + nombre + "' espera " + firma.parametros.size() + " argumento(s) pero se le pasaron " + args.size());
            return;
        }
        for (int i = 0; i < firma.parametros.size(); i++) {
            Tipo esperado = firma.parametros.get(i);
            Tipo recibido = args.get(i);
            if (esperado.esDesconocido()) continue;
            if (!Tipo.asignable(esperado, recibido)) {
                error(argsExpr.get(i).linea, "El argumento " + (i + 1) + " de '" + nombre
                    + "' espera tipo '" + esperado + "' pero se recibió '" + recibido + "'");
            }
        }
    }

    // ---------------- Macros (println!, format!, etc.) ----------------

    private Tipo analizarMacro(AST.LlamadaMacro m) {
        List<Tipo> tipos = new ArrayList<>();
        for (AST.Expr arg : m.argumentos) tipos.add(tipo(arg));

        if (m.nombre.equals("println") || m.nombre.equals("print") || m.nombre.equals("format") || m.nombre.equals("eprintln")) {
            if (!m.argumentos.isEmpty() && m.argumentos.get(0) instanceof AST.Literal) {
                String formato = ((AST.Literal) m.argumentos.get(0)).texto;
                int placeholders = contarPlaceholders(formato);
                int disponibles = m.argumentos.size() - 1;
                if (placeholders != disponibles) {
                    error(m.linea, "La macro '" + m.nombre + "!' tiene " + placeholders + " marcador(es) '{}' pero se pasaron "
                        + disponibles + " argumento(s) adicionales");
                }
                for (int i = 1; i < m.argumentos.size(); i++) {
                    comprobarDisplay(tipos.get(i), m.linea, m.nombre);
                }
            }
            return m.nombre.equals("format") ? Tipo.STRING : Tipo.UNIT;
        }
        if (m.nombre.equals("vec")) {
            Tipo comun = null;
            for (Tipo t : tipos) comun = (comun == null) ? t : Tipo.unir(comun, t);
            return vecDe(comun);
        }
        if (m.nombre.equals("assert") || m.nombre.equals("assert_eq") || m.nombre.equals("assert_ne")) {
            return Tipo.UNIT;
        }
        if (m.nombre.equals("panic")) return Tipo.NUNCA;
        return Tipo.DESCONOCIDO;
    }

    private Tipo vecDe(Tipo elemento) {
        Tipo t = Tipo.nominal("Vec");
        t.interno = elemento != null ? elemento : Tipo.DESCONOCIDO;
        return t;
    }

    private int contarPlaceholders(String formato) {
        int c = 0;
        for (int i = 0; i < formato.length() - 1; i++) {
            if (formato.charAt(i) == '{' && formato.charAt(i + 1) == '}') c++;
        }
        return c;
    }

    private void comprobarDisplay(Tipo t, int linea, String macro) {
        Tipo base = t.sinReferencias();
        if (base.esNominal() && structs.containsKey(base.nombre)) {
            error(linea, "El tipo '" + base + "' no implementa 'Display'; no puede usarse con '{}' en '" + macro + "!'");
        }
    }

    // ---------------- Utilidades ----------------

    private void error(int linea, String mensaje) {
        resultado.errores.add(new SemanticError(linea, mensaje));
        resultado.estructuras.add(new EstructuraSemantica(linea, "Error semántico", mensaje, tabla.ambitoActualNombre(), "ERROR"));
    }
}
