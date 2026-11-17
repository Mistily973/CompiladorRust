package Analizador_semantico;

import java.util.ArrayList;
import java.util.List;

/**
 * Catálogo de firmas de la biblioteca estándar de Rust. El parser de este
 * proyecto no soporta bloques "impl", así que los únicos métodos que un
 * programa puede invocar son los de la biblioteca estándar: esta clase es
 * la única fuente de verdad para validar el número y tipo de argumentos
 * de esas llamadas (cumple el requisito de validación semántica #2).
 */
public class BibliotecaEstandar {

    public static class Firma {
        public String nombre;
        public List<Tipo> parametros;
        public Tipo retorno;
        public boolean mutaReceptor;
        public boolean argsMismoTipo; // true: los args deben coincidir con el tipo del receptor/elemento
        public int retornoDeArg = -1; // si >=0, el retorno es el tipo del argumento en esa posición

        public Firma(String nombre, Tipo retorno, boolean mutaReceptor, Tipo... parametros) {
            this.nombre = nombre;
            this.retorno = retorno;
            this.mutaReceptor = mutaReceptor;
            this.parametros = new ArrayList<>();
            for (Tipo t : parametros) this.parametros.add(t);
        }
    }

    // ---- Funciones por ruta (String::from, Vec::new, std::cmp::max, etc.) ----
    public static Firma funcion(List<String> ruta) {
        String path = String.join("::", ruta);
        switch (path) {
            case "String::from": return new Firma("from", Tipo.STRING, false, Tipo.STR_REF);
            case "String::new": return new Firma("new", Tipo.STRING, false);
            case "Vec::new": return new Firma("new", Tipo.nominal("Vec"), false);
            case "std::cmp::max": return new Firma("max", Tipo.I32, false, Tipo.I32, Tipo.I32);
            case "std::cmp::min": return new Firma("min", Tipo.I32, false, Tipo.I32, Tipo.I32);
            default: return null;
        }
    }

    public static Firma metodo(Tipo receptor, String nombre) {
        if (receptor == null) return null;
        Tipo base = receptor.sinReferencias();
        if (base.esString() || base.esStr()) return metodoTexto(base, nombre);
        if (base.esEntero()) return metodoEntero(base, nombre);
        if (base.esFlotante()) return metodoFlotante(base, nombre);
        if (base.esNominal() && base.nombre.equals("char")) return metodoChar(nombre);
        if (base.esNominal() && (base.nombre.equals("Vec") || base.nombre.startsWith("Vec<"))) return metodoColeccion(base, nombre);
        if (base.clase == Tipo.Clase.ARREGLO || base.clase == Tipo.Clase.SLICE) return metodoColeccion(base, nombre);
        if (base.esNominal() && base.nombre.startsWith("Option")) return metodoOption(base, nombre);
        return metodoGenerico(base, nombre);
    }

    public static Firma metodoInexistente(Tipo receptor, String nombre) {
        return null;
    }

    private static Firma metodoTexto(Tipo base, String nombre) {
        switch (nombre) {
            case "len": return new Firma("len", Tipo.USIZE, false);
            case "is_empty": return new Firma("is_empty", Tipo.BOOL, false);
            case "to_string": return new Firma("to_string", Tipo.STRING, false);
            case "clone": return new Firma("clone", Tipo.STRING, false);
            case "push": return new Firma("push", Tipo.UNIT, true, Tipo.CHAR);
            case "push_str": return new Firma("push_str", Tipo.UNIT, true, Tipo.STR_REF);
            case "trim": return new Firma("trim", Tipo.STR_REF, false);
            case "to_uppercase": return new Firma("to_uppercase", Tipo.STRING, false);
            case "to_lowercase": return new Firma("to_lowercase", Tipo.STRING, false);
            case "chars": return new Firma("chars", Tipo.nominal("Chars"), false);
            case "as_str": return new Firma("as_str", Tipo.STR_REF, false);
            case "contains": return new Firma("contains", Tipo.BOOL, false, Tipo.STR_REF);
            case "replace": return new Firma("replace", Tipo.STRING, false, Tipo.STR_REF, Tipo.STR_REF);
            case "split": return new Firma("split", Tipo.nominal("Split"), false, Tipo.STR_REF);
            case "parse": return new Firma("parse", Tipo.I32, false);
            default: return null;
        }
    }

    private static Firma metodoEntero(Tipo base, String nombre) {
        switch (nombre) {
            case "to_string": return new Firma("to_string", Tipo.STRING, false);
            case "abs": return new Firma("abs", base, false);
            case "pow": return new Firma("pow", base, false, Tipo.nominal("u32"));
            case "min": return new Firma("min", base, false, base);
            case "max": return new Firma("max", base, false, base);
            case "clone": return new Firma("clone", base, false);
            default: return null;
        }
    }

    private static Firma metodoFlotante(Tipo base, String nombre) {
        switch (nombre) {
            case "to_string": return new Firma("to_string", Tipo.STRING, false);
            case "abs": return new Firma("abs", base, false);
            case "sqrt": return new Firma("sqrt", base, false);
            case "round": return new Firma("round", base, false);
            case "floor": return new Firma("floor", base, false);
            case "ceil": return new Firma("ceil", base, false);
            case "clone": return new Firma("clone", base, false);
            default: return null;
        }
    }

    private static Firma metodoChar(String nombre) {
        switch (nombre) {
            case "to_string": return new Firma("to_string", Tipo.STRING, false);
            case "is_alphabetic": return new Firma("is_alphabetic", Tipo.BOOL, false);
            case "is_numeric": return new Firma("is_numeric", Tipo.BOOL, false);
            case "to_uppercase": return new Firma("to_uppercase", Tipo.CHAR, false);
            default: return null;
        }
    }

    private static Firma metodoColeccion(Tipo base, String nombre) {
        Tipo elem = base.interno != null ? base.interno : Tipo.DESCONOCIDO;
        switch (nombre) {
            case "push": return new Firma("push", Tipo.UNIT, true, elem);
            case "pop": return new Firma("pop", Tipo.nominal("Option"), true);
            case "len": return new Firma("len", Tipo.USIZE, false);
            case "is_empty": return new Firma("is_empty", Tipo.BOOL, false);
            case "get": return new Firma("get", Tipo.nominal("Option"), false, Tipo.USIZE);
            case "iter": return new Firma("iter", Tipo.nominal("Iter"), false);
            case "clone": return new Firma("clone", base, false);
            case "sort": return new Firma("sort", Tipo.UNIT, true);
            case "contains": return new Firma("contains", Tipo.BOOL, false, Tipo.referencia(elem, false));
            default: return null;
        }
    }

    private static Firma metodoOption(Tipo base, String nombre) {
        switch (nombre) {
            case "is_some": return new Firma("is_some", Tipo.BOOL, false);
            case "is_none": return new Firma("is_none", Tipo.BOOL, false);
            case "unwrap": return new Firma("unwrap", Tipo.DESCONOCIDO, false);
            case "unwrap_or": return new Firma("unwrap_or", Tipo.DESCONOCIDO, false, Tipo.DESCONOCIDO);
            default: return null;
        }
    }

    private static Firma metodoIterador(Tipo base, String nombre) {
        switch (nombre) {
            case "next": return new Firma("next", Tipo.nominal("Option"), true);
            case "sum": return new Firma("sum", Tipo.I32, false);
            case "count": return new Firma("count", Tipo.USIZE, false);
            default: return null;
        }
    }

    private static Firma metodoRango(Tipo base, String nombre) {
        return metodoIterador(base, nombre);
    }

    private static Firma metodoGenerico(Tipo base, String nombre) {
        if (base.esNominal() && (base.nombre.equals("Iter") || base.nombre.equals("Chars") || base.nombre.equals("Split"))) {
            return metodoIterador(base, nombre);
        }
        return null;
    }
}
