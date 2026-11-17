package GUI;

import Analizador_lexico.LexicalError;
import Analizador_lexico.RustLexer;
import Analizador_lexico.Token;
import Analizador_semantico.AnalizadorSemantico;
import Analizador_semantico.Simbolo;
import Analizador_semantico.TablaDirecciones;
import Analizador_sintactico.RustParser;
import Codigo_intermedio.BloqueExpresion;
import Codigo_intermedio.Cuadruplo;
import Codigo_intermedio.GeneradorCodigoIntermedio;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Panel que ejecuta sobre un texto de código Rust la misma cadena de análisis que
 * el compilador (léxico -> sintáctico -> semántico -> código intermedio) y muestra
 * los resultados en pestañas, igual que el panel principal.
 *
 * Se usa para evaluar el CÓDIGO OPTIMIZADO: así se comprueba que, después de
 * optimizar, el programa sigue siendo válido y se puede generar su código intermedio.
 */
public class PanelAnalisisCodigo extends JTabbedPane {

    /** Resumen numérico del análisis. */
    public static class Resultado {
        public int tokens;
        public int erroresLexicos;
        public boolean sintacticoValido;
        public int erroresSintacticos;
        public int erroresSemanticos;
        public boolean semanticoEjecutado;
        public boolean intermedioGenerado;
        public int bloques;
        public int cuadruplos = -1;

        public boolean correcto() {
            return erroresLexicos == 0 && sintacticoValido && semanticoEjecutado && erroresSemanticos == 0 && intermedioGenerado;
        }
    }

    private final JTextArea areaResumen = new JTextArea();
    private final JTable tablaLexico = crearTabla("Línea", "Lexema", "Componente Léxico (Tipo)", "ID Token");
    private final JTable tablaSintactico = crearTabla("Línea", "Estructura Evaluada", "Estado");
    private final JTable tablaSemantico = crearTabla("Línea", "Estructura", "Detalle / Declaración", "Ámbito (Scope)", "Resultado");
    private final JTable tablaSimbolos = crearTabla("Nombre", "Categoría", "Tipo", "Mutable", "Ámbito", "Línea decl.", "Inicializado", "Usos", "Dirección");
    private final JTable tablaDirecciones = crearTabla("Nombre", "Segmento", "Dirección", "Desplazamiento", "Tamaño (bytes)", "Tipo", "Ámbito", "Línea");
    private final JTable tablaCuadruplosGlobal = crearTabla("#", "Operador", "Argumento 1", "Argumento 2", "Resultado");
    private final JTable tablaCuadruplosBloque = crearTabla("#", "Operador", "Argumento 1", "Argumento 2", "Resultado");
    private final DefaultListModel<String> modeloListaBloques = new DefaultListModel<>();
    private final JList<String> listaBloques = new JList<>(modeloListaBloques);
    private final JTextArea areaDetalleBloque = new JTextArea();
    private GeneradorCodigoIntermedio.ResultadoCodigoIntermedio codigoIntermedio;

    public PanelAnalisisCodigo() {
        setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        areaResumen.setEditable(false);
        areaResumen.setFont(new Font("Consolas", Font.PLAIN, 13));
        areaResumen.setMargin(new Insets(8, 10, 8, 10));

        addTab("Resumen", new JScrollPane(areaResumen));
        addTab("Léxico", new JScrollPane(tablaLexico));
        addTab("Sintáctico", new JScrollPane(tablaSintactico));
        addTab("Semántico", new JScrollPane(tablaSemantico));
        addTab("Tabla de Símbolos", new JScrollPane(tablaSimbolos));
        addTab("Tabla de Direcciones", new JScrollPane(tablaDirecciones));
        addTab("Código Intermedio", crearPanelIntermedio());

        for (int i = 0; i < getTabCount(); i++) {
            JLabel lbl = new JLabel(getTitleAt(i));
            lbl.setForeground(Color.BLACK);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            setTabComponentAt(i, lbl);
        }
        limpiar();
    }

    private JComponent crearPanelIntermedio() {
        listaBloques.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        listaBloques.addListSelectionListener(e -> mostrarBloqueSeleccionado());

        areaDetalleBloque.setEditable(false);
        areaDetalleBloque.setFont(new Font("Consolas", Font.PLAIN, 13));
        areaDetalleBloque.setLineWrap(true);
        areaDetalleBloque.setWrapStyleWord(true);
        areaDetalleBloque.setMargin(new Insets(8, 8, 8, 8));

        JPanel detalle = new JPanel(new BorderLayout());
        detalle.add(new JScrollPane(areaDetalleBloque), BorderLayout.NORTH);
        detalle.add(new JScrollPane(tablaCuadruplosBloque), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(listaBloques), detalle);
        split.setResizeWeight(0.28);

        JTabbedPane sub = new JTabbedPane();
        sub.addTab("Por expresión (prefijo / posfijo / cuádruplos)", split);
        sub.addTab("Todos los cuádruplos del programa", new JScrollPane(tablaCuadruplosGlobal));
        return sub;
    }

    /** Vacía todas las pestañas. */
    public void limpiar() {
        for (JTable t : new JTable[]{ tablaLexico, tablaSintactico, tablaSemantico, tablaSimbolos,
                                      tablaDirecciones, tablaCuadruplosGlobal, tablaCuadruplosBloque }) {
            ((DefaultTableModel) t.getModel()).setRowCount(0);
        }
        modeloListaBloques.clear();
        areaDetalleBloque.setText("");
        codigoIntermedio = null;
        areaResumen.setText("Aquí se mostrará el análisis léxico, sintáctico, semántico y el código\n"
            + "intermedio del código optimizado cuando presiones \"Optimizar código\".");
    }

    /** Analiza el texto completo y llena todas las pestañas. */
    public Resultado analizar(String codigo) {
        limpiar();
        Resultado r = new Resultado();
        StringBuilder resumen = new StringBuilder();

        // 1. LÉXICO
        RustLexer lexer = new RustLexer(codigo);
        lexer.analizar();
        List<Token> tokens = lexer.getTokens();
        List<LexicalError> erroresLex = lexer.getErrores();
        r.tokens = tokens.size();
        r.erroresLexicos = erroresLex.size();

        DefaultTableModel mLex = (DefaultTableModel) tablaLexico.getModel();
        for (Token t : tokens) {
            mLex.addRow(new Object[]{ t.linea, t.lexema, EstilosCompilador.NOMBRES_TOKEN.getOrDefault(t.tipo, t.tipo), t.id });
        }
        resumen.append(linea(erroresLex.isEmpty(), "Análisis léxico",
            tokens.size() + " token(s), " + erroresLex.size() + " error(es)"));
        for (LexicalError e : erroresLex) {
            resumen.append(String.format("      Línea %d | Error %d: %s | Lexema: '%s'%n", e.linea, e.codigoError, e.descripcion, e.lexema));
        }
        if (!erroresLex.isEmpty()) {
            resumen.append("   Sintáctico, semántico y código intermedio omitidos.\n");
            areaResumen.setText(resumen.toString());
            return r;
        }

        // 2. SINTÁCTICO
        RustParser.ResultadoSintactico rs = new RustParser(tokens).parsear();
        DefaultTableModel mSin = (DefaultTableModel) tablaSintactico.getModel();
        r.sintacticoValido = rs.esValido;
        if (!rs.esValido) {
            r.erroresSintacticos = rs.errores.size();
            resumen.append(linea(false, "Análisis sintáctico", rs.errores.size() + " error(es)"));
            for (RustParser.SyntaxError se : rs.errores) {
                mSin.addRow(new Object[]{ se.linea, "Error de sintaxis", "Rechazado" });
                resumen.append("      Línea ").append(se.linea).append(": ").append(se.mensaje).append("\n");
            }
            resumen.append("   Semántico y código intermedio omitidos.\n");
            areaResumen.setText(resumen.toString());
            return r;
        }
        mSin.addRow(new Object[]{ "-", "Programa principal", "Correcto" });
        resumen.append(linea(true, "Análisis sintáctico", "correcto"));

        // 3. SEMÁNTICO
        AnalizadorSemantico.ResultadoSemantico sem = new AnalizadorSemantico().analizar(rs.programa);
        r.semanticoEjecutado = true;
        r.erroresSemanticos = sem.errores.size();
        DefaultTableModel mSem = (DefaultTableModel) tablaSemantico.getModel();
        for (AnalizadorSemantico.EstructuraSemantica est : sem.estructuras) {
            mSem.addRow(new Object[]{ est.linea, est.tipoEstructura, est.detalle, est.ambito, est.resultado });
        }
        DefaultTableModel mSim = (DefaultTableModel) tablaSimbolos.getModel();
        for (Simbolo s : sem.simbolos) {
            mSim.addRow(new Object[]{
                s.nombre, s.categoria, s.tipo, s.esMutable ? "Sí" : "No", s.ambito,
                s.lineaDeclaracion, s.inicializado ? "Sí" : "No", s.usos,
                s.direccion >= 0 ? String.format("0x%08X", s.direccion) : "-" });
        }
        DefaultTableModel mDir = (DefaultTableModel) tablaDirecciones.getModel();
        for (TablaDirecciones.Entrada d : sem.direcciones) {
            mDir.addRow(new Object[]{ d.nombre, d.segmento, d.direccionHex(), d.desplazamientoHex(), d.tamano, d.tipo, d.ambito, d.linea });
        }
        resumen.append(linea(sem.errores.isEmpty(), "Análisis semántico",
            sem.errores.isEmpty() ? "consistencia correcta" : sem.errores.size() + " error(es)"));
        for (AnalizadorSemantico.SemanticError err : sem.errores) {
            resumen.append("      Línea ").append(err.linea).append(": ").append(err.mensaje).append("\n");
        }
        if (!sem.errores.isEmpty()) {
            resumen.append("   Código intermedio omitido.\n");
            areaResumen.setText(resumen.toString());
            return r;
        }

        // 4. CÓDIGO INTERMEDIO
        codigoIntermedio = new GeneradorCodigoIntermedio().generar(rs.programa);
        r.intermedioGenerado = true;
        r.bloques = codigoIntermedio.bloques.size();
        r.cuadruplos = codigoIntermedio.cuadruplosGlobales.size();
        int n = 1;
        for (BloqueExpresion b : codigoIntermedio.bloques) {
            modeloListaBloques.addElement("Tabla " + n++ + "  (línea " + b.linea + ")  " + b.contexto);
        }
        DefaultTableModel mGlob = (DefaultTableModel) tablaCuadruplosGlobal.getModel();
        for (Cuadruplo c : codigoIntermedio.cuadruplosGlobales) {
            mGlob.addRow(new Object[]{ c.numero, c.operador, c.arg1, c.arg2 == null ? "" : c.arg2, c.resultado });
        }
        if (!modeloListaBloques.isEmpty()) listaBloques.setSelectedIndex(0);
        resumen.append(linea(true, "Código intermedio",
            r.bloques + " tabla(s) de operaciones, " + r.cuadruplos + " cuádruplo(s) en total"));
        if (r.cuadruplos == 0) {
            resumen.append("   (No quedan operaciones aritméticas: el constant folding ya las calculó en compilación,\n"
                + "    por eso no hay cuádruplos que generar.)\n");
        }
        resumen.append("\nEl código optimizado es un programa válido y se puede compilar.\n");
        areaResumen.setText(resumen.toString());
        return r;
    }

    private String linea(boolean ok, String fase, String detalle) {
        return String.format("%s %-22s %s%n", ok ? "✔" : "✘", fase + ":", detalle);
    }

    private void mostrarBloqueSeleccionado() {
        int idx = listaBloques.getSelectedIndex();
        DefaultTableModel modelo = (DefaultTableModel) tablaCuadruplosBloque.getModel();
        modelo.setRowCount(0);
        if (idx < 0 || codigoIntermedio == null || idx >= codigoIntermedio.bloques.size()) {
            areaDetalleBloque.setText("");
            return;
        }
        BloqueExpresion b = codigoIntermedio.bloques.get(idx);
        StringBuilder sb = new StringBuilder();
        sb.append("Línea: ").append(b.linea).append("   Contexto: ").append(b.contexto).append("\n\n");
        sb.append("Infijo  : ").append(b.infijo).append("\n");
        sb.append("Prefijo : ").append(b.prefijo).append("\n");
        sb.append("Posfijo : ").append(b.posfijo).append("\n");
        if (b.resultadoEvaluado != null) sb.append("\n").append(b.resultadoEvaluado).append("\n");
        else sb.append("\n(El valor final no se puede determinar en tiempo de compilación: depende de un parámetro, una llamada a función u otro dato que no es conocido de antemano.)\n");
        areaDetalleBloque.setText(sb.toString());
        for (Cuadruplo c : b.cuadruplos) {
            modelo.addRow(new Object[]{ c.numero, c.operador, c.arg1, c.arg2 == null ? "" : c.arg2, c.resultado });
        }
    }

    private static JTable crearTabla(String... columnas) {
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tabla = new JTable(modelo);
        tabla.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabla.setRowHeight(24);
        tabla.setGridColor(EstilosCompilador.COLOR_BORDE);
        tabla.setSelectionBackground(EstilosCompilador.COLOR_SELECCION);
        tabla.setSelectionForeground(EstilosCompilador.COLOR_PRIMARIO);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabla.getTableHeader().setBackground(EstilosCompilador.COLOR_SIDEBAR);
        return tabla;
    }
}
