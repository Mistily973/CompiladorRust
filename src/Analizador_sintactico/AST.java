package Analizador_sintactico;

import java.util.List;

/**
 * Jerarquía del Árbol de Sintaxis Abstracta (AST). Son clases de datos
 * puras (sin lógica), cada una con el campo `linea` para poder reportar
 * errores sintácticos y semánticos con precisión. El analizador semántico
 * y el nuevo generador de código intermedio recorren estos nodos.
 */
public class AST {

    public static abstract class Nodo {
        public int linea;
    }

    public static class Programa extends Nodo {
        public List<DeclFuncion> funciones;
        public List<DeclStruct> estructuras;
    }

    public static class Parametro extends Nodo {
        public String nombre;
        public String tipoTexto;
        public boolean esMut;
        public boolean esReferencia;
    }

    public static class DeclFuncion extends Nodo {
        public String nombre;
        public List<Parametro> parametros;
        public String tipoRetornoTexto; // null => unit
        public Bloque cuerpo;
    }

    public static class CampoDecl extends Nodo {
        public String nombre;
        public String tipoTexto;
    }

    public static class DeclStruct extends Nodo {
        public String nombre;
        public List<CampoDecl> campos;
    }

    public static class Bloque extends Nodo {
        public List<Sentencia> sentencias;
        public Expr exprFinal; // expresión final sin ';' (valor del bloque)
    }

    public static abstract class Sentencia extends Nodo { }

    public static class Patron extends Nodo {
        public String texto;      // representación textual del patrón
        public String tokenTipo;  // tipo de token si es literal ("_" , ENTERO, etc.)
    }

    public static class DeclLet extends Sentencia {
        public String nombre;
        public boolean esMutable;
        public String tipoTexto; // puede ser null (inferido)
        public Expr inicializador; // puede ser null
    }

    public static class SentExpr extends Sentencia {
        public Expr expr;
    }

    public static class SentReturn extends Sentencia {
        public Expr valor; // puede ser null
    }

    public static class SentSalto extends Sentencia {
        public String tipo; // "break" | "continue"
        public Expr valor;  // valor de break, puede ser null
    }

    public static abstract class Expr extends Nodo { }

    public static class Literal extends Expr {
        public String texto;
        public String tokenTipo; // ENTERO, FLOTANTE, STRING, CARACTER, BOOLEANO
    }

    public static class Identificador extends Expr {
        public String nombre;
    }

    public static class Ruta extends Expr {
        public List<String> segmentos; // ej. ["String","from"]
    }

    public static class Unario extends Expr {
        public String operador; // "-", "!", "&", "&mut", "*"
        public Expr operando;
    }

    public static class Binario extends Expr {
        public Expr izquierda;
        public Expr derecha;
        public String operador;   // "+","-","*","/","%","==", etc. (texto)
        public String tokenTipo;  // OP_SUMA, OP_RESTA, OP_MULT, OP_DIV, OP_MOD, OP_COMP, OP_LOG
    }

    public static class Cast extends Expr {
        public Expr expr;
        public String tipoTexto;
    }

    public static class Asignacion extends Expr {
        public Expr objetivo;
        public String operador; // "=", "+=", "-=", "*=", "/=", "%="
        public Expr valor;
    }

    public static class Llamada extends Expr {
        public Expr callee; // normalmente Identificador o Ruta
        public List<Expr> argumentos;
    }

    public static class LlamadaMacro extends Expr {
        public String nombre; // println, format, vec, etc.
        public List<Expr> argumentos;
    }

    public static class LlamadaMetodo extends Expr {
        public Expr receptor;
        public String nombreMetodo;
        public List<Expr> argumentos;
    }

    public static class AccesoCampo extends Expr {
        public Expr objeto;
        public String campo;
    }

    public static class AccesoIndice extends Expr {
        public Expr objeto;
        public Expr indice;
    }

    public static class Tupla extends Expr {
        public List<Expr> elementos;
    }

    public static class ArregloLiteral extends Expr {
        public List<Expr> elementos;
    }

    public static class CampoInit extends Nodo {
        public String nombre;
        public Expr valor;
    }

    public static class InstanciaStruct extends Expr {
        public String nombreStruct;
        public List<CampoInit> campos;
    }

    public static class Rango extends Expr {
        public Expr inicio; // puede ser null
        public Expr fin;    // puede ser null
        public boolean inclusivo;
    }

    public static class ExprIf extends Expr {
        public Expr condicion;
        public Bloque entonces;
        public Expr sino; // Bloque o ExprIf, o null
    }

    public static class BrazoMatch extends Nodo {
        public Patron patron;
        public Expr cuerpo;
    }

    public static class ExprMatch extends Expr {
        public Expr sujeto;
        public List<BrazoMatch> brazos;
    }

    public static class ExprBloque extends Expr {
        public Bloque bloque;
    }

    public static class ExprLoop extends Expr {
        public Bloque cuerpo;
    }

    public static class ExprWhile extends Expr {
        public Expr condicion;
        public Bloque cuerpo;
    }

    public static class ExprFor extends Expr {
        public String variable;
        public Expr iterable;
        public Bloque cuerpo;
    }
}
