package Optimizacion;

import Analizador_sintactico.AST;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta las optimizaciones sobre el AST. Primero se pliegan constantes (lo que
 * deja a la vista condiciones fijas y variables sin uso) y después se elimina el
 * código muerto.
 *
 * IMPORTANTE: modifica el AST recibido; pásale una copia recién parseada.
 */
public class Optimizador {

    public static final String PLEGADO = "Constant folding";
    public static final String CODIGO_MUERTO = "Eliminación de código muerto";

    public static ResultadoOptimizacion optimizar(AST.Programa programa, boolean plegado, boolean codigoMuerto) {
        return optimizar(programa, plegado, codigoMuerto, false);
    }

    /** @param conservarResultado no eliminar el 'let' final (el resultado) de las funciones sin retorno */
    public static ResultadoOptimizacion optimizar(AST.Programa programa, boolean plegado, boolean codigoMuerto,
                                                  boolean conservarResultado) {
        List<Cambio> cambios = new ArrayList<>();
        PlegadoConstantes pc = new PlegadoConstantes();
        if (plegado) cambios.addAll(pc.aplicar(programa));
        if (codigoMuerto) {
            EliminadorCodigoMuerto ecm = new EliminadorCodigoMuerto();
            ecm.setVariablesPropagadas(pc.getVariablesPropagadas());
            ecm.setConservarResultado(conservarResultado);
            cambios.addAll(ecm.aplicar(programa));
        }
        return new ResultadoOptimizacion(cambios, new GeneradorCodigoRust().generar(programa));
    }
}
