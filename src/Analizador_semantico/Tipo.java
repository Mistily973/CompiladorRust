package Analizador_semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Representación estructurada de un tipo de Rust. Se usa en lugar de un
 * simple String porque necesitamos distinguir tipos "literales" sin
 * asignar todavía (ENTERO_LIT, FLOTANTE_LIT) de tipos concretos, y porque
 * tipos compuestos (tuplas, arreglos, referencias) necesitan sub-tipos.
 */
public class Tipo {

    public enum Clase { NOMINAL, REFERENCIA, TUPLA, ARREGLO, SLICE, ENTERO_LIT, FLOTANTE_LIT, DESCONOCIDO, NUNCA }

    public Clase clase;
    public String nombre;           // para NOMINAL: "i32", "String", "MiStruct", etc.
    public Tipo interno;            // para REFERENCIA/ARREGLO/SLICE
    public boolean mutable;         // para REFERENCIA
    public List<Tipo> elementos;    // para TUPLA

    public static final Tipo DESCONOCIDO = nuevo(Clase.DESCONOCIDO, "desconocido");
    public static final Tipo NUNCA = nuevo(Clase.NUNCA, "!");
    public static final Tipo ENTERO_LIT = nuevo(Clase.ENTERO_LIT, "{entero}");
    public static final Tipo FLOTANTE_LIT = nuevo(Clase.FLOTANTE_LIT, "{flotante}");
    public static final Tipo UNIT = nuevo(Clase.NOMINAL, "()");
    public static final Tipo BOOL = nuevo(Clase.NOMINAL, "bool");
    public static final Tipo CHAR = nuevo(Clase.NOMINAL, "char");
    public static final Tipo STRING = nuevo(Clase.NOMINAL, "String");
    public static final Tipo STR = nuevo(Clase.NOMINAL, "str");
    public static final Tipo STR_REF = referencia(STR, false);
    public static final Tipo USIZE = nuevo(Clase.NOMINAL, "usize");
    public static final Tipo I32 = nuevo(Clase.NOMINAL, "i32");
    public static final Tipo F64 = nuevo(Clase.NOMINAL, "f64");

    private static Tipo nuevo(Clase c, String n) {
        Tipo t = new Tipo();
        t.clase = c;
        t.nombre = n;
        return t;
    }

    public static Tipo nominal(String nombre) { return nuevo(Clase.NOMINAL, nombre); }

    public static Tipo referencia(Tipo interno, boolean mutable) {
        Tipo t = new Tipo();
        t.clase = Clase.REFERENCIA;
        t.interno = interno;
        t.mutable = mutable;
        t.nombre = (mutable ? "&mut " : "&") + interno.nombre;
        return t;
    }

    public static Tipo tupla(List<Tipo> elementos) {
        Tipo t = new Tipo();
        t.clase = Clase.TUPLA;
        t.elementos = elementos;
        t.nombre = "(" + elementos.size() + "-tupla)";
        return t;
    }

    public static Tipo arreglo(Tipo interno) {
        Tipo t = new Tipo();
        t.clase = Clase.ARREGLO;
        t.interno = interno;
        t.nombre = "[" + interno.nombre + "]";
        return t;
    }

    public static Tipo slice(Tipo interno) {
        Tipo t = new Tipo();
        t.clase = Clase.SLICE;
        t.interno = interno;
        t.nombre = "[" + interno.nombre + "]";
        return t;
    }

    public boolean esDesconocido() { return clase == Clase.DESCONOCIDO; }
    public boolean esNunca() { return clase == Clase.NUNCA; }
    public boolean esUnit() { return clase == Clase.NOMINAL && "()".equals(nombre); }
    public boolean esReferencia() { return clase == Clase.REFERENCIA; }
    public boolean esEntero() {
        return clase == Clase.ENTERO_LIT || (clase == Clase.NOMINAL && nombre.matches("i8|i16|i32|i64|i128|isize|u8|u16|u32|u64|u128|usize"));
    }
    public boolean esFlotante() {
        return clase == Clase.FLOTANTE_LIT || (clase == Clase.NOMINAL && (nombre.equals("f32") || nombre.equals("f64")));
    }
    public boolean esNumerico() { return esEntero() || esFlotante(); }
    public boolean esNominal() { return clase == Clase.NOMINAL; }
    public boolean esString() { return clase == Clase.NOMINAL && nombre.equals("String"); }
    public boolean esStr() { return clase == Clase.NOMINAL && nombre.equals("str"); }
    public boolean esNominalUsuario() {
        return clase == Clase.NOMINAL && !esEntero() && !esFlotante() && !nombre.equals("bool")
            && !nombre.equals("char") && !nombre.equals("String") && !nombre.equals("str") && !nombre.equals("()");
    }

    public Tipo sinReferencias() {
        Tipo t = this;
        while (t.clase == Clase.REFERENCIA) t = t.interno;
        return t;
    }

    public Tipo concreto() {
        if (clase == Clase.ENTERO_LIT) return I32;
        if (clase == Clase.FLOTANTE_LIT) return F64;
        return this;
    }

    public static boolean asignable(Tipo esperado, Tipo real) {
        if (esperado == null || real == null) return true;
        if (esperado.esDesconocido() || real.esDesconocido()) return true;
        if (real.esNunca()) return true;

        if (esperado.clase == Clase.REFERENCIA && real.clase == Clase.REFERENCIA) {
            if (esperado.mutable && !real.mutable) return false;
            // &String es asignable a &str (coerción estándar de Rust)
            if (esperado.interno.esStr() && real.interno.esString()) return true;
            return asignable(esperado.interno, real.interno);
        }
        // &mut T -> &T
        if (esperado.clase == Clase.REFERENCIA && real.clase == Clase.REFERENCIA) return false;

        if (esperado.clase == Clase.TUPLA && real.clase == Clase.TUPLA) {
            if (esperado.elementos.size() != real.elementos.size()) return false;
            for (int i = 0; i < esperado.elementos.size(); i++) {
                if (!asignable(esperado.elementos.get(i), real.elementos.get(i))) return false;
            }
            return true;
        }

        if ((esperado.clase == Clase.ARREGLO || esperado.clase == Clase.SLICE)
            && (real.clase == Clase.ARREGLO || real.clase == Clase.SLICE)) {
            return asignable(esperado.interno, real.interno);
        }

        if (real.clase == Clase.ENTERO_LIT && esperado.esEntero()) return true;
        if (real.clase == Clase.FLOTANTE_LIT && esperado.esFlotante()) return true;
        if (esperado.clase == Clase.ENTERO_LIT && real.esEntero()) return true;
        if (esperado.clase == Clase.FLOTANTE_LIT && real.esFlotante()) return true;

        if (esperado.clase == Clase.NOMINAL && real.clase == Clase.NOMINAL) {
            return esperado.nombre.equals(real.nombre);
        }

        return false;
    }

    public static Tipo unir(Tipo a, Tipo b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a.esNunca()) return b;
        if (b.esNunca()) return a;
        if (a.esDesconocido() || b.esDesconocido()) return Tipo.DESCONOCIDO;
        if (asignable(a, b)) return a;
        if (asignable(b, a)) return b;
        return Tipo.DESCONOCIDO;
    }

    public static Tipo parsear(String texto) {
        if (texto == null) return Tipo.UNIT;
        texto = texto.trim();
        if (texto.startsWith("&mut ")) return referencia(parsear(texto.substring(5)), true);
        if (texto.startsWith("&")) return referencia(parsear(texto.substring(1)), false);
        if (texto.startsWith("[") && texto.endsWith("]")) return slice(parsear(texto.substring(1, texto.length() - 1)));
        if (texto.startsWith("(") && texto.endsWith(")")) {
            String interior = texto.substring(1, texto.length() - 1).trim();
            if (interior.isEmpty()) return UNIT;
            List<Tipo> elems = new ArrayList<>();
            for (String parte : interior.split(",")) {
                elems.add(parsear(parte.trim()));
            }
            return tupla(elems);
        }
        switch (texto) {
            case "bool": return BOOL;
            case "char": return CHAR;
            case "String": return STRING;
            case "str": return STR;
            case "()": return UNIT;
            default: return nominal(texto);
        }
    }

    @Override
    public String toString() { return nombre; }
}
