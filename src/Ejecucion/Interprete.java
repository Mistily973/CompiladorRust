package Ejecucion;

import Analizador_sintactico.AST;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intérprete del AST para el subconjunto de Rust que soporta este compilador.
 * Sirve para EJECUTAR el programa original y el optimizado y comparar sus
 * tiempos y su salida.
 *
 * No modifica el AST. Cuenta las "operaciones evaluadas" (nodos del AST que se
 * evalúan), una medida determinista de cuánto trabajo hace el programa, además
 * del tiempo real.
 */
public class Interprete {

    // ======================================================================
    // Resultado de una ejecución
    // ======================================================================
    public static class Resultado {
        public String salida = "";
        public long operaciones;
        public long nanos;
        public String error;            // null si terminó bien
        public boolean ok() { return error == null; }
    }

    // ======================================================================
    // Valores y control de flujo
    // ======================================================================
    private static final Object UNIT = new Object[0];
    private static final long LIMITE_OPERACIONES = 30_000_000L;
    private static final int LIMITE_SALIDA = 1_000_000;

    private static class Flujo extends RuntimeException {
        Flujo() { super(null, null, false, false); }
    }
    private static class Retorno extends Flujo { final Object valor; Retorno(Object v) { valor = v; } }
    private static class Ruptura extends Flujo { final Object valor; Ruptura(Object v) { valor = v; } }
    private static class Continuacion extends Flujo { }
    private static class Panico extends RuntimeException {
        Panico(String m) { super(m, null, false, false); }
    }

    private static class Celda { Object v; Celda(Object v) { this.v = v; } }

    private static class Entorno {
        final Entorno padre;
        final HashMap<String, Celda> vars = new HashMap<>(8);
        Entorno(Entorno p) { padre = p; }
        void declarar(String n, Object v) { vars.put(n, new Celda(v)); }
        Celda buscar(String n) {
            for (Entorno e = this; e != null; e = e.padre) {
                Celda c = e.vars.get(n);
                if (c != null) return c;
            }
            return null;
        }
    }

    private static class Referencia {
        final java.util.function.Supplier<Object> lector;
        final java.util.function.Consumer<Object> escritor;
        Referencia(java.util.function.Supplier<Object> l, java.util.function.Consumer<Object> e) { lector = l; escritor = e; }
        Object get() { return lector.get(); }
        void set(Object v) { escritor.accept(v); }
    }

    private static class Instancia {
        final String tipo;
        final LinkedHashMap<String, Object> campos = new LinkedHashMap<>();
        Instancia(String t) { tipo = t; }
    }

    private static class Opcion {
        final boolean hay; final Object valor;
        Opcion(boolean h, Object v) { hay = h; valor = v; }
    }

    private static class RangoVal {
        final long ini, fin; final boolean incl;
        RangoVal(long i, long f, boolean in) { ini = i; fin = f; incl = in; }
    }

    private static class Iter {
        final Iterator<Object> it;
        Iter(Iterator<Object> it) { this.it = it; }
    }

    // ======================================================================
    // Estado
    // ======================================================================
    private final AST.Programa programa;
    private final Map<String, AST.DeclFuncion> funciones = new HashMap<>();
    private final Map<AST.Literal, Object> cacheLiterales = new IdentityHashMap<>();
    private StringBuilder salida;
    private long operaciones;

    public Interprete(AST.Programa programa) {
        this.programa = programa;
        for (AST.DeclFuncion f : programa.funciones) funciones.put(f.nombre, f);
    }

    public boolean tieneMain() { return funciones.containsKey("main"); }

    /** Ejecuta main() y devuelve la salida, las operaciones y el tiempo (solo de la ejecución). */
    public Resultado ejecutar() {
        Resultado r = new Resultado();
        salida = new StringBuilder();
        operaciones = 0;
        AST.DeclFuncion main = funciones.get("main");
        if (main == null) {
            r.error = "El programa no tiene función main().";
            return r;
        }
        long t0 = System.nanoTime();
        try {
            llamarFuncion(main, new ArrayList<>());
        } catch (Panico p) {
            r.error = "thread 'main' panicked: " + p.getMessage();
        } catch (StackOverflowError so) {
            r.error = "Desbordamiento de pila (recursión demasiado profunda).";
        } catch (Flujo f) {
            // break/continue/return sueltos: se ignoran
        } catch (RuntimeException ex) {
            r.error = "Error de ejecución: " + ex;
        }
        r.nanos = System.nanoTime() - t0;
        r.operaciones = operaciones;
        r.salida = salida.toString();
        return r;
    }

    private void tick() {
        if (++operaciones > LIMITE_OPERACIONES)
            throw new Panico("se superó el límite de " + LIMITE_OPERACIONES + " operaciones (¿ciclo infinito?)");
    }

    private void imprimir(String s) {
        if (salida.length() < LIMITE_SALIDA) salida.append(s);
    }

    // ======================================================================
    // Funciones, bloques, sentencias
    // ======================================================================
    private Object llamarFuncion(AST.DeclFuncion f, List<Object> args) {
        Entorno env = new Entorno(null);
        for (int i = 0; i < f.parametros.size() && i < args.size(); i++) {
            env.declarar(f.parametros.get(i).nombre, args.get(i));
        }
        try {
            return bloque(f.cuerpo, env);
        } catch (Retorno r) {
            return r.valor;
        }
    }

    private Object bloque(AST.Bloque b, Entorno padre) {
        Entorno env = new Entorno(padre);
        for (AST.Sentencia s : b.sentencias) sentencia(s, env);
        return b.exprFinal == null ? UNIT : eval(b.exprFinal, env);
    }

    private void sentencia(AST.Sentencia s, Entorno env) {
        tick();
        if (s instanceof AST.DeclLet) {
            AST.DeclLet d = (AST.DeclLet) s;
            Object v = d.inicializador == null ? null : eval(d.inicializador, env);
            env.declarar(d.nombre, v);
        } else if (s instanceof AST.SentExpr) {
            eval(((AST.SentExpr) s).expr, env);
        } else if (s instanceof AST.SentReturn) {
            AST.SentReturn r = (AST.SentReturn) s;
            throw new Retorno(r.valor == null ? UNIT : eval(r.valor, env));
        } else if (s instanceof AST.SentSalto) {
            AST.SentSalto j = (AST.SentSalto) s;
            if (j.tipo.equals("continue")) throw new Continuacion();
            throw new Ruptura(j.valor == null ? UNIT : eval(j.valor, env));
        }
    }

    // ======================================================================
    // Expresiones
    // ======================================================================
    private Object eval(AST.Expr e, Entorno env) {
        tick();
        if (e instanceof AST.Literal) return literal((AST.Literal) e);
        if (e instanceof AST.Identificador) {
            String n = ((AST.Identificador) e).nombre;
            Celda c = env.buscar(n);
            if (c == null) throw new Panico("variable no definida: " + n);
            return c.v;
        }
        if (e instanceof AST.Binario) return binario((AST.Binario) e, env);
        if (e instanceof AST.Unario) return unario((AST.Unario) e, env);
        if (e instanceof AST.Asignacion) return asignacion((AST.Asignacion) e, env);
        if (e instanceof AST.Llamada) return llamada((AST.Llamada) e, env);
        if (e instanceof AST.LlamadaMacro) return macro((AST.LlamadaMacro) e, env);
        if (e instanceof AST.LlamadaMetodo) return metodo((AST.LlamadaMetodo) e, env);
        if (e instanceof AST.Cast) {
            AST.Cast c = (AST.Cast) e;
            return castear(desref(eval(c.expr, env)), c.tipoTexto);
        }
        if (e instanceof AST.AccesoCampo) {
            AST.AccesoCampo a = (AST.AccesoCampo) e;
            Object o = desref(eval(a.objeto, env));
            if (o instanceof Instancia) {
                Instancia in = (Instancia) o;
                if (!in.campos.containsKey(a.campo)) throw new Panico("el campo '" + a.campo + "' no existe en " + in.tipo);
                return in.campos.get(a.campo);
            }
            if (o instanceof Object[] && a.campo.matches("\\d+")) return ((Object[]) o)[Integer.parseInt(a.campo)];
            throw new Panico("acceso a campo '" + a.campo + "' sobre un valor que no es struct");
        }
        if (e instanceof AST.AccesoIndice) {
            AST.AccesoIndice a = (AST.AccesoIndice) e;
            Object o = desref(eval(a.objeto, env));
            long i = aLong(desref(eval(a.indice, env)));
            if (!(o instanceof List)) throw new Panico("solo se puede indexar arreglos y vectores");
            List<?> l = (List<?>) o;
            if (i < 0 || i >= l.size()) throw new Panico("índice fuera de rango: el tamaño es " + l.size() + " y el índice es " + i);
            return l.get((int) i);
        }
        if (e instanceof AST.Tupla) {
            List<AST.Expr> es = ((AST.Tupla) e).elementos;
            if (es.isEmpty()) return UNIT;
            Object[] t = new Object[es.size()];
            for (int i = 0; i < t.length; i++) t[i] = eval(es.get(i), env);
            return t;
        }
        if (e instanceof AST.ArregloLiteral) {
            List<Object> l = new ArrayList<>();
            for (AST.Expr x : ((AST.ArregloLiteral) e).elementos) l.add(eval(x, env));
            return l;
        }
        if (e instanceof AST.InstanciaStruct) {
            AST.InstanciaStruct is = (AST.InstanciaStruct) e;
            Instancia in = new Instancia(is.nombreStruct);
            for (AST.CampoInit c : is.campos) in.campos.put(c.nombre, eval(c.valor, env));
            return in;
        }
        if (e instanceof AST.Rango) {
            AST.Rango r = (AST.Rango) e;
            long ini = r.inicio == null ? 0 : aLong(desref(eval(r.inicio, env)));
            long fin = r.fin == null ? Long.MAX_VALUE : aLong(desref(eval(r.fin, env)));
            return new RangoVal(ini, fin, r.inclusivo);
        }
        if (e instanceof AST.ExprIf) {
            AST.ExprIf i = (AST.ExprIf) e;
            if (aBool(desref(eval(i.condicion, env)))) return bloque(i.entonces, env);
            return i.sino == null ? UNIT : eval(i.sino, env);
        }
        if (e instanceof AST.ExprMatch) return exprMatch((AST.ExprMatch) e, env);
        if (e instanceof AST.ExprBloque) return bloque(((AST.ExprBloque) e).bloque, env);
        if (e instanceof AST.ExprLoop) {
            AST.ExprLoop l = (AST.ExprLoop) e;
            while (true) {
                try { bloque(l.cuerpo, env); }
                catch (Ruptura r) { return r.valor; }
                catch (Continuacion c) { /* sigue */ }
            }
        }
        if (e instanceof AST.ExprWhile) {
            AST.ExprWhile w = (AST.ExprWhile) e;
            while (aBool(desref(eval(w.condicion, env)))) {
                try { bloque(w.cuerpo, env); }
                catch (Ruptura r) { break; }
                catch (Continuacion c) { /* sigue */ }
            }
            return UNIT;
        }
        if (e instanceof AST.ExprFor) return exprFor((AST.ExprFor) e, env);
        if (e instanceof AST.Ruta) throw new Panico("una ruta no puede evaluarse como valor");
        throw new Panico("expresión no soportada: " + e.getClass().getSimpleName());
    }

    private Object exprFor(AST.ExprFor f, Entorno env) {
        Object it = desref(eval(f.iterable, env));
        Iterator<?> iterador;
        if (it instanceof RangoVal) {
            RangoVal r = (RangoVal) it;
            long fin = r.incl ? r.fin : r.fin - 1;
            iterador = new Iterator<Object>() {
                long i = r.ini;
                public boolean hasNext() { return i <= fin; }
                public Object next() { return i++; }
            };
        } else if (it instanceof List) iterador = ((List<?>) it).iterator();
        else if (it instanceof Iter) iterador = ((Iter) it).it;
        else throw new Panico("el valor del 'for' no es iterable");

        while (iterador.hasNext()) {
            Object v = iterador.next();
            Entorno inner = new Entorno(env);
            inner.declarar(f.variable, v);
            try { bloque(f.cuerpo, inner); }
            catch (Ruptura r) { break; }
            catch (Continuacion c) { /* sigue */ }
        }
        return UNIT;
    }

    private Object exprMatch(AST.ExprMatch m, Entorno env) {
        Object sujeto = desref(eval(m.sujeto, env));
        for (AST.BrazoMatch b : m.brazos) {
            AST.Patron p = b.patron;
            if (p.texto.equals("_")) return eval(b.cuerpo, env);
            if ("IDENTIFICADOR".equals(p.tokenTipo)) {
                Entorno inner = new Entorno(env);
                inner.declarar(p.texto, sujeto);
                return eval(b.cuerpo, inner);
            }
            AST.Literal lit = new AST.Literal();
            lit.texto = p.texto;
            lit.tokenTipo = p.tokenTipo;
            if (igual(sujeto, literal(lit))) return eval(b.cuerpo, env);
        }
        throw new Panico("ningún brazo del match coincide con el valor");
    }

    // ----------------------------------------------------------------------
    private Object literal(AST.Literal l) {
        Object v = cacheLiterales.get(l);
        if (v != null) return v;
        String t = l.texto;
        switch (l.tokenTipo == null ? "" : l.tokenTipo) {
            case "ENTERO": {
                String s = t.replace("_", "").replaceFirst("(i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize)$", "");
                try {
                    if (s.startsWith("0x")) v = Long.parseLong(s.substring(2), 16);
                    else if (s.startsWith("0b")) v = Long.parseLong(s.substring(2), 2);
                    else if (s.startsWith("0o")) v = Long.parseLong(s.substring(2), 8);
                    else v = Long.parseLong(s);
                } catch (NumberFormatException ex) { v = 0L; }
                break;
            }
            case "FLOTANTE": {
                String s = t.replace("_", "").replaceFirst("(f32|f64)$", "");
                try { v = Double.parseDouble(s); } catch (NumberFormatException ex) { v = 0.0; }
                break;
            }
            case "BOOLEANO": v = t.equals("true"); break;
            case "STRING": v = desescapar(t.substring(1, Math.max(1, t.length() - 1))); break;
            case "CARACTER": {
                String s = desescapar(t.substring(1, Math.max(1, t.length() - 1)));
                v = s.isEmpty() ? ' ' : s.charAt(0);
                break;
            }
            default: v = t;
        }
        cacheLiterales.put(l, v);
        return v;
    }

    private String desescapar(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '0': sb.append('\0'); break;
                    default: sb.append(n);
                }
            } else sb.append(c);
        }
        return sb.toString();
    }

    // ======================================================================
    // Operadores
    // ======================================================================
    private Object desref(Object v) {
        while (v instanceof Referencia) v = ((Referencia) v).get();
        return v;
    }

    private long aLong(Object v) {
        if (v instanceof Long) return (Long) v;
        if (v instanceof Double) return (long) (double) (Double) v;
        if (v instanceof Character) return (Character) v;
        throw new Panico("se esperaba un entero");
    }

    private double aDouble(Object v) {
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long) return (Long) v;
        throw new Panico("se esperaba un número");
    }

    private boolean aBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        throw new Panico("se esperaba un booleano");
    }

    private String texto(Object v) {
        v = desref(v);
        if (v instanceof CharSequence) return v.toString();
        if (v instanceof Character) return v.toString();
        return mostrar(v, false);
    }

    private Object binario(AST.Binario b, Entorno env) {
        String op = b.operador;
        if (op.equals("&&")) {
            return aBool(desref(eval(b.izquierda, env))) && aBool(desref(eval(b.derecha, env)));
        }
        if (op.equals("||")) {
            return aBool(desref(eval(b.izquierda, env))) || aBool(desref(eval(b.derecha, env)));
        }
        Object l = desref(eval(b.izquierda, env));
        Object r = desref(eval(b.derecha, env));
        return operar(op, l, r);
    }

    private Object operar(String op, Object l, Object r) {
        if (l instanceof Long && r instanceof Long) {
            long a = (Long) l, c = (Long) r;
            switch (op) {
                case "+": return a + c;
                case "-": return a - c;
                case "*": return a * c;
                case "/": if (c == 0) throw new Panico("attempt to divide by zero"); return a / c;
                case "%": if (c == 0) throw new Panico("attempt to calculate the remainder with a divisor of zero"); return a % c;
                case "==": return a == c;
                case "!=": return a != c;
                case "<": return a < c;
                case ">": return a > c;
                case "<=": return a <= c;
                case ">=": return a >= c;
            }
        }
        if ((l instanceof Long || l instanceof Double) && (r instanceof Long || r instanceof Double)) {
            double a = aDouble(l), c = aDouble(r);
            switch (op) {
                case "+": return a + c;
                case "-": return a - c;
                case "*": return a * c;
                case "/": return a / c;
                case "%": return a % c;
                case "==": return a == c;
                case "!=": return a != c;
                case "<": return a < c;
                case ">": return a > c;
                case "<=": return a <= c;
                case ">=": return a >= c;
            }
        }
        if (op.equals("+") && l instanceof CharSequence) {
            return new StringBuilder(l.toString()).append(texto(r));
        }
        if (op.equals("==")) return igual(l, r);
        if (op.equals("!=")) return !igual(l, r);
        if ((l instanceof CharSequence || l instanceof Character) && (r instanceof CharSequence || r instanceof Character)) {
            int cmp = l.toString().compareTo(r.toString());
            switch (op) {
                case "<": return cmp < 0;
                case ">": return cmp > 0;
                case "<=": return cmp <= 0;
                case ">=": return cmp >= 0;
            }
        }
        throw new Panico("operación '" + op + "' no soportada entre estos valores");
    }

    private boolean igual(Object a, Object b) {
        a = desref(a); b = desref(b);
        if (a instanceof Long && b instanceof Long) return ((Long) a).longValue() == ((Long) b).longValue();
        if ((a instanceof Long || a instanceof Double) && (b instanceof Long || b instanceof Double)) return aDouble(a) == aDouble(b);
        if ((a instanceof CharSequence) && (b instanceof CharSequence)) return a.toString().equals(b.toString());
        if (a instanceof Object[] && b instanceof Object[]) return Arrays.equals((Object[]) a, (Object[]) b);
        if (a instanceof List && b instanceof List) {
            List<?> x = (List<?>) a, y = (List<?>) b;
            if (x.size() != y.size()) return false;
            for (int i = 0; i < x.size(); i++) if (!igual(x.get(i), y.get(i))) return false;
            return true;
        }
        return Objects.equals(a, b);
    }

    private Object unario(AST.Unario u, Entorno env) {
        switch (u.operador) {
            case "-": {
                Object v = desref(eval(u.operando, env));
                if (v instanceof Long) return -(Long) v;
                return -aDouble(v);
            }
            case "!": {
                Object v = desref(eval(u.operando, env));
                if (v instanceof Boolean) return !(Boolean) v;
                return ~aLong(v);
            }
            case "*": return desref(eval(u.operando, env));
            case "&": return eval(u.operando, env);
            case "&mut": return referenciaA(u.operando, env);
        }
        throw new Panico("operador unario no soportado: " + u.operador);
    }

    private Object referenciaA(AST.Expr e, Entorno env) {
        if (e instanceof AST.Identificador) {
            Celda c = env.buscar(((AST.Identificador) e).nombre);
            if (c == null) throw new Panico("variable no definida");
            if (c.v instanceof Referencia) return c.v;
            return new Referencia(() -> c.v, v -> c.v = v);
        }
        if (e instanceof AST.AccesoCampo) {
            AST.AccesoCampo a = (AST.AccesoCampo) e;
            Object o = desref(eval(a.objeto, env));
            if (o instanceof Instancia) {
                Instancia in = (Instancia) o;
                return new Referencia(() -> in.campos.get(a.campo), v -> in.campos.put(a.campo, v));
            }
        }
        if (e instanceof AST.AccesoIndice) {
            AST.AccesoIndice a = (AST.AccesoIndice) e;
            Object o = desref(eval(a.objeto, env));
            long i = aLong(desref(eval(a.indice, env)));
            if (o instanceof List) {
                @SuppressWarnings("unchecked") List<Object> l = (List<Object>) o;
                if (i < 0 || i >= l.size()) throw new Panico("índice fuera de rango");
                return new Referencia(() -> l.get((int) i), v -> l.set((int) i, v));
            }
        }
        return eval(e, env);
    }

    // ----------------------------------------------------------------------
    private Object asignacion(AST.Asignacion a, Entorno env) {
        Object valor;
        if (a.operador.equals("=")) {
            valor = eval(a.valor, env);
        } else {
            Object actual = desref(eval(a.objetivo, env));
            Object der = desref(eval(a.valor, env));
            String op = a.operador.substring(0, a.operador.length() - 1);
            valor = operar(op, actual, der);
        }
        asignarA(a.objetivo, valor, env);
        return UNIT;
    }

    @SuppressWarnings("unchecked")
    private void asignarA(AST.Expr t, Object valor, Entorno env) {
        if (t instanceof AST.Identificador) {
            Celda c = env.buscar(((AST.Identificador) t).nombre);
            if (c == null) throw new Panico("variable no definida");
            c.v = valor;
        } else if (t instanceof AST.Unario && ((AST.Unario) t).operador.equals("*")) {
            AST.Expr op = ((AST.Unario) t).operando;
            Object r = eval(op, env);
            if (r instanceof Referencia) ((Referencia) r).set(valor);
            else asignarA(op, valor, env);
        } else if (t instanceof AST.AccesoCampo) {
            AST.AccesoCampo a = (AST.AccesoCampo) t;
            Object o = desref(eval(a.objeto, env));
            if (o instanceof Instancia) ((Instancia) o).campos.put(a.campo, valor);
            else throw new Panico("asignación a campo de un valor que no es struct");
        } else if (t instanceof AST.AccesoIndice) {
            AST.AccesoIndice a = (AST.AccesoIndice) t;
            Object o = desref(eval(a.objeto, env));
            long i = aLong(desref(eval(a.indice, env)));
            List<Object> l = (List<Object>) o;
            if (i < 0 || i >= l.size()) throw new Panico("índice fuera de rango: el tamaño es " + l.size() + " y el índice es " + i);
            l.set((int) i, valor);
        } else {
            throw new Panico("destino de asignación no válido");
        }
    }

    // ----------------------------------------------------------------------
    private Object castear(Object v, String tipo) {
        switch (tipo) {
            case "f64": case "f32":
                if (v instanceof Character) return (double) (Character) v;
                return aDouble(v);
            case "i64": case "i128": case "isize": case "u64": case "u128": case "usize":
                return aLong(v);
            case "i32": return (long) (int) aLong(v);
            case "i16": return (long) (short) aLong(v);
            case "i8":  return (long) (byte) aLong(v);
            case "u32": return aLong(v) & 0xFFFFFFFFL;
            case "u16": return aLong(v) & 0xFFFFL;
            case "u8":  return aLong(v) & 0xFFL;
            case "char": return (char) aLong(v);
            case "bool": return v;
            default: return v;
        }
    }

    // ======================================================================
    // Llamadas
    // ======================================================================
    private Object llamada(AST.Llamada c, Entorno env) {
        List<Object> args = new ArrayList<>(c.argumentos.size());
        for (AST.Expr a : c.argumentos) args.add(eval(a, env));

        if (c.callee instanceof AST.Identificador) {
            String n = ((AST.Identificador) c.callee).nombre;
            AST.DeclFuncion f = funciones.get(n);
            if (f == null) throw new Panico("función no definida: " + n);
            return llamarFuncion(f, args);
        }
        if (c.callee instanceof AST.Ruta) {
            String path = String.join("::", ((AST.Ruta) c.callee).segmentos);
            switch (path) {
                case "String::from": return new StringBuilder(texto(args.get(0)));
                case "String::new": return new StringBuilder();
                case "Vec::new": return new ArrayList<Object>();
                case "std::cmp::max": return aLong(desref(args.get(0))) >= aLong(desref(args.get(1))) ? desref(args.get(0)) : desref(args.get(1));
                case "std::cmp::min": return aLong(desref(args.get(0))) <= aLong(desref(args.get(1))) ? desref(args.get(0)) : desref(args.get(1));
            }
            throw new Panico("función de biblioteca no soportada: " + path);
        }
        throw new Panico("llamada no soportada");
    }

    private Object macro(AST.LlamadaMacro m, Entorno env) {
        switch (m.nombre) {
            case "println": case "print": case "format": case "panic": {
                String s = m.argumentos.isEmpty() ? "" : formatear(m.argumentos, env);
                if (m.nombre.equals("println")) { imprimir(s + "\n"); return UNIT; }
                if (m.nombre.equals("print")) { imprimir(s); return UNIT; }
                if (m.nombre.equals("panic")) throw new Panico(s);
                return new StringBuilder(s);
            }
            case "vec": {
                List<Object> l = new ArrayList<>();
                for (AST.Expr a : m.argumentos) l.add(eval(a, env));
                return l;
            }
            case "assert": {
                if (!aBool(desref(eval(m.argumentos.get(0), env)))) throw new Panico("assertion failed");
                return UNIT;
            }
            case "assert_eq": {
                if (!igual(eval(m.argumentos.get(0), env), eval(m.argumentos.get(1), env))) throw new Panico("assertion `left == right` failed");
                return UNIT;
            }
        }
        throw new Panico("macro no soportada: " + m.nombre + "!");
    }

    // ----------------------------------------------------------------------
    // Formato de println!/format!
    // ----------------------------------------------------------------------
    private static final Pattern ESPEC = Pattern.compile("^(?:(.)?([<>^]))?(\\d+)?(?:\\.(\\d+))?(\\?)?$");

    private String formatear(List<AST.Expr> argsExpr, Entorno env) {
        AST.Expr primero = argsExpr.get(0);
        if (!(primero instanceof AST.Literal)) return texto(eval(primero, env));
        String fmt = (String) literal((AST.Literal) primero);
        List<Object> args = new ArrayList<>();
        for (int i = 1; i < argsExpr.size(); i++) args.add(eval(argsExpr.get(i), env));

        StringBuilder sb = new StringBuilder();
        int siguiente = 0;
        for (int i = 0; i < fmt.length(); i++) {
            char c = fmt.charAt(i);
            if (c == '{' && i + 1 < fmt.length() && fmt.charAt(i + 1) == '{') { sb.append('{'); i++; continue; }
            if (c == '}' && i + 1 < fmt.length() && fmt.charAt(i + 1) == '}') { sb.append('}'); i++; continue; }
            if (c != '{') { sb.append(c); continue; }
            int fin = fmt.indexOf('}', i);
            if (fin < 0) throw new Panico("cadena de formato inválida");
            String contenido = fmt.substring(i + 1, fin);
            i = fin;
            String nombre = contenido, espec = "";
            int dp = contenido.indexOf(':');
            if (dp >= 0) { nombre = contenido.substring(0, dp); espec = contenido.substring(dp + 1); }

            Object valor;
            if (nombre.isEmpty()) {
                if (siguiente >= args.size()) throw new Panico("faltan argumentos para el formato");
                valor = args.get(siguiente++);
            } else if (nombre.matches("\\d+")) {
                valor = args.get(Integer.parseInt(nombre));
            } else {
                Celda cel = env.buscar(nombre);
                if (cel == null) throw new Panico("variable no definida en el formato: " + nombre);
                valor = cel.v;
            }
            sb.append(aplicarEspec(desref(valor), espec));
        }
        return sb.toString();
    }

    private String aplicarEspec(Object v, String espec) {
        if (espec.isEmpty()) return mostrar(v, false);
        Matcher m = ESPEC.matcher(espec);
        if (!m.matches()) return mostrar(v, false);
        boolean debug = m.group(5) != null;
        String s;
        if (m.group(4) != null && (v instanceof Double || v instanceof Long)) {
            s = String.format(Locale.ROOT, "%." + m.group(4) + "f", aDouble(v));
        } else {
            s = mostrar(v, debug);
        }
        if (m.group(3) != null) {
            int ancho = Integer.parseInt(m.group(3));
            char relleno = m.group(1) != null && !m.group(1).isEmpty() ? m.group(1).charAt(0) : ' ';
            String align = m.group(2) != null ? m.group(2) : ((v instanceof Long || v instanceof Double) ? ">" : "<");
            int falta = ancho - s.length();
            if (falta > 0) {
                String pad = String.valueOf(relleno).repeat(falta);
                if (align.equals(">")) s = pad + s;
                else if (align.equals("<")) s = s + pad;
                else s = pad.substring(0, falta / 2) + s + pad.substring(falta / 2);
            }
        }
        return s;
    }

    private String mostrar(Object v, boolean debug) {
        v = desref(v);
        if (v == null) return "<sin valor>";
        if (v == UNIT) return "()";
        if (v instanceof Long || v instanceof Boolean) return v.toString();
        if (v instanceof Double) {
            double d = (Double) v;
            if (Double.isNaN(d)) return "NaN";
            if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
            if (d == Math.rint(d) && Math.abs(d) < 1e16) {
                String s = String.valueOf((long) d);
                if (d == 0 && 1 / d < 0) s = "-0";
                return debug ? s + ".0" : s;
            }
            return new java.math.BigDecimal(Double.toString(d)).toPlainString();
        }
        if (v instanceof Character) return debug ? "'" + v + "'" : v.toString();
        if (v instanceof CharSequence) return debug ? "\"" + v + "\"" : v.toString();
        if (v instanceof Object[]) {
            Object[] t = (Object[]) v;
            StringJoiner sj = new StringJoiner(", ", "(", t.length == 1 ? ",)" : ")");
            for (Object x : t) sj.add(mostrar(x, true));
            return sj.toString();
        }
        if (v instanceof List) {
            StringJoiner sj = new StringJoiner(", ", "[", "]");
            for (Object x : (List<?>) v) sj.add(mostrar(x, true));
            return sj.toString();
        }
        if (v instanceof Instancia) {
            Instancia in = (Instancia) v;
            if (in.campos.isEmpty()) return in.tipo;
            StringJoiner sj = new StringJoiner(", ", in.tipo + " { ", " }");
            for (Map.Entry<String, Object> en : in.campos.entrySet()) sj.add(en.getKey() + ": " + mostrar(en.getValue(), true));
            return sj.toString();
        }
        if (v instanceof Opcion) {
            Opcion o = (Opcion) v;
            return o.hay ? "Some(" + mostrar(o.valor, true) + ")" : "None";
        }
        return v.toString();
    }

    // ======================================================================
    // Métodos de la biblioteca estándar
    // ======================================================================
    @SuppressWarnings("unchecked")
    private Object metodo(AST.LlamadaMetodo m, Entorno env) {
        Object r = desref(eval(m.receptor, env));
        List<Object> args = new ArrayList<>(m.argumentos.size());
        for (AST.Expr a : m.argumentos) args.add(desref(eval(a, env)));
        String n = m.nombreMetodo;

        // ---- Enteros ----
        if (r instanceof Long) {
            long x = (Long) r;
            switch (n) {
                case "abs": return Math.abs(x);
                case "pow": {
                    long res = 1, exp = aLong(args.get(0));
                    for (long i = 0; i < exp; i++) { res *= x; tick(); }
                    return res;
                }
                case "min": return Math.min(x, aLong(args.get(0)));
                case "max": return Math.max(x, aLong(args.get(0)));
                case "to_string": return new StringBuilder(Long.toString(x));
                case "clone": return x;
            }
        }
        // ---- Flotantes ----
        if (r instanceof Double) {
            double x = (Double) r;
            switch (n) {
                case "abs": return Math.abs(x);
                case "sqrt": return Math.sqrt(x);
                case "round": return x < 0 ? -Math.floor(-x + 0.5) : Math.floor(x + 0.5);
                case "floor": return Math.floor(x);
                case "ceil": return Math.ceil(x);
                case "to_string": return new StringBuilder(mostrar(x, false));
                case "clone": return x;
            }
        }
        // ---- char ----
        if (r instanceof Character) {
            char c = (Character) r;
            switch (n) {
                case "to_string": return new StringBuilder(String.valueOf(c));
                case "is_alphabetic": return Character.isLetter(c);
                case "is_numeric": return Character.isDigit(c);
                case "to_uppercase": return Character.toUpperCase(c);
            }
        }
        // ---- String / &str ----
        if (r instanceof CharSequence) {
            String s = r.toString();
            switch (n) {
                case "len": return (long) s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                case "is_empty": return s.isEmpty();
                case "to_string": case "clone": return new StringBuilder(s);
                case "push": ((StringBuilder) r).append((char) (Character) args.get(0)); return UNIT;
                case "push_str": ((StringBuilder) r).append(texto(args.get(0))); return UNIT;
                case "trim": return s.trim();
                case "to_uppercase": return new StringBuilder(s.toUpperCase());
                case "to_lowercase": return new StringBuilder(s.toLowerCase());
                case "as_str": return s;
                case "contains": return s.contains(texto(args.get(0)));
                case "replace": return new StringBuilder(s.replace(texto(args.get(0)), texto(args.get(1))));
                case "chars": { List<Object> cs = new ArrayList<>(); for (char c : s.toCharArray()) cs.add(c); return new Iter(cs.iterator()); }
                case "split": { List<Object> ps = new ArrayList<>(); for (String p : s.split(Pattern.quote(texto(args.get(0))), -1)) ps.add(p); return new Iter(ps.iterator()); }
                case "parse": {
                    try { return Long.parseLong(s.trim()); }
                    catch (NumberFormatException e1) {
                        try { return Double.parseDouble(s.trim()); }
                        catch (NumberFormatException e2) { throw new Panico("no se pudo convertir '" + s + "' con parse()"); }
                    }
                }
            }
        }
        // ---- Vec / arreglos ----
        if (r instanceof List) {
            List<Object> l = (List<Object>) r;
            switch (n) {
                case "push": l.add(args.get(0)); return UNIT;
                case "pop": return l.isEmpty() ? new Opcion(false, null) : new Opcion(true, l.remove(l.size() - 1));
                case "len": return (long) l.size();
                case "is_empty": return l.isEmpty();
                case "get": { long i = aLong(args.get(0)); return (i >= 0 && i < l.size()) ? new Opcion(true, l.get((int) i)) : new Opcion(false, null); }
                case "iter": return new Iter(new ArrayList<>(l).iterator());
                case "clone": return new ArrayList<>(l);
                case "sort": l.sort((a, b) -> {
                    if (a instanceof Long && b instanceof Long) return Long.compare((Long) a, (Long) b);
                    if (a instanceof Double || b instanceof Double) return Double.compare(aDouble(a), aDouble(b));
                    return a.toString().compareTo(b.toString());
                }); return UNIT;
                case "contains": for (Object o : l) if (igual(o, args.get(0))) return true; return false;
            }
        }
        // ---- Option ----
        if (r instanceof Opcion) {
            Opcion o = (Opcion) r;
            switch (n) {
                case "is_some": return o.hay;
                case "is_none": return !o.hay;
                case "unwrap": if (!o.hay) throw new Panico("called `Option::unwrap()` on a `None` value"); return o.valor;
                case "unwrap_or": return o.hay ? o.valor : args.get(0);
            }
        }
        // ---- Iteradores ----
        if (r instanceof Iter) {
            Iter it = (Iter) r;
            switch (n) {
                case "next": return it.it.hasNext() ? new Opcion(true, it.it.next()) : new Opcion(false, null);
                case "count": { long c = 0; while (it.it.hasNext()) { it.it.next(); c++; } return c; }
                case "sum": {
                    long sl = 0; double sd = 0; boolean flot = false;
                    while (it.it.hasNext()) {
                        Object x = desref(it.it.next());
                        if (x instanceof Double) { flot = true; sd += (Double) x; } else sl += aLong(x);
                    }
                    return flot ? (Object) (sd + sl) : (Object) sl;
                }
            }
        }
        throw new Panico("método no soportado: " + n + "()");
    }
}
