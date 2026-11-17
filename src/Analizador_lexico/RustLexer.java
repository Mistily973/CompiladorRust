package Analizador_lexico;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Analizador léxico de Rust. Recorre el texto fuente carácter por carácter
 * y produce una lista de Tokens más una lista de LexicalError. Las
 * expresiones regulares para enteros/flotantes se precompilan una sola vez
 * (REGEX_ENTERO / REGEX_FLOTANTE) porque antes se compilaban en cada llamada,
 * lo cual era costoso para archivos grandes.
 */
public class RustLexer {

    private static final Pattern REGEX_ENTERO = Pattern.compile("^[0-9][0-9_]*$");
    private static final Pattern REGEX_FLOTANTE = Pattern.compile("^[0-9][0-9_]*\\.[0-9][0-9_]*$");

    private final String fuente;
    private int pos = 0;
    private int linea = 1;
    private final List<Token> tokens = new ArrayList<>();
    private final List<LexicalError> errores = new ArrayList<>();
    private int idSecuencia = 1000;

    public RustLexer(String fuente) {
        this.fuente = fuente;
    }

    public List<Token> getTokens() { return tokens; }
    public List<LexicalError> getErrores() { return errores; }

    public void analizar() {
        while (pos < fuente.length()) {
            char c = fuente.charAt(pos);

            if (c == '\n') { linea++; pos++; continue; }
            if (Character.isWhitespace(c)) { pos++; continue; }

            // Comentario de línea
            if (c == '/' && siguiente() == '/') {
                while (pos < fuente.length() && fuente.charAt(pos) != '\n') pos++;
                continue;
            }
            // Comentario de bloque
            if (c == '/' && siguiente() == '*') {
                int lineaInicio = linea;
                pos += 2;
                boolean cerrado = false;
                StringBuilder sb = new StringBuilder("/*");
                while (pos < fuente.length()) {
                    if (fuente.charAt(pos) == '*' && siguiente() == '/') {
                        sb.append("*/");
                        pos += 2;
                        cerrado = true;
                        break;
                    }
                    if (fuente.charAt(pos) == '\n') linea++;
                    sb.append(fuente.charAt(pos));
                    pos++;
                }
                if (!cerrado) {
                    errores.add(new LexicalError(resumir(sb.toString()), "Comentario de bloque no cerrado", 105, lineaInicio));
                } else {
                    tokens.add(new Token(resumir(sb.toString()), "COMENTARIO_BLOQUE", 998, lineaInicio));
                }
                continue;
            }

            if (c == '"') { leerString(); continue; }
            if (c == '\'') { leerChar(); continue; }
            if (Character.isDigit(c)) { leerNumero(); continue; }
            if (Character.isLetter(c) || c == '_') { leerIdentificador(); continue; }

            // Operadores de 2 caracteres
            if (pos + 1 < fuente.length()) {
                String dos = fuente.substring(pos, pos + 2);
                RustLexerConfig.TokenInfo info2 = RustLexerConfig.SEPARADORES_AUTOMATAS_2.get(dos);
                if (info2 != null) {
                    tokens.add(new Token(dos, info2.tipo, info2.id, linea));
                    pos += 2;
                    continue;
                }
            }

            // Operadores de 1 carácter
            RustLexerConfig.TokenInfo info1 = RustLexerConfig.SEPARADORES_AUTOMATAS_1.get(c);
            if (info1 != null) {
                tokens.add(new Token(String.valueOf(c), info1.tipo, info1.id, linea));
                pos++;
                continue;
            }

            // Separadores auxiliares
            if (RustLexerConfig.SEPARADORES_AUXILIARES.contains(c)) {
                tokens.add(new Token(String.valueOf(c), nombreSeparador(c), 900 + c, linea));
                pos++;
                continue;
            }

            // Símbolo desconocido
            errores.add(new LexicalError(String.valueOf(c), "Símbolo desconocido", 100, linea));
            pos++;
        }
    }

    private String nombreSeparador(char c) {
        switch (c) {
            case '(': return "PAR_A";
            case ')': return "PAR_C";
            case '{': return "LLAVE_A";
            case '}': return "LLAVE_C";
            case '[': return "CORCHETE_A";
            case ']': return "CORCHETE_C";
            case ';': return "PUNTO_COMA";
            case ',': return "COMA";
            case ':': return "DOS_PUNTOS";
            case '.': return "PUNTO";
            default: return "SEPARADOR";
        }
    }

    private char siguiente() {
        return (pos + 1 < fuente.length()) ? fuente.charAt(pos + 1) : '\0';
    }

    private String resumir(String lexema) {
        if (lexema.length() > 40) {
            return lexema.substring(0, 37) + "...";
        }
        return lexema;
    }

    private void leerIdentificador() {
        int inicio = pos;
        int lineaInicio = linea;
        while (pos < fuente.length() && (Character.isLetterOrDigit(fuente.charAt(pos)) || fuente.charAt(pos) == '_')) {
            pos++;
        }
        String lex = fuente.substring(inicio, pos);
        RustLexerConfig.TokenInfo info = RustLexerConfig.PALABRAS_RESERVADAS.get(lex);
        if (info != null) {
            tokens.add(new Token(lex, info.tipo, info.id, lineaInicio));
        } else {
            tokens.add(new Token(lex, "IDENTIFICADOR", 200, lineaInicio));
        }
    }

    private void leerNumero() {
        int inicio = pos;
        int lineaInicio = linea;
        while (pos < fuente.length() && (Character.isDigit(fuente.charAt(pos)) || fuente.charAt(pos) == '_')) pos++;
        boolean esFlotante = false;
        if (pos < fuente.length() && fuente.charAt(pos) == '.' && pos + 1 < fuente.length() && Character.isDigit(fuente.charAt(pos + 1))) {
            esFlotante = true;
            pos++;
            while (pos < fuente.length() && (Character.isDigit(fuente.charAt(pos)) || fuente.charAt(pos) == '_')) pos++;
        }
        // sufijo de tipo (i32, u8, f64, etc.)
        int finNumero = pos;
        while (pos < fuente.length() && (Character.isLetterOrDigit(fuente.charAt(pos)))) pos++;
        String lex = fuente.substring(inicio, pos);
        String numeroPuro = fuente.substring(inicio, finNumero);

        if (esFlotante) {
            if (REGEX_FLOTANTE.matcher(numeroPuro).matches()) {
                tokens.add(new Token(lex, "FLOTANTE", 203, lineaInicio));
            } else {
                errores.add(new LexicalError(lex, "Literal flotante mal formado", 103, lineaInicio));
            }
        } else {
            if (REGEX_ENTERO.matcher(numeroPuro).matches()) {
                tokens.add(new Token(lex, "ENTERO", 202, lineaInicio));
            } else {
                errores.add(new LexicalError(lex, "Literal entero mal formado", 103, lineaInicio));
            }
        }
    }

    private boolean esCaracterStringValido(char c) {
        return Character.isLetterOrDigit(c) || Character.isLetter(c) || " .,;:!?¿¡-_'\"()[]{}/*+=<>%$&|^~#@".indexOf(c) >= 0;
    }

    private void leerString() {
        int lineaInicio = linea;
        StringBuilder sb = new StringBuilder("\"");
        pos++; // saltar comilla inicial
        boolean cerrado = false;
        boolean valido = true;
        while (pos < fuente.length()) {
            char c = fuente.charAt(pos);
            if (c == '"') {
                sb.append('"');
                pos++;
                cerrado = true;
                break;
            }
            if (c == '\n') break; // string sin cerrar en la misma línea
            if (c == '\\') {
                char sig = siguiente();
                switch (sig) {
                    case 'n': case 't': case 'r': case '\\': case '"': case '\'': case '0':
                        sb.append(c).append(sig);
                        pos += 2;
                        continue;
                    default:
                        valido = false;
                        sb.append(c);
                        if (sig != '\0') { sb.append(sig); pos += 2; } else { pos++; }
                        continue;
                }
            }
            if (!esCaracterStringValido(c)) {
                valido = false;
            }
            sb.append(c);
            pos++;
        }

        String lex = sb.toString();
        if (!cerrado) {
            errores.add(new LexicalError(resumir(lex), "Cadena de texto no cerrada", 102, lineaInicio));
        } else if (!valido) {
            errores.add(new LexicalError(lex, "Cadena con secuencia de escape inválida", 106, lineaInicio));
        } else {
            tokens.add(new Token(lex, "STRING", 204, lineaInicio));
        }
    }

    private void leerChar() {
        int lineaInicio = linea;
        StringBuilder sb = new StringBuilder("'");
        pos++; // saltar comilla inicial
        int contenido = 0;
        boolean valido = true;
        boolean cerrado = false;

        while (pos < fuente.length()) {
            char c = fuente.charAt(pos);
            if (c == '\'') {
                sb.append('\'');
                pos++;
                cerrado = true;
                break;
            }
            if (c == '\n') break;
            if (c == '\\') {
                char sig = siguiente();
                sb.append(c);
                if (sig != '\0') { sb.append(sig); pos += 2; } else { pos++; }
                contenido++;
                continue;
            }
            sb.append(c);
            pos++;
            contenido++;
        }

        String lex = sb.toString();
        if (!cerrado) {
            errores.add(new LexicalError(resumir(lex), "Carácter no cerrado", 102, lineaInicio));
        } else if (contenido != 1) {
            errores.add(new LexicalError(lex, "Literal de carácter inválido (debe contener exactamente un carácter)", 104, lineaInicio));
        } else {
            tokens.add(new Token(lex, "CARACTER", 205, lineaInicio));
        }
    }
}
