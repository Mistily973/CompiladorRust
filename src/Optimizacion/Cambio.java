package Optimizacion;

/** Un cambio aplicado por alguna optimización (para mostrarlo en la interfaz). */
public class Cambio {
    public final int linea;
    public final String optimizacion;   // "Constant folding" o "Eliminación de código muerto"
    public final String tipo;           // p. ej. "Variable sin uso"
    public final String detalle;

    public Cambio(int linea, String optimizacion, String tipo, String detalle) {
        this.linea = linea;
        this.optimizacion = optimizacion;
        this.tipo = tipo;
        this.detalle = detalle;
    }
}
