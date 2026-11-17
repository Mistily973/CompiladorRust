import Analizador_lexico.*;
import Analizador_sintactico.*;
import Analizador_semantico.*;
import Codigo_intermedio.*;
import java.nio.file.*;
import java.util.*;

public class S {
    public static void main(String[] args) throws Exception {
        String ruta = args[0];
        boolean verbose = args.length > 1 && args[1].equals("verbose");
        String texto = new String(Files.readAllBytes(Paths.get(ruta)));

        RustLexer lexer = new RustLexer(texto);
        lexer.analizar();
        for (LexicalError le : lexer.getErrores()) {
            System.out.println("LEX L" + le.linea + ": " + le.descripcion + " -> '" + le.lexema + "'");
        }
        if (!lexer.getErrores().isEmpty()) return;

        RustParser parser = new RustParser(lexer.getTokens());
        RustParser.ResultadoSintactico rs = parser.parsear();
        for (RustParser.SyntaxError se : rs.errores) {
            System.out.println("SIN L" + se.linea + ": " + se.mensaje);
        }
        if (!rs.esValido) return;

        AnalizadorSemantico sem = new AnalizadorSemantico();
        AnalizadorSemantico.ResultadoSemantico res = sem.analizar(rs.programa);
        for (AnalizadorSemantico.SemanticError e : res.errores) {
            System.out.println("SEM L" + e.linea + ": " + e.mensaje);
        }

        if (verbose) {
            System.out.println("--- ESTRUCTURAS ---");
            for (AnalizadorSemantico.EstructuraSemantica es : res.estructuras) {
                System.out.println("EST L" + es.linea + " [" + es.resultado + "] " + es.tipoEstructura + ": " + es.detalle + " (ambito=" + es.ambito + ")");
            }
            System.out.println("--- SIMBOLOS ---");
            for (Simbolo s : res.simbolos) {
                System.out.println("SIM " + s.nombre + " tipo=" + s.tipo + " mut=" + s.esMutable + " cat=" + s.categoria
                    + " ambito=" + s.ambito + " linea=" + s.lineaDeclaracion + " usos=" + s.usos + " dir=0x" + Integer.toHexString(s.direccion));
            }
            System.out.println("--- DIRECCIONES ---");
            for (TablaDirecciones.Entrada d : res.direcciones) {
                System.out.println("DIR " + d.nombre + " seg=" + d.segmento + " dir=" + d.direccionHex() + " tam=" + d.tamano + " tipo=" + d.tipo + " ambito=" + d.ambito);
            }
        }

        if (!res.errores.isEmpty()) {
            System.out.println("(se detectaron errores semánticos, no se genera código intermedio)");
            return;
        }

        GeneradorCodigoIntermedio gen = new GeneradorCodigoIntermedio();
        GeneradorCodigoIntermedio.ResultadoCodigoIntermedio ci = gen.generar(rs.programa);

        System.out.println("=== BLOQUES DE CODIGO INTERMEDIO (" + ci.bloques.size() + ") ===");
        int n = 1;
        for (BloqueExpresion b : ci.bloques) {
            System.out.println("--- Codigo intermedio #" + n + " | linea " + b.linea + " | " + b.contexto + " ---");
            System.out.println("  Infijo : " + b.infijo);
            System.out.println("  Prefijo: " + b.prefijo);
            System.out.println("  Posfijo: " + b.posfijo);
            System.out.println(String.format("  %-4s | %-8s | %-11s | %-11s | %-10s", "#", "Operador", "Argumento 1", "Argumento 2", "Resultado"));
            for (Cuadruplo c : b.cuadruplos) {
                System.out.println(String.format("  %-4s | %-8s | %-11s | %-11s | %-10s",
                    c.numero, c.operador, c.arg1, c.arg2 == null ? "" : c.arg2, c.resultado));
            }
            if (b.resultadoEvaluado != null) {
                System.out.println("  " + b.resultadoEvaluado);
            }
            n++;
        }

        if (verbose) {
            System.out.println("=== TABLA GLOBAL DE CUADRUPLOS (" + ci.cuadruplosGlobales.size() + ") ===");
            for (Cuadruplo c : ci.cuadruplosGlobales) {
                System.out.println("  [" + c.numero + "] " + c);
            }
        }

        System.out.println("OK: 0 errores lexicos, 0 sintacticos, " + res.errores.size() + " semanticos, "
            + ci.bloques.size() + " tablas de codigo intermedio generadas.");
    }
}
