package Codigo_intermedio;

/**
 * Un cuádruplo de código intermedio clásico: (resultado, operando1, operador,
 * operando2). Representa una única operación de tres direcciones, ej.:
 * resultado=t1, operando1=a, operador=+, operando2=b, que se lee "t1 = a + b".
 * Cuando operando2 (arg2) es null la operación es unaria ("neg") o una
 * simple copia/asignación final (operador "=", ej. resultado=x, arg1=t3,
 * que se lee "x = t3").
 */
public class Cuadruplo {
    public int numero;       // posición dentro de la tabla (1, 2, 3, ...)
    public String operador;  // "+","-","*","/","%","neg",":="
    public String arg1;
    public String arg2;      // puede ser null
    public String resultado; // nombre del temporal (tN) o variable destino

    public Cuadruplo(int numero, String operador, String arg1, String arg2, String resultado) {
        this.numero = numero;
        this.operador = operador;
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.resultado = resultado;
    }

    /** Representación legible: "t1 = a + b" */
    public String comoAsignacion() {
        if (operador.equals(":=") || operador.equals("=")) {
            return resultado + " = " + arg1;
        }
        if (arg2 == null) {
            return resultado + " = " + operador + " " + arg1;
        }
        return resultado + " = " + arg1 + " " + operador + " " + arg2;
    }

    @Override
    public String toString() {
        return String.format("(%s, %s, %s, %s)", operador, arg1, arg2 == null ? "_" : arg2, resultado);
    }
}
