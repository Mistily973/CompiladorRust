package Ejecucion;

import Analizador_sintactico.AST;

import java.util.Arrays;

/**
 * Ejecuta el programa original y el optimizado muchas veces (con calentamiento)
 * y reporta el tiempo mediano de cada uno, además de las operaciones evaluadas
 * y si ambos producen exactamente la misma salida.
 */
public class ComparadorEjecucion {

    public static class Medicion {
        public Interprete.Resultado original;
        public Interprete.Resultado optimizado;
        public double nanosOriginal;       // mediana
        public double nanosOptimizado;     // mediana
        public int repeticiones;
        public boolean mismaSalida;
        public String error;               // problema que impidió medir (null si todo bien)
    }

    public static Medicion comparar(AST.Programa original, AST.Programa optimizado) {
        final Medicion[] holder = new Medicion[1];
        // Hilo con pila grande para soportar recursión profunda en los programas ejecutados
        Thread t = new Thread(null, () -> holder[0] = medir(original, optimizado), "ejecucion", 256L * 1024 * 1024);
        t.start();
        try { t.join(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
        if (holder[0] == null) {
            Medicion m = new Medicion();
            m.error = "La medición se interrumpió.";
            return m;
        }
        return holder[0];
    }

    private static Medicion medir(AST.Programa po, AST.Programa pp) {
        Medicion m = new Medicion();
        Interprete io = new Interprete(po), ip = new Interprete(pp);
        if (!io.tieneMain()) { m.error = "El programa no tiene función main(): no se puede ejecutar."; return m; }
        if (!ip.tieneMain()) { m.error = "La versión optimizada no tiene main()."; return m; }

        m.original = io.ejecutar();
        m.optimizado = ip.ejecutar();
        m.mismaSalida = m.original.salida.equals(m.optimizado.salida)
            && (m.original.error == null) == (m.optimizado.error == null);
        m.nanosOriginal = m.original.nanos;
        m.nanosOptimizado = m.optimizado.nanos;
        if (!m.original.ok() || !m.optimizado.ok()) return m;   // sin medir si hubo error

        long peor = Math.max(1, Math.max(m.original.nanos, m.optimizado.nanos));
        int reps = (int) Math.max(3, Math.min(300, 300_000_000L / peor));
        int calentamiento = reps >= 50 ? 100 : 0;
        for (int i = 0; i < calentamiento; i++) { io.ejecutar(); ip.ejecutar(); }

        long[] a = new long[reps], b = new long[reps];
        for (int i = 0; i < reps; i++) {   // intercalados para que el ruido afecte por igual
            a[i] = io.ejecutar().nanos;
            b[i] = ip.ejecutar().nanos;
        }
        Arrays.sort(a);
        Arrays.sort(b);
        m.nanosOriginal = a[reps / 2];
        m.nanosOptimizado = b[reps / 2];
        m.repeticiones = reps;
        return m;
    }
}
