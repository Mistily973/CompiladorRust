package Codigo_intermedio;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupa todo lo generado para UNA expresión aritmética de nivel superior
 * (el lado derecho de un 'let', de una asignación, o una expresión suelta):
 * su notación infija original, su conversión a prefijo y a posfijo, y la
 * tabla de cuádruplos (paso a paso, con temporales) que la implementa.
 *
 * Esto cumple el requisito "por cada operación intermedia (aritmética) se
 * hace una tabla": cada instancia de esta clase es exactamente esa tabla.
 */
public class BloqueExpresion {
    public int linea;
    public String contexto;     // ej. "let resultado = ..." o "asignación a 'total'"
    public String infijo;
    public String prefijo;
    public String posfijo;
    public List<Cuadruplo> cuadruplos = new ArrayList<>();
    public String variableDestino; // puede ser null si es una expresión suelta

    /**
     * Línea final del estilo "a := 6": el valor numérico resultante de
     * evaluar la expresión usando los valores que las variables ya tenían
     * asignados en el código hasta este punto. Es null cuando no se puede
     * determinar en tiempo de compilación (depende de un parámetro, de una
     * llamada a función, de entrada del usuario, etc.).
     */
    public String resultadoEvaluado;
}
