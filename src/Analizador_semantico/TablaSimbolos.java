package Analizador_semantico;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Tabla de símbolos implementada como una pila de ámbitos. A diferencia de
 * un Stack<Map> plano, cada ámbito (Ambito) sabe si es una "barrera de
 * función": al buscar un nombre, si cruzamos una barrera sin encontrarlo,
 * saltamos directo al ámbito Global en vez de seguir mirando ámbitos de
 * funciones encerradoras (Rust no tiene closures de variables libres como
 * en este subconjunto del lenguaje). También se mantiene un historial
 * completo de símbolos declarados (incluso de ámbitos ya cerrados) para
 * poder mostrar la Tabla de Símbolos completa al final del análisis.
 */
public class TablaSimbolos {

    private static class Ambito {
        String nombre;
        boolean barrera;
        Map<String, Simbolo> simbolos = new HashMap<>();
        Ambito(String nombre, boolean barrera) { this.nombre = nombre; this.barrera = barrera; }
    }

    private final Stack<Ambito> ambitos = new Stack<>();
    private final List<Simbolo> historial = new ArrayList<>();

    public TablaSimbolos() {
        ambitos.push(new Ambito("Global", false));
    }

    public void entrarAmbito() {
        ambitos.push(new Ambito(ambitoActualNombre() + "::bloque", false));
    }

    public void entrarAmbitoFuncion(String nombre) {
        ambitos.push(new Ambito(nombre, true));
    }

    public void salirAmbito() {
        if (ambitos.size() > 1) ambitos.pop();
    }

    public String ambitoActualNombre() {
        return ambitos.peek().nombre;
    }

    public boolean insertar(Simbolo simbolo) {
        simbolo.ambito = ambitoActualNombre();
        ambitos.peek().simbolos.put(simbolo.nombre, simbolo);
        historial.add(simbolo);
        return true;
    }

    public Simbolo buscarEnAmbitoActual(String nombre) {
        return ambitos.peek().simbolos.get(nombre);
    }

    public Simbolo buscar(String nombre) {
        boolean cruzoBarrera = false;
        for (int i = ambitos.size() - 1; i >= 0; i--) {
            Ambito a = ambitos.get(i);
            if (cruzoBarrera && !a.nombre.equals("Global")) continue;
            if (a.simbolos.containsKey(nombre)) return a.simbolos.get(nombre);
            if (a.barrera) cruzoBarrera = true;
        }
        return null;
    }

    public List<Simbolo> getHistorial() {
        return historial;
    }
}
