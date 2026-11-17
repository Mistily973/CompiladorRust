package Analizador_lexico;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuración tabular del analizador léxico: palabras reservadas,
 * operadores de uno y dos caracteres, y conjuntos de clasificación de
 * caracteres. Centralizar estas tablas aquí evita cadenas de if/else
 * repetidas en RustLexer y facilita agregar nuevas palabras clave o
 * símbolos del lenguaje Rust sin tocar la lógica de barrido.
 */
public class RustLexerConfig {

    // --- PALABRAS RESERVADAS: lexema -> (tipoToken, id) ---
    public static final Map<String, int[]> PLACEHOLDER = new HashMap<>(); // no usado, solo referencia

    public static final Map<String, TokenInfo> PALABRAS_RESERVADAS = new HashMap<>();

    public static class TokenInfo {
        public final String tipo;
        public final int id;
        public TokenInfo(String tipo, int id) { this.tipo = tipo; this.id = id; }
    }

    static {
        PALABRAS_RESERVADAS.put("fn",       new TokenInfo("FN", 1));
        PALABRAS_RESERVADAS.put("let",      new TokenInfo("LET", 2));
        PALABRAS_RESERVADAS.put("mut",      new TokenInfo("MUT", 3));
        PALABRAS_RESERVADAS.put("if",       new TokenInfo("IF", 5));
        PALABRAS_RESERVADAS.put("else",     new TokenInfo("ELSE", 6));
        PALABRAS_RESERVADAS.put("while",    new TokenInfo("WHILE", 7));
        PALABRAS_RESERVADAS.put("for",      new TokenInfo("FOR", 8));
        PALABRAS_RESERVADAS.put("loop",     new TokenInfo("LOOP", 9));
        PALABRAS_RESERVADAS.put("in",       new TokenInfo("IN", 10));
        PALABRAS_RESERVADAS.put("return",   new TokenInfo("RETURN", 11));
        PALABRAS_RESERVADAS.put("struct",   new TokenInfo("STRUCT", 12));
        PALABRAS_RESERVADAS.put("impl",     new TokenInfo("IMPL", 13));
        PALABRAS_RESERVADAS.put("match",    new TokenInfo("MATCH", 14));
        PALABRAS_RESERVADAS.put("break",    new TokenInfo("BREAK", 15));
        PALABRAS_RESERVADAS.put("true",     new TokenInfo("BOOLEANO", 16));
        PALABRAS_RESERVADAS.put("false",    new TokenInfo("BOOLEANO", 17));
        PALABRAS_RESERVADAS.put("pub",      new TokenInfo("PUB", 18));
        PALABRAS_RESERVADAS.put("continue", new TokenInfo("CONTINUE", 19));
        PALABRAS_RESERVADAS.put("self",     new TokenInfo("SELF", 20));
        PALABRAS_RESERVADAS.put("as",       new TokenInfo("AS", 46));

        // Tipos primitivos
        for (String t : new String[]{"i8","i16","i32","i64","i128","isize",
                                      "u8","u16","u32","u64","u128","usize"}) {
            PALABRAS_RESERVADAS.put(t, new TokenInfo("TIPO_INT", 4));
        }
        PALABRAS_RESERVADAS.put("f32", new TokenInfo("TIPO_FLOAT", 5));
        PALABRAS_RESERVADAS.put("f64", new TokenInfo("TIPO_FLOAT", 5));
        PALABRAS_RESERVADAS.put("bool", new TokenInfo("TIPO_BOOL", 21));
        PALABRAS_RESERVADAS.put("char", new TokenInfo("TIPO_CHAR", 22));
        PALABRAS_RESERVADAS.put("String", new TokenInfo("TIPO_STR", 29));
        PALABRAS_RESERVADAS.put("str", new TokenInfo("TIPO_STR", 29));
    }

    // --- OPERADORES DE DOS CARACTERES ---
    public static final Map<String, TokenInfo> SEPARADORES_AUTOMATAS_2 = new HashMap<>();
    static {
        SEPARADORES_AUTOMATAS_2.put("==", new TokenInfo("OP_COMP", 30));
        SEPARADORES_AUTOMATAS_2.put("!=", new TokenInfo("OP_COMP", 31));
        SEPARADORES_AUTOMATAS_2.put("<=", new TokenInfo("OP_COMP", 32));
        SEPARADORES_AUTOMATAS_2.put(">=", new TokenInfo("OP_COMP", 33));
        SEPARADORES_AUTOMATAS_2.put("&&", new TokenInfo("OP_LOG", 34));
        SEPARADORES_AUTOMATAS_2.put("||", new TokenInfo("OP_LOG", 35));
        SEPARADORES_AUTOMATAS_2.put("->", new TokenInfo("FLECHA", 36));
        SEPARADORES_AUTOMATAS_2.put("::", new TokenInfo("DOBLE_DOSPUNTOS", 37));
        SEPARADORES_AUTOMATAS_2.put("..", new TokenInfo("RANGO", 38));
        SEPARADORES_AUTOMATAS_2.put("+=", new TokenInfo("OP_ASIG_COMP", 39));
        SEPARADORES_AUTOMATAS_2.put("-=", new TokenInfo("OP_ASIG_COMP", 40));
        SEPARADORES_AUTOMATAS_2.put("*=", new TokenInfo("OP_ASIG_COMP", 41));
        SEPARADORES_AUTOMATAS_2.put("/=", new TokenInfo("OP_ASIG_COMP", 42));
        SEPARADORES_AUTOMATAS_2.put("=>", new TokenInfo("FLECHA_GRUESA", 43));
        SEPARADORES_AUTOMATAS_2.put("%=", new TokenInfo("OP_ASIG_COMP", 44));
    }

    // --- OPERADORES DE UN CARÁCTER ---
    public static final Map<Character, TokenInfo> SEPARADORES_AUTOMATAS_1 = new HashMap<>();
    static {
        SEPARADORES_AUTOMATAS_1.put('+', new TokenInfo("OP_SUMA", 24));
        SEPARADORES_AUTOMATAS_1.put('-', new TokenInfo("OP_RESTA", 25));
        SEPARADORES_AUTOMATAS_1.put('*', new TokenInfo("OP_MULT", 28));
        SEPARADORES_AUTOMATAS_1.put('/', new TokenInfo("OP_DIV", 27));
        SEPARADORES_AUTOMATAS_1.put('%', new TokenInfo("OP_MOD", 45));
        SEPARADORES_AUTOMATAS_1.put('=', new TokenInfo("OP_ASIG", 26));
        SEPARADORES_AUTOMATAS_1.put('<', new TokenInfo("OP_COMP", 32));
        SEPARADORES_AUTOMATAS_1.put('>', new TokenInfo("OP_COMP", 33));
        SEPARADORES_AUTOMATAS_1.put('!', new TokenInfo("OP_NEG", 23));
        SEPARADORES_AUTOMATAS_1.put('&', new TokenInfo("OP_REF", 47));
    }

    // Separadores auxiliares (no forman parte de operadores de 1 o 2 chars,
    // ya se manejan directo en el lexer): paréntesis, llaves, etc.
    public static final Set<Character> SEPARADORES_AUXILIARES = new HashSet<>();
    static {
        for (char c : new char[]{'(', ')', '{', '}', '[', ']', ';', ',', ':', '.'}) {
            SEPARADORES_AUXILIARES.add(c);
        }
        // Nota: '*' fue removido de aquí porque ahora se trata como operador (OP_MULT)
    }

    // Caracteres que pueden iniciar un autómata de operador compuesto
    public static final String INICIO_AUTOMATA = "=!<>&|-:.+-*/%";
}
