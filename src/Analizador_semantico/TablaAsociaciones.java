package Analizador_semantico;

/**
 * Tabla de compatibilidad de operadores: dada una operación binaria y los
 * tipos de sus operandos, determina el tipo resultado (o null si la
 * combinación no es válida en Rust). Cubre los 12 anchos enteros, 2 anchos
 * flotantes, bool, char y String/str.
 */
public class TablaAsociaciones {

    public static Tipo resultado(String operador, Tipo izq, Tipo der) {
        if (izq == null || der == null) return Tipo.DESCONOCIDO;
        if (izq.esDesconocido() || der.esDesconocido()) return Tipo.DESCONOCIDO;

        boolean esComparacion = operador.equals("==") || operador.equals("!=") || operador.equals("<")
            || operador.equals(">") || operador.equals("<=") || operador.equals(">=");
        boolean esLogico = operador.equals("&&") || operador.equals("||");
        boolean esAritmetico = operador.equals("+") || operador.equals("-") || operador.equals("*")
            || operador.equals("/") || operador.equals("%");

        if (esLogico) {
            if (izq.equals(Tipo.BOOL) || (izq.esNominal() && izq.nombre.equals("bool"))) {
                if (der.esNominal() && der.nombre.equals("bool")) return Tipo.BOOL;
            }
            return null;
        }

        if (esAritmetico) {
            if (izq.esNumerico() && der.esNumerico()) {
                if (izq.esEntero() && der.esEntero()) {
                    if (izq.clase == Tipo.Clase.ENTERO_LIT) return der;
                    if (der.clase == Tipo.Clase.ENTERO_LIT) return izq;
                    if (izq.nombre.equals(der.nombre)) return izq;
                    return null;
                }
                if (izq.esFlotante() && der.esFlotante()) {
                    if (izq.clase == Tipo.Clase.FLOTANTE_LIT) return der;
                    if (der.clase == Tipo.Clase.FLOTANTE_LIT) return izq;
                    if (izq.nombre.equals(der.nombre)) return izq;
                    return null;
                }
                return null; // no hay conversión implícita entre entero y flotante
            }
            // Concatenación de String/str con '+'
            if (operador.equals("+") && izq.esString()) {
                if (der.esStr() || der.esString() || (der.esReferencia() && der.interno.esStr())) {
                    return Tipo.STRING;
                }
            }
            return null;
        }

        if (esComparacion) {
            if (izq.esNumerico() && der.esNumerico()) {
                if (izq.esEntero() && der.esEntero()) return Tipo.BOOL;
                if (izq.esFlotante() && der.esFlotante()) return Tipo.BOOL;
                return null;
            }
            if (izq.esNominal() && der.esNominal() && izq.nombre.equals(der.nombre)) return Tipo.BOOL;
            if ((izq.nombre.equals("bool") && der.nombre.equals("bool"))) return Tipo.BOOL;
            if ((izq.nombre.equals("char") && der.nombre.equals("char"))) return Tipo.BOOL;
            return null;
        }

        return null;
    }
}
