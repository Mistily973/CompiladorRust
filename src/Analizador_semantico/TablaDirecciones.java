package Analizador_semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Simula la asignación de memoria del programa: un segmento de CÓDIGO para
 * las funciones (direcciones consecutivas de tamaño fijo) y un segmento de
 * PILA con un marco (frame) por función, donde cada variable local recibe
 * un desplazamiento alineado según el tamaño de su tipo (igual que lo
 * haría un compilador real al generar el layout de la pila).
 */
public class TablaDirecciones {

    public static class Entrada {
        public int direccion;
        public int desplazamiento;
        public String nombre;
        public String tipo;
        public int tamano;
        public String segmento; // "CÓDIGO" | "PILA" | "ESTRUCTURA"
        public String ambito;
        public int linea;

        public String direccionHex() { return String.format("0x%08X", direccion); }
        public String desplazamientoHex() { return String.format("0x%04X", desplazamiento); }
    }

    private static final int BASE_CODIGO = 0x00400000;
    private static final int PASO_CODIGO = 0x40;
    private static final int BASE_PILA = 0x00010000;
    private static final int TAMANO_MARCO = 0x00010000;

    private int siguienteCodigo = BASE_CODIGO;
    private int indiceMarco = 0;
    private int desplazamientoActual = 0;
    private String ambitoMarcoActual = "";

    public final List<Entrada> entradas = new ArrayList<>();

    public Entrada asignarFuncion(String nombre, int linea) {
        Entrada e = new Entrada();
        e.direccion = siguienteCodigo;
        e.desplazamiento = 0;
        e.nombre = nombre;
        e.tipo = "función";
        e.tamano = PASO_CODIGO;
        e.segmento = "CÓDIGO";
        e.ambito = "Global";
        e.linea = linea;
        siguienteCodigo += PASO_CODIGO;
        entradas.add(e);
        return e;
    }

    public void abrirMarco(String nombreFuncion) {
        indiceMarco++;
        desplazamientoActual = 0;
        ambitoMarcoActual = nombreFuncion;
    }

    public void cerrarMarco() {
        desplazamientoActual = 0;
    }

    public int tamanoMarcoActual() { return desplazamientoActual; }

    public Entrada asignarLocal(String nombre, Tipo tipo, String ambito, int linea) {
        int tam = tamanoDe(tipo);
        int align = alineacionDe(tipo);
        if (align > 0 && desplazamientoActual % align != 0) {
            desplazamientoActual += align - (desplazamientoActual % align);
        }
        int base = BASE_PILA * indiceMarco;
        Entrada e = new Entrada();
        e.direccion = base + desplazamientoActual;
        e.desplazamiento = desplazamientoActual;
        e.nombre = nombre;
        e.tipo = tipo.toString();
        e.tamano = tam;
        e.segmento = "PILA";
        e.ambito = ambito;
        e.linea = linea;
        desplazamientoActual += Math.max(tam, 1);
        entradas.add(e);
        return e;
    }

    public List<Entrada> registrarEstructura(String nombreStruct, List<String> camposNombres, List<Tipo> camposTipos, int linea) {
        List<Entrada> lista = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < camposNombres.size(); i++) {
            Tipo t = camposTipos.get(i);
            int tam = tamanoDe(t);
            int align = alineacionDe(t);
            if (align > 0 && offset % align != 0) offset += align - (offset % align);
            Entrada e = new Entrada();
            e.direccion = offset;
            e.desplazamiento = offset;
            e.nombre = nombreStruct + "." + camposNombres.get(i);
            e.tipo = t.toString();
            e.tamano = tam;
            e.segmento = "ESTRUCTURA";
            e.ambito = nombreStruct;
            e.linea = linea;
            lista.add(e);
            entradas.add(e);
            offset += Math.max(tam, 1);
        }
        return lista;
    }

    public void actualizarTipo(int direccion, Tipo tipo) {
        for (Entrada e : entradas) {
            if (e.direccion == direccion) {
                e.tipo = tipo.toString();
                e.tamano = tamanoDe(tipo);
            }
        }
    }

    public int tamanoDe(Tipo t) {
        if (t == null) return 0;
        Tipo real = t.concreto();
        if (real.clase == Tipo.Clase.REFERENCIA) return 8;
        if (real.clase == Tipo.Clase.TUPLA) {
            int s = 0;
            for (Tipo e : real.elementos) s += tamanoDe(e);
            return s;
        }
        if (real.clase == Tipo.Clase.SLICE) return 16;
        if (real.clase == Tipo.Clase.ARREGLO) return tamanoDe(real.interno) * 4;
        if (!real.esNominal()) return 8;
        switch (real.nombre) {
            case "i8": case "u8": case "bool": return 1;
            case "i16": case "u16": return 2;
            case "i32": case "u32": case "f32": case "char": return 4;
            case "i64": case "u64": case "f64": case "isize": case "usize": return 8;
            case "i128": case "u128": return 16;
            case "str": return 16;
            case "String": case "Vec": return 24;
            case "()": return 0;
            default: return 8; // Box/Rc/structs de usuario sin detalle
        }
    }

    public int alineacionDe(Tipo t) {
        int tam = tamanoDe(t);
        if (tam >= 8) return 8;
        if (tam >= 4) return 4;
        if (tam >= 2) return 2;
        return 1;
    }
}
