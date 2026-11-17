package Analizador_sintactico;

import Analizador_lexico.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Analizador sintáctico descendente recursivo (recursive-descent) para un
 * subconjunto de Rust.
 *
 * Gramática (resumen, de menor a mayor precedencia en expresiones):
 *   programa      -> (declFuncion | declStruct)*
 *   declFuncion   -> "fn" IDENT "(" parametros? ")" ("->" tipo)? bloque
 *   declStruct    -> "struct" IDENT "{" (campo ("," campo)* ","?)? "}"
 *   bloque        -> "{" sentencia* expr? "}"
 *   sentencia     -> declLet | sentReturn | sentSalto | sentExpr
 *   declLet       -> "let" "mut"? IDENT (":" tipo)? ("=" expr)? ";"
 *   expr          -> asignacion
 *   asignacion    -> rango (("="|"+="|"-="|"*="|"/="|"%=") asignacion)?
 *   rango         -> or (".." "."? or)?
 *   or            -> and ("||" and)*
 *   and           -> comparacion ("&&" comparacion)*
 *   comparacion   -> suma (("=="|"!="|"<"|">"|"<="|">=") suma)?   (no encadenable)
 *   suma          -> mult (("+"|"-") mult)*
 *   mult          -> cast (("*"|"/"|"%") cast)*
 *   cast          -> unario ("as" tipo)*
 *   unario        -> ("-"|"!"|"&"|"&mut"|"*") unario | postfijo
 *   postfijo      -> primario ( "." IDENT | "." IDENT "(" args ")" | "(" args ")" | "[" expr "]" )*
 *   primario      -> literal | IDENT | ruta | "(" expr ")" | "[" ... "]" | bloqueExpr | if | match | while | loop | for
 */
public class RustParser {

    public static class SyntaxError {
        public int linea;
        public String mensaje;
        public SyntaxError(int linea, String mensaje) { this.linea = linea; this.mensaje = mensaje; }
    }

    public static class ResultadoSintactico {
        public boolean esValido;
        public List<SyntaxError> errores = new ArrayList<>();
        public AST.Programa programa;
    }

    private static class AbortarSentencia extends RuntimeException { }

    private final List<Token> tokens;
    private int pos = 0;
    private final List<SyntaxError> errores = new ArrayList<>();
    private final java.util.Set<Integer> lineasConError = new java.util.HashSet<>();
    private static final int MAX_ERRORES = 60;

    public RustParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public ResultadoSintactico parsear() {
        ResultadoSintactico res = new ResultadoSintactico();
        AST.Programa programa = new AST.Programa();
        programa.funciones = new ArrayList<>();
        programa.estructuras = new ArrayList<>();

        while (!finDeArchivo() && errores.size() < MAX_ERRORES) {
            try {
                if (chequear("STRUCT")) {
                    programa.estructuras.add(declStruct());
                } else if (chequear("FN")) {
                    programa.funciones.add(declFuncion());
                } else {
                    error("Se esperaba una declaración de función ('fn') o estructura ('struct') en el ámbito global");
                    avanzar();
                }
            } catch (AbortarSentencia ex) {
                sincronizarDeclaracion();
            }
        }

        res.programa = programa;
        res.errores = errores;
        res.esValido = errores.isEmpty();
        return res;
    }

    // ---------------- Declaraciones ----------------

    private AST.DeclStruct declStruct() {
        AST.DeclStruct d = new AST.DeclStruct();
        d.linea = actual().linea;
        avanzar(); // struct
        d.nombre = esperarIdentificador("Se esperaba el nombre de la estructura");
        esperar("LLAVE_A", "Se esperaba '{' después del nombre de la estructura");
        d.campos = new ArrayList<>();
        while (!chequear("LLAVE_C") && !finDeArchivo()) {
            AST.CampoDecl campo = new AST.CampoDecl();
            campo.linea = actual().linea;
            campo.nombre = esperarIdentificador("Se esperaba el nombre del campo");
            esperar("DOS_PUNTOS", "Se esperaba ':' después del nombre del campo");
            campo.tipoTexto = tipo();
            d.campos.add(campo);
            if (chequear("COMA")) avanzar(); else break;
        }
        esperar("LLAVE_C", "Se esperaba '}' para cerrar la estructura");
        return d;
    }

    private AST.DeclFuncion declFuncion() {
        AST.DeclFuncion f = new AST.DeclFuncion();
        f.linea = actual().linea;
        avanzar(); // fn
        f.nombre = esperarIdentificador("Se esperaba el nombre de la función");
        esperar("PAR_A", "Se esperaba '(' después del nombre de la función");
        f.parametros = new ArrayList<>();
        while (!chequear("PAR_C") && !finDeArchivo()) {
            AST.Parametro p = new AST.Parametro();
            p.linea = actual().linea;
            if (chequear("SELF")) {
                avanzar();
                p.nombre = "self";
                p.tipoTexto = "Self";
                f.parametros.add(p);
                if (chequear("COMA")) { avanzar(); continue; } else break;
            }
            if (chequear("MUT")) { p.esMut = true; avanzar(); }
            p.nombre = esperarIdentificador("Se esperaba el nombre del parámetro");
            esperar("DOS_PUNTOS", "Se esperaba ':' después del nombre del parámetro");
            p.tipoTexto = tipo();
            f.parametros.add(p);
            if (chequear("COMA")) avanzar(); else break;
        }
        esperar("PAR_C", "Se esperaba ')' para cerrar la lista de parámetros");
        if (chequear("FLECHA")) {
            avanzar();
            f.tipoRetornoTexto = tipo();
        }
        f.cuerpo = bloque();
        return f;
    }

    private String tipo() {
        StringBuilder sb = new StringBuilder();
        while (chequear("OP_REF")) { sb.append("&"); avanzar(); if (chequear("MUT")) { sb.append("mut "); avanzar(); } }
        if (chequear("CORCHETE_A")) {
            avanzar();
            String interno = tipo();
            if (chequear("PUNTO_COMA")) { avanzar(); if (!chequear("CORCHETE_C")) avanzar(); }
            esperar("CORCHETE_C", "Se esperaba ']' en el tipo arreglo");
            return "[" + interno + "]";
        }
        if (chequear("PAR_A")) {
            avanzar();
            List<String> elems = new ArrayList<>();
            while (!chequear("PAR_C") && !finDeArchivo()) {
                elems.add(tipo());
                if (chequear("COMA")) avanzar(); else break;
            }
            esperar("PAR_C", "Se esperaba ')' en el tipo tupla");
            return "(" + String.join(", ", elems) + ")";
        }
        String nombre = actual().lexema;
        if (!esIdentificadorTipo()) {
            error("Se esperaba un tipo válido");
            return sb + "desconocido";
        }
        avanzar();
        sb.append(nombre);
        // genéricos simples Vec<T>, Option<T>
        if (chequear("OP_COMP") && actual().lexema.equals("<")) {
            avanzar();
            sb.append("<");
            sb.append(tipo());
            while (chequear("COMA")) { avanzar(); sb.append(", ").append(tipo()); }
            if (chequear("OP_COMP") && actual().lexema.equals(">")) { avanzar(); }
            sb.append(">");
        }
        return sb.toString();
    }

    private boolean esIdentificadorTipo() {
        String t = actual().tipo;
        return t.equals("IDENTIFICADOR") || t.equals("TIPO_INT") || t.equals("TIPO_FLOAT")
            || t.equals("TIPO_BOOL") || t.equals("TIPO_CHAR") || t.equals("TIPO_STR") || t.equals("SELF");
    }

    // ---------------- Bloques y sentencias ----------------

    private AST.Bloque bloque() {
        AST.Bloque b = new AST.Bloque();
        b.linea = actual().linea;
        esperar("LLAVE_A", "Se esperaba '{' para iniciar el bloque");
        b.sentencias = new ArrayList<>();
        while (!chequear("LLAVE_C") && !finDeArchivo()) {
            try {
                if (esInicioExprFinal()) {
                    AST.Expr e = expr();
                    if (chequear("PUNTO_COMA")) {
                        avanzar();
                        AST.SentExpr se = new AST.SentExpr();
                        se.linea = e.linea;
                        se.expr = e;
                        b.sentencias.add(se);
                    } else if (chequear("LLAVE_C")) {
                        b.exprFinal = e;
                        break;
                    } else {
                        AST.SentExpr se = new AST.SentExpr();
                        se.linea = e.linea;
                        se.expr = e;
                        b.sentencias.add(se);
                    }
                } else {
                    b.sentencias.add(sentencia());
                }
            } catch (AbortarSentencia ex) {
                sincronizarSentencia();
            }
        }
        esperar("LLAVE_C", "Se esperaba '}' para cerrar el bloque");
        return b;
    }

    private boolean esInicioExprFinal() {
        return !chequear("LET") && !chequear("RETURN") && !chequear("BREAK") && !chequear("CONTINUE");
    }

    private AST.Sentencia sentencia() {
        if (chequear("LET")) return declLet();
        if (chequear("RETURN")) return sentReturn();
        if (chequear("BREAK") || chequear("CONTINUE")) return sentSalto();
        AST.Expr e = expr();
        esperar("PUNTO_COMA", "Se esperaba ';' al final de la instrucción");
        AST.SentExpr se = new AST.SentExpr();
        se.linea = e.linea;
        se.expr = e;
        return se;
    }

    private AST.DeclLet declLet() {
        AST.DeclLet d = new AST.DeclLet();
        d.linea = actual().linea;
        avanzar(); // let
        if (chequear("MUT")) { d.esMutable = true; avanzar(); }
        d.nombre = esperarIdentificador("Se esperaba el nombre de la variable");
        if (chequear("DOS_PUNTOS")) { avanzar(); d.tipoTexto = tipo(); }
        if (chequear("OP_ASIG")) { avanzar(); d.inicializador = expr(); }
        esperar("PUNTO_COMA", "Se esperaba ';' al final de la declaración 'let'");
        return d;
    }

    private AST.SentReturn sentReturn() {
        AST.SentReturn r = new AST.SentReturn();
        r.linea = actual().linea;
        avanzar();
        if (!chequear("PUNTO_COMA")) {
            r.valor = expr();
        }
        esperar("PUNTO_COMA", "Se esperaba ';' al final de 'return'");
        return r;
    }

    private AST.SentSalto sentSalto() {
        AST.SentSalto s = new AST.SentSalto();
        s.linea = actual().linea;
        s.tipo = actual().tipo.equals("BREAK") ? "break" : "continue";
        avanzar();
        if (!chequear("PUNTO_COMA")) {
            s.valor = expr();
        }
        esperar("PUNTO_COMA", "Se esperaba ';' al final de '" + s.tipo + "'");
        return s;
    }

    // ---------------- Expresiones ----------------

    private AST.Expr expr() { return asignacion(); }

    private AST.Expr asignacion() {
        AST.Expr izq = rango();
        if (chequear("OP_ASIG") || chequear("OP_ASIG_COMP")) {
            String op = actual().lexema;
            int ln = actual().linea;
            avanzar();
            AST.Expr val = asignacion();
            AST.Asignacion a = new AST.Asignacion();
            a.linea = ln;
            a.objetivo = izq;
            a.operador = op;
            a.valor = val;
            return a;
        }
        return izq;
    }

    private AST.Expr rango() {
        AST.Expr izq = or();
        if (chequear("RANGO")) {
            boolean inclusivo = false;
            int ln = actual().linea;
            avanzar();
            if (chequear("OP_ASIG")) { inclusivo = true; avanzar(); }
            AST.Rango r = new AST.Rango();
            r.linea = ln;
            r.inicio = izq;
            r.inclusivo = inclusivo;
            if (!chequear("LLAVE_A") && !chequear("PAR_C") && !chequear("PUNTO_COMA")) {
                r.fin = or();
            }
            return r;
        }
        return izq;
    }

    private AST.Expr or() {
        AST.Expr izq = and();
        while (chequear("OP_LOG") && actual().lexema.equals("||")) {
            int ln = actual().linea;
            avanzar();
            AST.Expr der = and();
            izq = binario(izq, der, "||", "OP_LOG", ln);
        }
        return izq;
    }

    private AST.Expr and() {
        AST.Expr izq = comparacion();
        while (chequear("OP_LOG") && actual().lexema.equals("&&")) {
            int ln = actual().linea;
            avanzar();
            AST.Expr der = comparacion();
            izq = binario(izq, der, "&&", "OP_LOG", ln);
        }
        return izq;
    }

    private AST.Expr comparacion() {
        AST.Expr izq = suma();
        if (chequear("OP_COMP")) {
            String op = actual().lexema;
            int ln = actual().linea;
            avanzar();
            AST.Expr der = suma();
            izq = binario(izq, der, op, "OP_COMP", ln);
        }
        return izq;
    }

    private AST.Expr suma() {
        AST.Expr izq = mult();
        while (chequear("OP_SUMA") || chequear("OP_RESTA")) {
            String op = actual().lexema;
            String tt = actual().tipo;
            int ln = actual().linea;
            avanzar();
            AST.Expr der = mult();
            izq = binario(izq, der, op, tt, ln);
        }
        return izq;
    }

    private AST.Expr mult() {
        AST.Expr izq = castExpr();
        while (chequear("OP_MULT") || chequear("OP_DIV") || chequear("OP_MOD")) {
            String op = actual().lexema;
            String tt = actual().tipo;
            int ln = actual().linea;
            avanzar();
            AST.Expr der = castExpr();
            izq = binario(izq, der, op, tt, ln);
        }
        return izq;
    }

    private AST.Expr castExpr() {
        AST.Expr e = unario();
        while (chequear("AS")) {
            avanzar();
            AST.Cast c = new AST.Cast();
            c.linea = e.linea;
            c.expr = e;
            c.tipoTexto = tipo();
            e = c;
        }
        return e;
    }

    private AST.Expr unario() {
        if (chequear("OP_RESTA") || chequear("OP_NEG")) {
            String op = actual().lexema;
            int ln = actual().linea;
            avanzar();
            AST.Unario u = new AST.Unario();
            u.linea = ln;
            u.operador = op;
            u.operando = unario();
            return u;
        }
        if (chequear("OP_MULT")) { // deref
            int ln = actual().linea;
            avanzar();
            AST.Unario u = new AST.Unario();
            u.linea = ln;
            u.operador = "*";
            u.operando = unario();
            return u;
        }
        if (chequear("OP_REF")) {
            int ln = actual().linea;
            avanzar();
            boolean mut = false;
            if (chequear("MUT")) { mut = true; avanzar(); }
            AST.Unario u = new AST.Unario();
            u.linea = ln;
            u.operador = mut ? "&mut" : "&";
            u.operando = unario();
            return u;
        }
        return postfijo();
    }

    private AST.Expr postfijo() {
        AST.Expr e = primario();
        while (true) {
            if (chequear("PUNTO")) {
                avanzar();
                String nombre = esperarIdentificador("Se esperaba el nombre de un campo o método después de '.'");
                if (chequear("PAR_A")) {
                    avanzar();
                    List<AST.Expr> args = listaArgumentos();
                    esperar("PAR_C", "Se esperaba ')' para cerrar la llamada al método '" + nombre + "'");
                    AST.LlamadaMetodo m = new AST.LlamadaMetodo();
                    m.linea = e.linea;
                    m.receptor = e;
                    m.nombreMetodo = nombre;
                    m.argumentos = args;
                    e = m;
                } else {
                    AST.AccesoCampo ac = new AST.AccesoCampo();
                    ac.linea = e.linea;
                    ac.objeto = e;
                    ac.campo = nombre;
                    e = ac;
                }
            } else if (chequear("PAR_A")) {
                avanzar();
                List<AST.Expr> args = listaArgumentos();
                esperar("PAR_C", "Se esperaba ')' para cerrar la llamada a la función");
                AST.Llamada ll = new AST.Llamada();
                ll.linea = e.linea;
                ll.callee = e;
                ll.argumentos = args;
                e = ll;
            } else if (chequear("CORCHETE_A")) {
                avanzar();
                AST.Expr idx = expr();
                esperar("CORCHETE_C", "Se esperaba ']' para cerrar el índice");
                AST.AccesoIndice ai = new AST.AccesoIndice();
                ai.linea = e.linea;
                ai.objeto = e;
                ai.indice = idx;
                e = ai;
            } else {
                break;
            }
        }
        return e;
    }

    private List<AST.Expr> listaArgumentos() {
        List<AST.Expr> args = new ArrayList<>();
        while (!chequear("PAR_C") && !finDeArchivo()) {
            args.add(expr());
            if (chequear("COMA")) avanzar(); else break;
        }
        return args;
    }

    private AST.Expr primario() {
        Token t = actual();

        if (chequear("ENTERO") || chequear("FLOTANTE") || chequear("STRING") || chequear("CARACTER") || chequear("BOOLEANO")) {
            avanzar();
            AST.Literal lit = new AST.Literal();
            lit.linea = t.linea;
            lit.texto = t.lexema;
            lit.tokenTipo = t.tipo;
            return lit;
        }

        if (chequear("PAR_A")) {
            avanzar();
            if (chequear("PAR_C")) { // unit ()
                avanzar();
                AST.Tupla tup = new AST.Tupla();
                tup.linea = t.linea;
                tup.elementos = new ArrayList<>();
                return tup;
            }
            AST.Expr primero = expr();
            if (chequear("COMA")) {
                List<AST.Expr> elems = new ArrayList<>();
                elems.add(primero);
                while (chequear("COMA")) {
                    avanzar();
                    if (chequear("PAR_C")) break;
                    elems.add(expr());
                }
                esperar("PAR_C", "Se esperaba ')' para cerrar la tupla");
                AST.Tupla tup = new AST.Tupla();
                tup.linea = t.linea;
                tup.elementos = elems;
                return tup;
            }
            esperar("PAR_C", "Se esperaba ')' para cerrar la expresión");
            return primero;
        }

        if (chequear("CORCHETE_A")) {
            avanzar();
            List<AST.Expr> elems = new ArrayList<>();
            while (!chequear("CORCHETE_C") && !finDeArchivo()) {
                elems.add(expr());
                if (chequear("COMA")) avanzar(); else break;
            }
            esperar("CORCHETE_C", "Se esperaba ']' para cerrar el arreglo");
            AST.ArregloLiteral arr = new AST.ArregloLiteral();
            arr.linea = t.linea;
            arr.elementos = elems;
            return arr;
        }

        if (chequear("LLAVE_A")) {
            AST.ExprBloque eb = new AST.ExprBloque();
            eb.linea = t.linea;
            eb.bloque = bloque();
            return eb;
        }

        if (chequear("IF")) return exprIf();
        if (chequear("WHILE")) return exprWhile();
        if (chequear("LOOP")) return exprLoop();
        if (chequear("FOR")) return exprFor();
        if (chequear("MATCH")) return exprMatch();

        if (chequear("IDENTIFICADOR") || chequear("SELF")) {
            String nombre = t.lexema;
            avanzar();

            // macro: ident ! (...)
            if (chequear("OP_NEG")) {
                avanzar();
                esperar("PAR_A", "Se esperaba '(' después de la macro '" + nombre + "!'");
                List<AST.Expr> args = listaArgumentos();
                esperar("PAR_C", "Se esperaba ')' para cerrar la macro '" + nombre + "!'");
                AST.LlamadaMacro m = new AST.LlamadaMacro();
                m.linea = t.linea;
                m.nombre = nombre;
                m.argumentos = args;
                return m;
            }

            // ruta: ident :: ident ...
            if (chequear("DOBLE_DOSPUNTOS")) {
                List<String> seg = new ArrayList<>();
                seg.add(nombre);
                while (chequear("DOBLE_DOSPUNTOS")) {
                    avanzar();
                    seg.add(esperarIdentificador("Se esperaba un identificador después de '::'"));
                }
                AST.Ruta r = new AST.Ruta();
                r.linea = t.linea;
                r.segmentos = seg;
                return r;
            }

            // instancia de struct: Ident { campo: valor, ... }
            if (chequear("LLAVE_A") && Character.isUpperCase(nombre.charAt(0))) {
                avanzar();
                List<AST.CampoInit> campos = new ArrayList<>();
                while (!chequear("LLAVE_C") && !finDeArchivo()) {
                    AST.CampoInit ci = new AST.CampoInit();
                    ci.linea = actual().linea;
                    ci.nombre = esperarIdentificador("Se esperaba el nombre del campo");
                    esperar("DOS_PUNTOS", "Se esperaba ':' después del nombre del campo");
                    ci.valor = expr();
                    campos.add(ci);
                    if (chequear("COMA")) avanzar(); else break;
                }
                esperar("LLAVE_C", "Se esperaba '}' para cerrar la instancia de estructura");
                AST.InstanciaStruct is = new AST.InstanciaStruct();
                is.linea = t.linea;
                is.nombreStruct = nombre;
                is.campos = campos;
                return is;
            }

            AST.Identificador id = new AST.Identificador();
            id.linea = t.linea;
            id.nombre = nombre;
            return id;
        }

        error("Se esperaba una expresión, se encontró '" + t.lexema + "'");
        throw new AbortarSentencia();
    }

    private AST.ExprIf exprIf() {
        AST.ExprIf e = new AST.ExprIf();
        e.linea = actual().linea;
        avanzar();
        e.condicion = expr();
        e.entonces = bloque();
        if (chequear("ELSE")) {
            avanzar();
            if (chequear("IF")) {
                e.sino = exprIf();
            } else {
                AST.ExprBloque eb = new AST.ExprBloque();
                eb.linea = actual().linea;
                eb.bloque = bloque();
                e.sino = eb;
            }
        }
        return e;
    }

    private AST.ExprWhile exprWhile() {
        AST.ExprWhile w = new AST.ExprWhile();
        w.linea = actual().linea;
        avanzar();
        w.condicion = expr();
        w.cuerpo = bloque();
        return w;
    }

    private AST.ExprLoop exprLoop() {
        AST.ExprLoop l = new AST.ExprLoop();
        l.linea = actual().linea;
        avanzar();
        l.cuerpo = bloque();
        return l;
    }

    private AST.ExprFor exprFor() {
        AST.ExprFor f = new AST.ExprFor();
        f.linea = actual().linea;
        avanzar();
        f.variable = esperarIdentificador("Se esperaba el nombre de la variable de iteración");
        esperar("IN", "Se esperaba 'in' en el ciclo 'for'");
        f.iterable = rango();
        f.cuerpo = bloque();
        return f;
    }

    private AST.ExprMatch exprMatch() {
        AST.ExprMatch m = new AST.ExprMatch();
        m.linea = actual().linea;
        avanzar();
        m.sujeto = expr();
        esperar("LLAVE_A", "Se esperaba '{' para iniciar el bloque 'match'");
        m.brazos = new ArrayList<>();
        while (!chequear("LLAVE_C") && !finDeArchivo()) {
            AST.BrazoMatch brazo = new AST.BrazoMatch();
            brazo.linea = actual().linea;
            AST.Patron p = new AST.Patron();
            p.linea = actual().linea;
            if (actual().lexema.equals("_")) {
                p.texto = "_"; p.tokenTipo = "_"; avanzar();
            } else {
                p.texto = actual().lexema; p.tokenTipo = actual().tipo; avanzar();
            }
            brazo.patron = p;
            esperar("FLECHA_GRUESA", "Se esperaba '=>' en el brazo de 'match'");
            brazo.cuerpo = expr();
            m.brazos.add(brazo);
            if (chequear("COMA")) avanzar();
        }
        esperar("LLAVE_C", "Se esperaba '}' para cerrar el bloque 'match'");
        return m;
    }

    private AST.Binario binario(AST.Expr izq, AST.Expr der, String op, String tipoTok, int ln) {
        AST.Binario b = new AST.Binario();
        b.linea = ln;
        b.izquierda = izq;
        b.derecha = der;
        b.operador = op;
        b.tokenTipo = tipoTok;
        return b;
    }

    // ---------------- Utilidades de lectura de tokens ----------------

    private Token actual() {
        if (pos < tokens.size()) return tokens.get(pos);
        int ultLinea = tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).linea;
        return new Token("", "EOF", -1, ultLinea);
    }

    private boolean finDeArchivo() { return pos >= tokens.size(); }

    private boolean chequear(String tipo) { return !finDeArchivo() && actual().tipo.equals(tipo); }

    private void avanzar() { pos++; }

    private String esperarIdentificador(String mensaje) {
        if (chequear("IDENTIFICADOR") || chequear("SELF")) {
            String n = actual().lexema;
            avanzar();
            return n;
        }
        error(mensaje);
        return "__error__";
    }

    private void esperar(String tipoToken, String mensaje) {
        if (chequear(tipoToken)) { avanzar(); return; }
        // Atribuir el error a la línea previa si el siguiente token ya está en otra línea
        int lineaReporte = actual().linea;
        if (pos > 0) {
            int lineaPrev = tokens.get(pos - 1).linea;
            if (actual().linea > lineaPrev) lineaReporte = lineaPrev;
        }
        errorEnLinea(lineaReporte, mensaje);
        throw new AbortarSentencia();
    }

    private void error(String mensaje) {
        errorEnLinea(actual().linea, mensaje);
    }

    private void errorEnLinea(int linea, String mensaje) {
        String clave = linea + "|" + mensaje;
        errores.add(new SyntaxError(linea, mensaje));
    }

    private void sincronizarSentencia() {
        while (!finDeArchivo() && !chequear("LLAVE_C")) {
            if (chequear("PUNTO_COMA")) { avanzar(); return; }
            if (chequear("LET") || chequear("RETURN") || chequear("IF") || chequear("WHILE")
                || chequear("FOR") || chequear("LOOP") || chequear("BREAK") || chequear("CONTINUE")) {
                return;
            }
            avanzar();
        }
    }

    private void sincronizarDeclaracion() {
        while (!finDeArchivo() && !chequear("FN") && !chequear("STRUCT")) {
            avanzar();
        }
    }
}
