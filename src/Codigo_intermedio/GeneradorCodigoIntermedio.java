package Codigo_intermedio;

import Analizador_sintactico.AST;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generador de código intermedio. Recorre el AST ya validado por el
 * analizador semántico y, para cada expresión que contenga al menos una
 * operación aritmética (+, -, *, /, %, o el '-' unario), produce:
 *
 *   1) Su notación infija (tal como aparece en el código, normalizada).
 *   2) Su conversión a notación PREFIJA (polaca).
 *   3) Su conversión a notación POSFIJA (polaca inversa).
 *   4) Una tabla de CUÁDRUPLOS (resultado, operando1, operador, operando2)
 *      que implementa esa expresión paso a paso usando variables
 *      temporales t1, t2, t3... (algoritmo clásico de evaluación de árbol
 *      de expresión en post-orden).
 *   5) El VALOR NUMÉRICO FINAL de la expresión, evaluado con los valores
 *      que las variables involucradas ya tenían asignados en el código
 *      hasta ese punto (se simula la ejecución secuencial del programa,
 *      llevando un "entorno" de valores conocidos). Si algún operando no
 *      se puede determinar en tiempo de compilación (un parámetro, una
 *      llamada a función, etc.), el valor final simplemente no se reporta.
 *
 * Cada una de estas cosas queda agrupada en un BloqueExpresion ("la tabla"
 * que pide el requerimiento, una por cada operación intermedia aritmética
 * encontrada). Además se acumula una lista global de todos los cuádruplos
 * generados en el programa, para la vista "observar los cuádruplos" completa.
 */
public class GeneradorCodigoIntermedio {

    public static class ResultadoCodigoIntermedio {
        public List<BloqueExpresion> bloques = new ArrayList<>();
        public List<Cuadruplo> cuadruplosGlobales = new ArrayList<>();
    }

    /** Valor numérico conocido en tiempo de "compilación" para una variable o sub-expresión. */
    private static class Valor {
        double num;
        boolean entero;
        Valor(double num, boolean entero) { this.num = num; this.entero = entero; }
    }

    private static final java.util.Set<String> OPERADORES_ARITMETICOS =
        new java.util.HashSet<>(java.util.Arrays.asList("+", "-", "*", "/", "%"));

    private static final String[] SUFIJOS_NUMERICOS = {
        "usize", "isize", "i128", "u128", "i64", "u64", "i32", "u32",
        "i16", "u16", "i8", "u8", "f64", "f32"
    };

    private final ResultadoCodigoIntermedio resultado = new ResultadoCodigoIntermedio();
    private int contadorGlobal = 0;

    /** Entorno de valores conocidos de variables, simulando la ejecución secuencial de la función actual. */
    private Map<String, Valor> entorno = new HashMap<>();

    public ResultadoCodigoIntermedio generar(AST.Programa programa) {
        if (programa == null) return resultado;
        for (AST.DeclFuncion f : programa.funciones) {
            entorno = new HashMap<>(); // cada función tiene su propio espacio de variables
            recorrerBloque(f.cuerpo);
        }
        return resultado;
    }

    // ---------------- Recorrido del AST buscando expresiones aritméticas ----------------

    private void recorrerBloque(AST.Bloque b) {
        if (b == null) return;
        for (AST.Sentencia s : b.sentencias) {
            recorrerSentencia(s);
        }
        if (b.exprFinal != null) {
            Valor val = evalNumerico(b.exprFinal);
            procesarSiAritmetica(b.exprFinal, "expresión final de bloque (línea " + b.exprFinal.linea + ")", null, val);
            recorrerAnidados(b.exprFinal);
        }
    }

    private void recorrerSentencia(AST.Sentencia s) {
        if (s instanceof AST.DeclLet) {
            AST.DeclLet d = (AST.DeclLet) s;
            if (d.inicializador != null) {
                Valor val = evalNumerico(d.inicializador);
                if (val != null) entorno.put(d.nombre, val); else entorno.remove(d.nombre);
                procesarSiAritmetica(d.inicializador, "let " + d.nombre + " = ...", d.nombre, val);
                recorrerAnidados(d.inicializador);
            }
        } else if (s instanceof AST.SentExpr) {
            AST.Expr e = ((AST.SentExpr) s).expr;
            if (e instanceof AST.Asignacion) {
                AST.Asignacion a = (AST.Asignacion) e;
                String destino = a.objetivo instanceof AST.Identificador ? ((AST.Identificador) a.objetivo).nombre : aTexto(a.objetivo);
                Valor valNuevo = evalNumerico(a.valor);
                Valor valFinal = combinarAsignacion(a.operador, destino, valNuevo);
                if (valFinal != null) entorno.put(destino, valFinal); else entorno.remove(destino);
                procesarSiAritmetica(a.valor, "asignación a '" + destino + "'", destino, valFinal);
                recorrerAnidados(a.valor);
            } else {
                Valor val = evalNumerico(e);
                procesarSiAritmetica(e, "instrucción suelta (línea " + e.linea + ")", null, val);
                recorrerAnidados(e);
            }
        } else if (s instanceof AST.SentReturn) {
            AST.SentReturn r = (AST.SentReturn) s;
            if (r.valor != null) {
                Valor val = evalNumerico(r.valor);
                procesarSiAritmetica(r.valor, "return ...", null, val);
                recorrerAnidados(r.valor);
            }
        }
    }

    /** Para "x += expr" etc., combina el valor anterior de x con el nuevo valor usando el operador base. */
    private Valor combinarAsignacion(String operador, String destino, Valor nuevo) {
        if (operador.equals("=")) return nuevo;
        Valor anterior = entorno.get(destino);
        if (anterior == null || nuevo == null) return null;
        String opBase = operador.substring(0, 1);
        return operar(opBase, anterior, nuevo);
    }

    /** Baja a los bloques anidados dentro de estructuras de control, para no perder expresiones aritméticas dentro de if/while/for/loop/match. */
    private void recorrerAnidados(AST.Expr e) {
        if (e == null) return;
        if (e instanceof AST.ExprIf) {
            AST.ExprIf eif = (AST.ExprIf) e;
            recorrerBloque(eif.entonces);
            if (eif.sino instanceof AST.ExprBloque) recorrerBloque(((AST.ExprBloque) eif.sino).bloque);
            else if (eif.sino instanceof AST.ExprIf) recorrerAnidados(eif.sino);
        } else if (e instanceof AST.ExprWhile) {
            recorrerBloque(((AST.ExprWhile) e).cuerpo);
        } else if (e instanceof AST.ExprFor) {
            recorrerBloque(((AST.ExprFor) e).cuerpo);
        } else if (e instanceof AST.ExprLoop) {
            recorrerBloque(((AST.ExprLoop) e).cuerpo);
        } else if (e instanceof AST.ExprBloque) {
            recorrerBloque(((AST.ExprBloque) e).bloque);
        } else if (e instanceof AST.ExprMatch) {
            for (AST.BrazoMatch brazo : ((AST.ExprMatch) e).brazos) {
                if (brazo.cuerpo instanceof AST.ExprBloque) recorrerBloque(((AST.ExprBloque) brazo.cuerpo).bloque);
                else {
                    Valor val = evalNumerico(brazo.cuerpo);
                    procesarSiAritmetica(brazo.cuerpo, "brazo de match (línea " + brazo.cuerpo.linea + ")", null, val);
                    recorrerAnidados(brazo.cuerpo);
                }
            }
        } else if (e instanceof AST.Asignacion) {
            recorrerAnidados(((AST.Asignacion) e).valor);
        }
    }

    private void procesarSiAritmetica(AST.Expr e, String contexto, String variableDestino, Valor valorFinal) {
        if (!contieneAritmetica(e)) return;

        BloqueExpresion bloque = new BloqueExpresion();
        bloque.linea = e.linea;
        bloque.contexto = contexto;
        bloque.variableDestino = variableDestino;
        bloque.infijo = aTexto(e);
        bloque.prefijo = prefijo(e);
        bloque.posfijo = posfijo(e);

        int[] contadorLocal = {0};
        String resultadoFinal = procesarCuadruplos(e, bloque.cuadruplos, contadorLocal);

        if (variableDestino != null) {
            Cuadruplo copia = new Cuadruplo(bloque.cuadruplos.size() + 1, "=", resultadoFinal, null, variableDestino);
            bloque.cuadruplos.add(copia);
        }

        // Renumerar localmente 1..N (tabla propia de esta expresión) y acumular en la tabla global
        for (int i = 0; i < bloque.cuadruplos.size(); i++) {
            Cuadruplo c = bloque.cuadruplos.get(i);
            c.numero = i + 1;
            Cuadruplo copiaGlobal = new Cuadruplo(++contadorGlobal, c.operador, c.arg1, c.arg2, c.resultado);
            resultado.cuadruplosGlobales.add(copiaGlobal);
        }

        // Valor final evaluado con los datos conocidos hasta este punto del programa
        String etiqueta = (variableDestino != null) ? variableDestino : "resultado";
        if (valorFinal != null) {
            bloque.resultadoEvaluado = etiqueta + " := " + formatValor(valorFinal);
        } else {
            bloque.resultadoEvaluado = null; // no se puede determinar en tiempo de compilación (depende de parámetros, llamadas, etc.)
        }

        resultado.bloques.add(bloque);
    }

    // ---------------- ¿Contiene aritmética? ----------------

    private boolean contieneAritmetica(AST.Expr e) {
        if (e == null) return false;
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            if (OPERADORES_ARITMETICOS.contains(b.operador)) return true;
            return contieneAritmetica(b.izquierda) || contieneAritmetica(b.derecha);
        }
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            if (u.operador.equals("-")) return true;
            return contieneAritmetica(u.operando);
        }
        if (e instanceof AST.Cast) return contieneAritmetica(((AST.Cast) e).expr);
        if (esPow(e)) return true;
        return false;
    }

    // ---------------- Soporte de '.pow(...)' como operación aritmética (potencia) ----------------

    /** ".pow(exp)" sobre un entero se trata como una operación aritmética más (equivalente a '^'). */
    private boolean esPow(AST.Expr e) {
        if (!(e instanceof AST.LlamadaMetodo)) return false;
        AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
        return m.nombreMetodo.equals("pow") && m.argumentos.size() == 1;
    }

    /** Quita un 'as Tipo' envolvente (ej. "d as u32") para mostrar solo el valor relevante en las tablas. */
    private AST.Expr desenvolverCast(AST.Expr e) {
        while (e instanceof AST.Cast) e = ((AST.Cast) e).expr;
        return e;
    }

    // ---------------- Generación de cuádruplos (post-orden) ----------------

    private String procesarCuadruplos(AST.Expr e, List<Cuadruplo> lista, int[] contador) {
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            String a1 = procesarCuadruplos(b.izquierda, lista, contador);
            String a2 = procesarCuadruplos(b.derecha, lista, contador);
            if (OPERADORES_ARITMETICOS.contains(b.operador)) {
                String t = "t" + (++contador[0]);
                lista.add(new Cuadruplo(lista.size() + 1, b.operador, a1, a2, t));
                return t;
            }
            return "(" + a1 + " " + b.operador + " " + a2 + ")";
        }
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            String a1 = procesarCuadruplos(u.operando, lista, contador);
            if (u.operador.equals("-")) {
                String t = "t" + (++contador[0]);
                lista.add(new Cuadruplo(lista.size() + 1, "neg", a1, null, t));
                return t;
            }
            return u.operador + a1;
        }
        if (e instanceof AST.Cast) {
            return procesarCuadruplos(((AST.Cast) e).expr, lista, contador);
        }
        if (esPow(e)) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            String a1 = procesarCuadruplos(m.receptor, lista, contador);
            String a2 = procesarCuadruplos(desenvolverCast(m.argumentos.get(0)), lista, contador);
            String t = "t" + (++contador[0]);
            lista.add(new Cuadruplo(lista.size() + 1, "^", a1, a2, t));
            return t;
        }
        return aTexto(e);
    }

    // ---------------- Evaluación numérica (usa los valores ya asignados en el código) ----------------

    private Valor evalNumerico(AST.Expr e) {
        if (e == null) return null;
        if (e instanceof AST.Literal) return evalLiteral((AST.Literal) e);
        if (e instanceof AST.Identificador) return entorno.get(((AST.Identificador) e).nombre);
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            if (u.operador.equals("-")) {
                Valor v = evalNumerico(u.operando);
                return v == null ? null : new Valor(-v.num, v.entero);
            }
            return null; // !, &, &mut, * no son evaluables numéricamente
        }
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            if (!OPERADORES_ARITMETICOS.contains(b.operador)) return null;
            Valor izq = evalNumerico(b.izquierda);
            Valor der = evalNumerico(b.derecha);
            if (izq == null || der == null) return null;
            return operar(b.operador, izq, der);
        }
        if (e instanceof AST.Cast) {
            Valor v = evalNumerico(((AST.Cast) e).expr);
            if (v == null) return null;
            boolean entero = ((AST.Cast) e).tipoTexto.matches("i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize");
            double num = entero ? (long) v.num : v.num;
            return new Valor(num, entero);
        }
        if (esPow(e)) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            Valor base = evalNumerico(m.receptor);
            Valor exp = evalNumerico(desenvolverCast(m.argumentos.get(0)));
            if (base == null || exp == null) return null;
            double r = Math.pow(base.num, exp.num);
            return new Valor(base.entero ? Math.round(r) : r, base.entero);
        }
        return null; // llamadas, campos, índices, etc.: no evaluables sin un intérprete completo
    }

    private Valor operar(String operador, Valor izq, Valor der) {
        boolean entero = izq.entero && der.entero;
        if (entero) {
            long a = Math.round(izq.num);
            long b = Math.round(der.num);
            long r;
            switch (operador) {
                case "+": r = a + b; break;
                case "-": r = a - b; break;
                case "*": r = a * b; break;
                case "/": if (b == 0) return null; r = a / b; break;   // división entera trunca hacia cero, igual que Rust
                case "%": if (b == 0) return null; r = a % b; break;
                default: return null;
            }
            return new Valor(r, true);
        } else {
            double a = izq.num, b = der.num;
            double r;
            switch (operador) {
                case "+": r = a + b; break;
                case "-": r = a - b; break;
                case "*": r = a * b; break;
                case "/": if (b == 0) return null; r = a / b; break;
                case "%": if (b == 0) return null; r = a % b; break;
                default: return null;
            }
            return new Valor(r, false);
        }
    }

    private Valor evalLiteral(AST.Literal lit) {
        if (lit.tokenTipo.equals("ENTERO")) {
            String limpio = quitarSufijo(lit.texto).replace("_", "");
            try { return new Valor(Double.parseDouble(limpio), true); } catch (NumberFormatException ex) { return null; }
        }
        if (lit.tokenTipo.equals("FLOTANTE")) {
            String limpio = quitarSufijo(lit.texto).replace("_", "");
            try { return new Valor(Double.parseDouble(limpio), false); } catch (NumberFormatException ex) { return null; }
        }
        return null; // strings, chars, booleanos: no numéricos
    }

    private String quitarSufijo(String texto) {
        for (String suf : SUFIJOS_NUMERICOS) {
            if (texto.endsWith(suf) && texto.length() > suf.length() && Character.isDigit(texto.charAt(texto.length() - suf.length() - 1))) {
                return texto.substring(0, texto.length() - suf.length());
            }
        }
        return texto;
    }

    private String formatValor(Valor v) {
        if (v.entero) return String.valueOf(Math.round(v.num));
        BigDecimal bd = BigDecimal.valueOf(v.num).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        String s = bd.toPlainString();
        if (!s.contains(".")) s = s + ".0";
        return s;
    }

    // ---------------- Prefijo / Posfijo / Infijo (reconstruido) ----------------

    private String prefijo(AST.Expr e) {
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            return simbolo(b.operador) + " " + prefijo(b.izquierda) + " " + prefijo(b.derecha);
        }
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            if (u.operador.equals("-")) return "neg " + prefijo(u.operando);
            return u.operador + " " + prefijo(u.operando);
        }
        if (e instanceof AST.Cast) return prefijo(((AST.Cast) e).expr);
        if (esPow(e)) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            return "^ " + prefijo(m.receptor) + " " + prefijo(desenvolverCast(m.argumentos.get(0)));
        }
        return aTexto(e);
    }

    private String posfijo(AST.Expr e) {
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            return posfijo(b.izquierda) + " " + posfijo(b.derecha) + " " + simbolo(b.operador);
        }
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            if (u.operador.equals("-")) return posfijo(u.operando) + " neg";
            return posfijo(u.operando) + " " + u.operador;
        }
        if (e instanceof AST.Cast) return posfijo(((AST.Cast) e).expr);
        if (esPow(e)) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            return posfijo(m.receptor) + " " + posfijo(desenvolverCast(m.argumentos.get(0))) + " ^";
        }
        return aTexto(e);
    }

    private String simbolo(String op) { return op; }

    /** Reconstrucción textual (infija, con paréntesis) de cualquier expresión del AST. */
    private String aTexto(AST.Expr e) {
        if (e == null) return "";
        if (e instanceof AST.Literal) return ((AST.Literal) e).texto;
        if (e instanceof AST.Identificador) return ((AST.Identificador) e).nombre;
        if (e instanceof AST.Ruta) return String.join("::", ((AST.Ruta) e).segmentos);
        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            return u.operador + aTexto(u.operando);
        }
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            return "(" + aTexto(b.izquierda) + " " + b.operador + " " + aTexto(b.derecha) + ")";
        }
        if (e instanceof AST.Cast) {
            AST.Cast c = (AST.Cast) e;
            return "(" + aTexto(c.expr) + " as " + c.tipoTexto + ")";
        }
        if (e instanceof AST.Asignacion) {
            AST.Asignacion a = (AST.Asignacion) e;
            return aTexto(a.objetivo) + " " + a.operador + " " + aTexto(a.valor);
        }
        if (e instanceof AST.Llamada) {
            AST.Llamada c = (AST.Llamada) e;
            StringBuilder sb = new StringBuilder(aTexto(c.callee)).append("(");
            for (int i = 0; i < c.argumentos.size(); i++) { if (i > 0) sb.append(", "); sb.append(aTexto(c.argumentos.get(i))); }
            return sb.append(")").toString();
        }
        if (e instanceof AST.LlamadaMetodo) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            if (esPow(m)) {
                return "(" + aTexto(m.receptor) + " ^ " + aTexto(desenvolverCast(m.argumentos.get(0))) + ")";
            }
            StringBuilder sb = new StringBuilder(aTexto(m.receptor)).append(".").append(m.nombreMetodo).append("(");
            for (int i = 0; i < m.argumentos.size(); i++) { if (i > 0) sb.append(", "); sb.append(aTexto(m.argumentos.get(i))); }
            return sb.append(")").toString();
        }
        if (e instanceof AST.LlamadaMacro) {
            AST.LlamadaMacro m = (AST.LlamadaMacro) e;
            StringBuilder sb = new StringBuilder(m.nombre).append("!(");
            for (int i = 0; i < m.argumentos.size(); i++) { if (i > 0) sb.append(", "); sb.append(aTexto(m.argumentos.get(i))); }
            return sb.append(")").toString();
        }
        if (e instanceof AST.AccesoCampo) {
            AST.AccesoCampo ac = (AST.AccesoCampo) e;
            return aTexto(ac.objeto) + "." + ac.campo;
        }
        if (e instanceof AST.AccesoIndice) {
            AST.AccesoIndice ai = (AST.AccesoIndice) e;
            return aTexto(ai.objeto) + "[" + aTexto(ai.indice) + "]";
        }
        if (e instanceof AST.Tupla) {
            AST.Tupla t = (AST.Tupla) e;
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < t.elementos.size(); i++) { if (i > 0) sb.append(", "); sb.append(aTexto(t.elementos.get(i))); }
            return sb.append(")").toString();
        }
        if (e instanceof AST.ArregloLiteral) {
            AST.ArregloLiteral a = (AST.ArregloLiteral) e;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < a.elementos.size(); i++) { if (i > 0) sb.append(", "); sb.append(aTexto(a.elementos.get(i))); }
            return sb.append("]").toString();
        }
        return "<expr>";
    }
}
