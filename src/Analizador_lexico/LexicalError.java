package Analizador_lexico;

public class LexicalError {
    public final String lexema;
    public final String descripcion;
    public final int codigoError;
    public final int linea;

    public LexicalError(String lexema, String descripcion, int codigoError, int linea) {
        this.lexema = lexema;
        this.descripcion = descripcion;
        this.codigoError = codigoError;
        this.linea = linea;
    }
}
