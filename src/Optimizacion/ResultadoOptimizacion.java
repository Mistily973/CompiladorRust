package Optimizacion;

import java.util.List;

/** Código Rust resultante más la lista de cambios aplicados, en orden de aplicación. */
public class ResultadoOptimizacion {
    public final List<Cambio> cambios;
    public final String codigo;

    public ResultadoOptimizacion(List<Cambio> cambios, String codigo) {
        this.cambios = cambios;
        this.codigo = codigo;
    }

    public int contar(String optimizacion) {
        int n = 0;
        for (Cambio c : cambios) if (c.optimizacion.equals(optimizacion)) n++;
        return n;
    }
}
