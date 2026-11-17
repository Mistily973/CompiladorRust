package Analizador_lexico;

public class Token {
    public final String lexema;
    public final String tipo;
    public final int id;
    public final int linea;

    public Token(String lexema, String tipo, int id, int linea) {
        this.lexema = lexema;
        this.tipo = tipo;
        this.id = id;
        this.linea = linea;
    }

    public String getLexema() { return lexema; }
    public String getTipo() { return tipo; }
    public int getId() { return id; }
    public int getLinea() { return linea; }

    @Override
    public String toString() {
        return String.format("Token[Lexema='%s', Tipo='%s', ID=%d, Línea=%d]", lexema, tipo, id, linea);
    }
}
