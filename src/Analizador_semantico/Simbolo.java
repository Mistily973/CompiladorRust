package Analizador_semantico;

import java.util.List;

/**
 * Entrada de la tabla de símbolos. Se amplió respecto a la versión
 * original (que solo tenía nombre/tipo-como-String/mutable/categoría)
 * para soportar tipos estructurados, dirección de memoria asociada
 * (llenada por TablaDirecciones), número de usos, y firma de parámetros
 * para funciones (necesaria para validar llamadas).
 */
public class Simbolo {

    public enum TipoSimbolo { VARIABLE, PARAMETRO, FUNCION, ESTRUCTURA, CAMPO }

    public static class ParametroInfo {
        public String nombre;
        public Tipo tipo;
        public ParametroInfo(String nombre, Tipo tipo) { this.nombre = nombre; this.tipo = tipo; }
    }

    public String nombre;
    public Tipo tipo;
    public boolean esMutable;
    public TipoSimbolo categoria;
    public int lineaDeclaracion;
    public boolean inicializado;
    public String ambito;
    public int usos = 0;

    public int direccion = -1;
    public int tamano = 0;

    // Solo para FUNCION
    public List<ParametroInfo> parametros;
    public Tipo tipoRetorno;

    public Simbolo(String nombre, Tipo tipo, boolean esMutable, TipoSimbolo categoria, int lineaDeclaracion, boolean inicializado) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.esMutable = esMutable;
        this.categoria = categoria;
        this.lineaDeclaracion = lineaDeclaracion;
        this.inicializado = inicializado;
    }

    public String firma() {
        if (categoria != TipoSimbolo.FUNCION) return nombre + ": " + tipo;
        StringBuilder sb = new StringBuilder("fn ").append(nombre).append("(");
        if (parametros != null) {
            for (int i = 0; i < parametros.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(parametros.get(i).nombre).append(": ").append(parametros.get(i).tipo);
            }
        }
        sb.append(")");
        if (tipoRetorno != null && !tipoRetorno.esUnit()) sb.append(" -> ").append(tipoRetorno);
        return sb.toString();
    }
}
