package Optimizacion;

import Analizador_sintactico.AST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Convierte un AST (posiblemente ya optimizado) de vuelta a código fuente Rust
 * legible, con sangrado de 4 espacios y sin líneas vacías. Se usa para mostrar el resultado en el
 * panel "Código optimizado".
 *
 * Limitaciones (por lo que el AST no conserva):
 *  - Los comentarios no aparecen en la salida.
 *  - El tamaño de los tipos arreglo ([i32; 5]) se imprime como [i32].
 *  - Los paréntesis se regeneran según la precedencia de operadores.
 */
public class GeneradorCodigoRust {

    private int nivel;

    /** Texto Rust de una sola expresión (para los mensajes de la interfaz). */
    public String expresion(AST.Expr e) {
        nivel = 0;
        return expr(e, 0);
    }

    public String generar(AST.Programa programa) {
        nivel = 0;
        // El AST separa structs y funciones; se reordenan según su línea original.
        List<AST.Nodo> items = new ArrayList<>();
        items.addAll(programa.estructuras);
        items.addAll(programa.funciones);
        items.sort(Comparator.comparingInt(n -> n.linea));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            AST.Nodo n = items.get(i);
            if (n instanceof AST.DeclStruct) sb.append(estructura((AST.DeclStruct) n));
            else sb.append(funcion((AST.DeclFuncion) n));
            sb.append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Declaraciones
    // ------------------------------------------------------------------
    private String estructura(AST.DeclStruct d) {
        StringBuilder sb = new StringBuilder("struct ").append(d.nombre).append(" {\n");
        for (AST.CampoDecl c : d.campos) {
            sb.append("    ").append(c.nombre).append(": ").append(c.tipoTexto).append(",\n");
        }
        return sb.append("}").toString();
    }

    private String funcion(AST.DeclFuncion f) {
        StringBuilder sb = new StringBuilder("fn ").append(f.nombre).append("(");
        for (int i = 0; i < f.parametros.size(); i++) {
            if (i > 0) sb.append(", ");
            AST.Parametro p = f.parametros.get(i);
            if (p.nombre.equals("self")) { sb.append("self"); continue; }
            if (p.esMut) sb.append("mut ");
            sb.append(p.nombre).append(": ").append(p.tipoTexto);
        }
        sb.append(")");
        if (f.tipoRetornoTexto != null) sb.append(" -> ").append(f.tipoRetornoTexto);
        sb.append(" ").append(bloque(f.cuerpo));
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Bloques y sentencias
    // ------------------------------------------------------------------
    private String ind() { return "    ".repeat(nivel); }

    private String bloque(AST.Bloque b) {
        StringBuilder sb = new StringBuilder("{\n");
        nivel++;
        for (AST.Sentencia s : b.sentencias) {
            sb.append(ind()).append(sentencia(s)).append("\n");
        }
        if (b.exprFinal != null) {
            sb.append(ind()).append(expr(b.exprFinal, 0)).append("\n");
        }
        nivel--;
        return sb.append(ind()).append("}").toString();
    }

    private String sentencia(AST.Sentencia s) {
        if (s instanceof AST.DeclLet) {
            AST.DeclLet d = (AST.DeclLet) s;
            StringBuilder sb = new StringBuilder("let ");
            if (d.esMutable) sb.append("mut ");
            sb.append(d.nombre);
            if (d.tipoTexto != null) sb.append(": ").append(d.tipoTexto);
            if (d.inicializador != null) sb.append(" = ").append(expr(d.inicializador, 0));
            return sb.append(";").toString();
        }
        if (s instanceof AST.SentReturn) {
            AST.SentReturn r = (AST.SentReturn) s;
            return "return" + (r.valor != null ? " " + expr(r.valor, 0) : "") + ";";
        }
        if (s instanceof AST.SentSalto) {
            AST.SentSalto j = (AST.SentSalto) s;
            return j.tipo + (j.valor != null ? " " + expr(j.valor, 0) : "") + ";";
        }
        AST.SentExpr se = (AST.SentExpr) s;
        String txt = expr(se.expr, 0);
        return esBloqueLike(se.expr) ? txt : txt + ";";
    }

    private boolean esBloqueLike(AST.Expr e) {
        return e instanceof AST.ExprIf || e instanceof AST.ExprMatch || e instanceof AST.ExprBloque
            || e instanceof AST.ExprLoop || e instanceof AST.ExprWhile || e instanceof AST.ExprFor;
    }

    // ------------------------------------------------------------------
    // Expresiones (con precedencia para decidir los paréntesis)
    //   1 asignación | 2 rango | 3 || | 4 && | 5 comparación | 6 + - | 7 * / %
    //   8 cast | 9 unario | 10 postfijo | 11 primario
    // ------------------------------------------------------------------
    private int prec(AST.Expr e) {
        if (e instanceof AST.Asignacion) return 1;
        if (e instanceof AST.Rango) return 2;
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            switch (b.operador) {
                case "||": return 3;
                case "&&": return 4;
                case "+": case "-": return 6;
                case "*": case "/": case "%": return 7;
                default: return 5; // comparaciones
            }
        }
        if (e instanceof AST.Cast) return 8;
        if (e instanceof AST.Unario) return 9;
        if (e instanceof AST.AccesoCampo || e instanceof AST.AccesoIndice
            || e instanceof AST.Llamada || e instanceof AST.LlamadaMetodo) return 10;
        return 11;
    }

    private String expr(AST.Expr e, int minPrec) {
        String s = exprPlana(e);
        boolean parentesis = prec(e) < minPrec || (esBloqueLike(e) && minPrec >= 3);
        return parentesis ? "(" + s + ")" : s;
    }

    private String exprPlana(AST.Expr e) {
        if (e instanceof AST.Literal) return ((AST.Literal) e).texto;
        if (e instanceof AST.Identificador) return ((AST.Identificador) e).nombre;
        if (e instanceof AST.Ruta) return String.join("::", ((AST.Ruta) e).segmentos);

        if (e instanceof AST.Unario) {
            AST.Unario u = (AST.Unario) e;
            String op = u.operador.equals("&mut") ? "&mut " : u.operador;
            String operando = expr(u.operando, 9);
            if (!op.endsWith(" ") && operando.startsWith(op)) op += " "; // evita "--x"
            return op + operando;
        }
        if (e instanceof AST.Binario) {
            AST.Binario b = (AST.Binario) e;
            int p = prec(b);
            boolean comparacion = p == 5;
            String izq = expr(b.izquierda, comparacion ? p + 1 : p);
            String der = expr(b.derecha, p + 1);
            return izq + " " + b.operador + " " + der;
        }
        if (e instanceof AST.Cast) {
            AST.Cast c = (AST.Cast) e;
            return expr(c.expr, 9) + " as " + c.tipoTexto;
        }
        if (e instanceof AST.Asignacion) {
            AST.Asignacion a = (AST.Asignacion) e;
            return expr(a.objetivo, 2) + " " + a.operador + " " + expr(a.valor, 1);
        }
        if (e instanceof AST.Llamada) {
            AST.Llamada l = (AST.Llamada) e;
            return expr(l.callee, 10) + "(" + lista(l.argumentos) + ")";
        }
        if (e instanceof AST.LlamadaMacro) {
            AST.LlamadaMacro m = (AST.LlamadaMacro) e;
            return m.nombre + "!(" + lista(m.argumentos) + ")";
        }
        if (e instanceof AST.LlamadaMetodo) {
            AST.LlamadaMetodo m = (AST.LlamadaMetodo) e;
            return expr(m.receptor, 10) + "." + m.nombreMetodo + "(" + lista(m.argumentos) + ")";
        }
        if (e instanceof AST.AccesoCampo) {
            AST.AccesoCampo a = (AST.AccesoCampo) e;
            return expr(a.objeto, 10) + "." + a.campo;
        }
        if (e instanceof AST.AccesoIndice) {
            AST.AccesoIndice a = (AST.AccesoIndice) e;
            return expr(a.objeto, 10) + "[" + expr(a.indice, 0) + "]";
        }
        if (e instanceof AST.Tupla) {
            AST.Tupla t = (AST.Tupla) e;
            if (t.elementos.isEmpty()) return "()";
            if (t.elementos.size() == 1) return "(" + expr(t.elementos.get(0), 0) + ",)";
            return "(" + lista(t.elementos) + ")";
        }
        if (e instanceof AST.ArregloLiteral) {
            return "[" + lista(((AST.ArregloLiteral) e).elementos) + "]";
        }
        if (e instanceof AST.InstanciaStruct) {
            AST.InstanciaStruct is = (AST.InstanciaStruct) e;
            if (is.campos.isEmpty()) return is.nombreStruct + " {}";
            List<String> cs = new ArrayList<>();
            for (AST.CampoInit c : is.campos) cs.add(c.nombre + ": " + expr(c.valor, 0));
            return is.nombreStruct + " { " + String.join(", ", cs) + " }";
        }
        if (e instanceof AST.Rango) {
            AST.Rango r = (AST.Rango) e;
            return (r.inicio != null ? expr(r.inicio, 3) : "")
                + (r.inclusivo ? "..=" : "..")
                + (r.fin != null ? expr(r.fin, 3) : "");
        }
        if (e instanceof AST.ExprIf) return exprIf((AST.ExprIf) e);
        if (e instanceof AST.ExprMatch) {
            AST.ExprMatch m = (AST.ExprMatch) e;
            StringBuilder sb = new StringBuilder("match ").append(expr(m.sujeto, 0)).append(" {\n");
            nivel++;
            for (AST.BrazoMatch b : m.brazos) {
                sb.append(ind()).append(b.patron.texto).append(" => ").append(expr(b.cuerpo, 0)).append(",\n");
            }
            nivel--;
            return sb.append(ind()).append("}").toString();
        }
        if (e instanceof AST.ExprBloque) return bloque(((AST.ExprBloque) e).bloque);
        if (e instanceof AST.ExprLoop) return "loop " + bloque(((AST.ExprLoop) e).cuerpo);
        if (e instanceof AST.ExprWhile) {
            AST.ExprWhile w = (AST.ExprWhile) e;
            return "while " + expr(w.condicion, 0) + " " + bloque(w.cuerpo);
        }
        if (e instanceof AST.ExprFor) {
            AST.ExprFor f = (AST.ExprFor) e;
            return "for " + f.variable + " in " + expr(f.iterable, 2) + " " + bloque(f.cuerpo);
        }
        return "/* expresión no soportada */";
    }

    private String exprIf(AST.ExprIf i) {
        StringBuilder sb = new StringBuilder("if ").append(expr(i.condicion, 0)).append(" ").append(bloque(i.entonces));
        if (i.sino == null) return sb.toString();
        sb.append(" else ");
        if (i.sino instanceof AST.ExprIf) {
            sb.append(exprIf((AST.ExprIf) i.sino));
        } else if (i.sino instanceof AST.ExprBloque) {
            sb.append(bloque(((AST.ExprBloque) i.sino).bloque));
        } else { // la rama else quedó reducida a una sola expresión
            nivel++;
            String cuerpo = ind() + expr(i.sino, 0);
            nivel--;
            sb.append("{\n").append(cuerpo).append("\n").append(ind()).append("}");
        }
        return sb.toString();
    }

    private String lista(List<AST.Expr> es) {
        List<String> partes = new ArrayList<>();
        for (AST.Expr e : es) partes.add(expr(e, 0));
        return String.join(", ", partes);
    }
}
