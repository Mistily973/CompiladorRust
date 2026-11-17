package GUI;

import Analizador_lexico.LexicalError;
import Analizador_lexico.RustLexer;
import Analizador_lexico.Token;
import Analizador_sintactico.AST;
import Analizador_sintactico.RustParser;
import Analizador_semantico.AnalizadorSemantico;
import Analizador_semantico.Simbolo;
import Analizador_semantico.TablaDirecciones;
import Codigo_intermedio.BloqueExpresion;
import Codigo_intermedio.Cuadruplo;
import Codigo_intermedio.GeneradorCodigoIntermedio;
import Ejecucion.ComparadorEjecucion;
import Optimizacion.Cambio;
import Optimizacion.Optimizador;
import Optimizacion.ResultadoOptimizacion;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class CompiladorGUI extends JFrame {

    // --- ESTADO ---
    private File archivoActual = null;
    private boolean modificado = false;

    // Último resultado de código intermedio generado (para la pestaña de abajo)
    private GeneradorCodigoIntermedio.ResultadoCodigoIntermedio ultimoCodigoIntermedio;

    // --- RESALTADO DE LÍNEA ACTIVA ---
    private Object marcaLineaActual = null;
    private final Highlighter.HighlightPainter pintorLinea = new Highlighter.HighlightPainter() {
        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r0 = c.modelToView2D(p0).getBounds();
                g.setColor(new Color(45, 45, 48));
                g.fillRect(0, r0.y, c.getWidth(), r0.height);
            } catch (BadLocationException ignored) {}
        }
    };

    // --- COMPONENTES ---
    private JMenuBar menuBar;
    private JToolBar toolBar;
    private JLabel labelEstado;
    private JLabel labelPosicion;
    private JLabel labelArchivo;

    private JTextArea areaCodigo;
    private JTextArea areaNumerosLinea;

    private JTable tablaLexico;
    private JTable tablaSintactico;
    private JTable tablaSemantico;
    private JTable tablaSimbolos;
    private JTable tablaDirecciones;
    private JTable tablaCuadruplosGlobal;
    private JTable tablaCuadruplosBloque;

    private JList<String> listaBloquesIntermedio;
    private DefaultListModel<String> modeloListaBloques;
    private JTextArea areaDetalleBloque;

    private JTextArea areaErroresLexicos;
    private JTextArea areaErroresSintacticos;
    private JTextArea areaErroresSemanticos;

    private JTextArea consola;

    // --- Pestaña "Código Optimizado" ---
    private JTextArea areaCodigoOptimizado;
    private JTextArea areaNumerosOptimizado;
    private JTextArea areaCambios;
    private JTextArea areaSalidaPrograma;
    private JTable tablaTiempos;
    private JLabel labelEstadoOptimizacion;
    private JButton btnOptimizar;
    private JButton btnCompararTiempos;
    private JButton btnExportar;
    private JCheckBox chkPlegado;
    private JCheckBox chkCodigoMuerto;
    private JCheckBox chkConservarResultado;
    private PanelAnalisisCodigo panelAnalisisOptimizado;
    private PanelAnalisisCodigo.Resultado resultadoAnalisisOptimizado = null;

    // Resultado del último análisis correcto (base para optimizar)
    private List<Token> ultimosTokens = null;
    private String ultimoCodigoAnalizado = null;
    private AST.Programa programaOptimizado = null;
    private String codigoOptimizado = null;

    public CompiladorGUI() {
        configurarVentana();
        configurarEstilos();
        crearMenu();
        crearToolBar();
        crearLayoutPrincipal();
        crearBarraEstado();
        configurarAtajos();
        actualizarTitulo();
    }

    private void configurarVentana() {
        setTitle("Compilador Rust - Léxico, Sintáctico, Semántico y Código Intermedio");
        setSize(1500, 850);
        setMinimumSize(new Dimension(1100, 650));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) { salir(); }
        });
        setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void configurarEstilos() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        UIManager.put("Panel.background", EstilosCompilador.COLOR_FONDO);
        UIManager.put("TabbedPane.background", EstilosCompilador.COLOR_SIDEBAR);
        UIManager.put("TabbedPane.font", new Font("Segoe UI", Font.BOLD, 12));
    }

    private void crearMenu() {
        menuBar = new JMenuBar();

        JMenu menuArchivo = new JMenu("Archivo");
        JMenuItem itemNuevo = new JMenuItem("Nuevo");
        JMenuItem itemAbrir = new JMenuItem("Abrir...");
        JMenuItem itemGuardar = new JMenuItem("Guardar");
        JMenuItem itemGuardarComo = new JMenuItem("Guardar como...");
        JMenuItem itemSalir = new JMenuItem("Salir");

        itemNuevo.addActionListener(e -> archivoNuevo());
        itemAbrir.addActionListener(e -> abrirArchivo());
        itemGuardar.addActionListener(e -> guardarArchivo());
        itemGuardarComo.addActionListener(e -> guardarComo());
        itemSalir.addActionListener(e -> salir());

        menuArchivo.add(itemNuevo);
        menuArchivo.add(itemAbrir);
        menuArchivo.addSeparator();
        menuArchivo.add(itemGuardar);
        menuArchivo.add(itemGuardarComo);
        menuArchivo.addSeparator();
        menuArchivo.add(itemSalir);

        JMenu menuAnalisis = new JMenu("Análisis");
        JMenuItem itemEjecutar = new JMenuItem("Ejecutar análisis (F5)");
        JMenuItem itemLimpiar = new JMenuItem("Limpiar resultados");
        itemEjecutar.addActionListener(e -> procesarCodigo());
        itemLimpiar.addActionListener(e -> limpiarResultados());
        menuAnalisis.add(itemEjecutar);
        menuAnalisis.add(itemLimpiar);

        JMenu menuEjemplos = new JMenu("Ejemplos");
        JMenuItem itemEjLexico = new JMenuItem("Cargar ejemplo léxico");
        JMenuItem itemEjSemantico = new JMenuItem("Cargar ejemplo semántico (con errores)");
        JMenuItem itemEjIntermedio = new JMenuItem("Cargar ejemplo de código intermedio");
        itemEjLexico.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_RUST));
        itemEjSemantico.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_SEMANTICO));
        itemEjIntermedio.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_INTERMEDIO));
        JMenuItem itemEjMuerto = new JMenuItem("Cargar ejemplo de código muerto");
        itemEjMuerto.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_CODIGO_MUERTO));
        JMenuItem itemEjFolding = new JMenuItem("Cargar ejemplo de constant folding");
        itemEjFolding.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_CONSTANT_FOLDING));
        JMenuItem itemEjOptim = new JMenuItem("Cargar ejemplo de optimizaciones combinadas");
        itemEjOptim.addActionListener(e -> cargarEjemplo(EstilosCompilador.CODIGO_EJEMPLO_OPTIMIZACIONES));
        menuEjemplos.add(itemEjLexico);
        menuEjemplos.add(itemEjSemantico);
        menuEjemplos.add(itemEjIntermedio);
        menuEjemplos.add(itemEjMuerto);
        menuEjemplos.add(itemEjFolding);
        menuEjemplos.add(itemEjOptim);

        JMenu menuAyuda = new JMenu("Ayuda");
        JMenuItem itemAcercaDe = new JMenuItem("Acerca de...");
        itemAcercaDe.addActionListener(e -> mostrarAcercaDe());
        menuAyuda.add(itemAcercaDe);

        menuBar.add(menuArchivo);
        menuBar.add(menuAnalisis);
        menuBar.add(menuEjemplos);
        menuBar.add(menuAyuda);

        setJMenuBar(menuBar);
    }

    private void cargarEjemplo(String codigo) {
        if (!confirmarDescartar()) return;
        areaCodigo.setText(codigo);
        archivoActual = null;
        modificado = false;
        limpiarResultados();
        actualizarTitulo();
        procesarCodigo();
    }

    private void crearToolBar() {
        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(EstilosCompilador.COLOR_SIDEBAR);
        toolBar.setPreferredSize(new Dimension(getWidth(), 50));

        JButton btnNuevo = new JButton("Nuevo");
        JButton btnAbrir = new JButton("Abrir");
        JButton btnGuardar = new JButton("Guardar");
        JButton btnAnalizar = new JButton("▶ Analizar (F5)");

        btnNuevo.addActionListener(e -> archivoNuevo());
        btnAbrir.addActionListener(e -> abrirArchivo());
        btnGuardar.addActionListener(e -> guardarArchivo());

        btnAnalizar.setBackground(EstilosCompilador.COLOR_EXITO);
        btnAnalizar.setForeground(Color.WHITE);
        btnAnalizar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnAnalizar.addActionListener(e -> procesarCodigo());

        toolBar.add(Box.createRigidArea(new Dimension(10, 0)));
        toolBar.add(btnNuevo);
        toolBar.add(Box.createRigidArea(new Dimension(5, 0)));
        toolBar.add(btnAbrir);
        toolBar.add(Box.createRigidArea(new Dimension(5, 0)));
        toolBar.add(btnGuardar);
        toolBar.addSeparator();
        toolBar.add(btnAnalizar);

        add(toolBar, BorderLayout.NORTH);
    }

    private void crearLayoutPrincipal() {
        Font fuenteEditor = new Font("Consolas", Font.PLAIN, 14);

        areaCodigo = new JTextArea(EstilosCompilador.CODIGO_EJEMPLO_RUST);
        areaCodigo.setFont(fuenteEditor);
        areaCodigo.setBackground(new Color(30, 30, 30));
        areaCodigo.setForeground(new Color(220, 220, 220));
        areaCodigo.setCaretColor(Color.WHITE);
        areaCodigo.setSelectionColor(EstilosCompilador.COLOR_SELECCION);
        areaCodigo.setSelectedTextColor(EstilosCompilador.COLOR_PRIMARIO);
        areaCodigo.setLineWrap(false);
        areaCodigo.setMargin(new Insets(5, 8, 5, 8));

        areaNumerosLinea = new JTextArea("1 ");
        areaNumerosLinea.setFont(fuenteEditor);
        areaNumerosLinea.setBackground(EstilosCompilador.COLOR_GUTTER);
        areaNumerosLinea.setForeground(EstilosCompilador.COLOR_TEXTO_SUAVE);
        areaNumerosLinea.setEditable(false);
        areaNumerosLinea.setMargin(new Insets(5, 8, 5, 8));

        areaCodigo.getDocument().addDocumentListener(new DocumentListener() {
            private void actualizar() {
                SwingUtilities.invokeLater(() -> {
                    marcarComoModificado();
                    actualizarNumeros();
                    actualizarPosicion();
                    resaltarLineaActual();
                });
            }
            @Override public void insertUpdate(DocumentEvent e) { actualizar(); }
            @Override public void removeUpdate(DocumentEvent e) { actualizar(); }
            @Override public void changedUpdate(DocumentEvent e) { actualizar(); }
        });
        areaCodigo.addCaretListener(e -> { actualizarPosicion(); resaltarLineaActual(); });

        JScrollPane scrollEditor = new JScrollPane(areaCodigo);
        scrollEditor.setRowHeaderView(areaNumerosLinea);

        JPanel panelEditor = new JPanel(new BorderLayout());
        labelArchivo = new JLabel("Sin título");
        labelArchivo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        labelArchivo.setForeground(EstilosCompilador.COLOR_TEXTO_SUAVE);
        labelArchivo.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        panelEditor.add(labelArchivo, BorderLayout.NORTH);
        panelEditor.add(scrollEditor, BorderLayout.CENTER);

        // ---- Tablas de resultados ----
        tablaLexico = crearTabla("Línea", "Lexema", "Componente Léxico (Tipo)", "ID Token");
        tablaSintactico = crearTabla("Línea", "Estructura Evaluada", "Estado");
        tablaSemantico = crearTabla("Línea", "Estructura", "Detalle / Declaración", "Ámbito (Scope)", "Resultado");
        tablaSimbolos = crearTabla("Nombre", "Categoría", "Tipo", "Mutable", "Ámbito", "Línea decl.", "Inicializado", "Usos", "Dirección");
        tablaDirecciones = crearTabla("Nombre", "Segmento", "Dirección", "Desplazamiento", "Tamaño (bytes)", "Tipo", "Ámbito", "Línea");
        tablaCuadruplosGlobal = crearTabla("#", "Operador", "Argumento 1", "Argumento 2", "Resultado");
        tablaCuadruplosBloque = crearTabla("#", "Operador", "Argumento 1", "Argumento 2", "Resultado");

        agregarNavegacionPorLinea(tablaLexico, 0);
        agregarNavegacionPorLinea(tablaSintactico, 0);
        agregarNavegacionPorLinea(tablaSemantico, 0);
        agregarNavegacionPorLinea(tablaSimbolos, 5);
        agregarNavegacionPorLinea(tablaDirecciones, 7);

        JTabbedPane pestanasResultados = new JTabbedPane();
        pestanasResultados.addTab("Léxico", new JScrollPane(tablaLexico));
        pestanasResultados.addTab("Sintáctico", new JScrollPane(tablaSintactico));
        pestanasResultados.addTab("Semántico", new JScrollPane(tablaSemantico));
        pestanasResultados.addTab("Tabla de Símbolos", new JScrollPane(tablaSimbolos));
        pestanasResultados.addTab("Tabla de Direcciones", new JScrollPane(tablaDirecciones));
        pestanasResultados.addTab("Código Intermedio", crearPanelCodigoIntermedio());
        pestanasResultados.addTab("Código Optimizado", crearPanelCodigoOptimizado(fuenteEditor));

        for (int i = 0; i < pestanasResultados.getTabCount(); i++) {
            JLabel lbl = new JLabel(pestanasResultados.getTitleAt(i));
            lbl.setForeground(Color.BLACK);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            pestanasResultados.setTabComponentAt(i, lbl);
        }

        consola = new JTextArea();
        consola.setEditable(false);
        consola.setFont(new Font("Consolas", Font.PLAIN, 12));
        consola.setBackground(Color.WHITE);

        JPanel panelConsola = new JPanel(new BorderLayout());
        JLabel labelConsola = new JLabel(" Consola de Salida");
        labelConsola.setFont(new Font("Segoe UI", Font.BOLD, 12));
        labelConsola.setBackground(EstilosCompilador.COLOR_SIDEBAR);
        labelConsola.setOpaque(true);
        panelConsola.add(labelConsola, BorderLayout.NORTH);
        panelConsola.add(new JScrollPane(consola), BorderLayout.CENTER);

        JSplitPane splitDerecho = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pestanasResultados, panelConsola);
        splitDerecho.setResizeWeight(0.72);

        JSplitPane splitSuperior = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panelEditor, splitDerecho);
        splitSuperior.setResizeWeight(0.45);

        JTabbedPane pestanasErrores = new JTabbedPane();
        areaErroresLexicos = crearAreaErrores();
        areaErroresSintacticos = crearAreaErrores();
        areaErroresSemanticos = crearAreaErrores();
        pestanasErrores.addTab("Errores Léxicos", new JScrollPane(areaErroresLexicos));
        pestanasErrores.addTab("Errores Sintácticos", new JScrollPane(areaErroresSintacticos));
        pestanasErrores.addTab("Errores Semánticos", new JScrollPane(areaErroresSemanticos));
        for (int i = 0; i < pestanasErrores.getTabCount(); i++) {
            JLabel lbl = new JLabel(pestanasErrores.getTitleAt(i));
            lbl.setForeground(Color.BLACK);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            pestanasErrores.setTabComponentAt(i, lbl);
        }

        JSplitPane splitPrincipal = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitSuperior, pestanasErrores);
        splitPrincipal.setResizeWeight(0.75);

        add(splitPrincipal, BorderLayout.CENTER);
        actualizarNumeros();
        resaltarLineaActual();
    }

    /**
     * Pestaña "Código Intermedio": a la izquierda la lista de tablas (una por
     * cada operación aritmética encontrada, con su línea y contexto); a la
     * derecha el infijo/prefijo/posfijo de la expresión seleccionada y su
     * tabla de cuádruplos paso a paso. Abajo, en una pestaña aparte, la tabla
     * global con TODOS los cuádruplos del programa (para "observar los
     * cuádruplos" del programa completo de un vistazo).
     */
    private JComponent crearPanelCodigoIntermedio() {
        modeloListaBloques = new DefaultListModel<>();
        listaBloquesIntermedio = new JList<>(modeloListaBloques);
        listaBloquesIntermedio.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        listaBloquesIntermedio.addListSelectionListener(e -> mostrarBloqueSeleccionado());

        areaDetalleBloque = new JTextArea();
        areaDetalleBloque.setEditable(false);
        areaDetalleBloque.setFont(new Font("Consolas", Font.PLAIN, 13));
        areaDetalleBloque.setLineWrap(true);
        areaDetalleBloque.setWrapStyleWord(true);
        areaDetalleBloque.setMargin(new Insets(8, 8, 8, 8));

        JPanel panelDetalle = new JPanel(new BorderLayout());
        panelDetalle.add(new JScrollPane(areaDetalleBloque), BorderLayout.NORTH);
        panelDetalle.add(new JScrollPane(tablaCuadruplosBloque), BorderLayout.CENTER);

        JSplitPane splitIntermedio = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(listaBloquesIntermedio), panelDetalle);
        splitIntermedio.setResizeWeight(0.28);

        JTabbedPane sub = new JTabbedPane();
        sub.addTab("Por expresión (prefijo / posfijo / cuádruplos)", splitIntermedio);
        sub.addTab("Todos los cuádruplos del programa", new JScrollPane(tablaCuadruplosGlobal));
        return sub;
    }

    // =========================================================================
    // PESTAÑA "CÓDIGO OPTIMIZADO"
    // =========================================================================
    /**
     * Pestaña con una "consola" donde se muestra el código optimizado. Arriba hay
     * tres botones (Optimizar, Comparar tiempos, Exportar .txt); abajo, pestañas
     * con la lista de cambios, la tabla de tiempos y la salida de ambos programas.
     */
    private JComponent crearPanelCodigoOptimizado(Font fuenteEditor) {
        // ---- Barra de botones ----
        btnOptimizar = new JButton("⚡ Optimizar código");
        btnOptimizar.setBackground(EstilosCompilador.COLOR_EXITO);
        btnOptimizar.setForeground(Color.WHITE);
        btnOptimizar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnOptimizar.addActionListener(e -> optimizarCodigo());

        btnCompararTiempos = new JButton("⏱ Comparar tiempos de ejecución");
        btnCompararTiempos.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCompararTiempos.addActionListener(e -> compararTiempos());

        btnExportar = new JButton("💾 Exportar a .txt");
        btnExportar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnExportar.addActionListener(e -> exportarCodigoOptimizado());

        labelEstadoOptimizacion = new JLabel(" ");
        labelEstadoOptimizacion.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        labelEstadoOptimizacion.setForeground(EstilosCompilador.COLOR_TEXTO_SUAVE);

        chkPlegado = new JCheckBox("Constant folding", true);
        chkCodigoMuerto = new JCheckBox("Eliminación de código muerto", true);
        chkPlegado.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkCodigoMuerto.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkPlegado.setToolTipText("Calcula en compilación las expresiones constantes y propaga constantes");
        chkCodigoMuerto.setToolTipText("Elimina variables sin uso, código inalcanzable, ramas imposibles, funciones sin llamar...");

        JPanel fila1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        fila1.add(btnOptimizar);
        fila1.add(btnCompararTiempos);
        fila1.add(btnExportar);
        chkConservarResultado = new JCheckBox("Conservar el resultado final de cada función", true);
        chkConservarResultado.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkConservarResultado.setToolTipText("En funciones sin retorno cuyo último paso es un 'let' (p. ej. let r = ...;), "
            + "esa variable es el resultado de la función y no se elimina");

        JPanel fila2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        fila2.add(new JLabel("Aplicar:"));
        fila2.add(chkPlegado);
        fila2.add(chkCodigoMuerto);
        fila2.add(chkConservarResultado);
        JPanel fila3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        fila3.add(labelEstadoOptimizacion);
        JPanel barra = new JPanel(new GridLayout(3, 1));
        barra.add(fila1);
        barra.add(fila2);
        barra.add(fila3);

        // ---- Consola con el código optimizado ----
        areaCodigoOptimizado = new JTextArea();
        areaCodigoOptimizado.setFont(fuenteEditor);
        areaCodigoOptimizado.setBackground(new Color(30, 30, 30));
        areaCodigoOptimizado.setForeground(new Color(220, 220, 220));
        areaCodigoOptimizado.setCaretColor(Color.WHITE);
        areaCodigoOptimizado.setSelectionColor(EstilosCompilador.COLOR_SELECCION);
        areaCodigoOptimizado.setSelectedTextColor(EstilosCompilador.COLOR_PRIMARIO);
        areaCodigoOptimizado.setEditable(false);
        areaCodigoOptimizado.setLineWrap(false);
        areaCodigoOptimizado.setMargin(new Insets(5, 8, 5, 8));

        areaNumerosOptimizado = new JTextArea("1 ");
        areaNumerosOptimizado.setFont(fuenteEditor);
        areaNumerosOptimizado.setBackground(EstilosCompilador.COLOR_GUTTER);
        areaNumerosOptimizado.setForeground(EstilosCompilador.COLOR_TEXTO_SUAVE);
        areaNumerosOptimizado.setEditable(false);
        areaNumerosOptimizado.setMargin(new Insets(5, 8, 5, 8));

        areaCodigoOptimizado.getDocument().addDocumentListener(new DocumentListener() {
            private void actualizar() {
                int lineas = areaCodigoOptimizado.getLineCount();
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= lineas; i++) sb.append(i).append("\n");
                if (!areaNumerosOptimizado.getText().equals(sb.toString())) areaNumerosOptimizado.setText(sb.toString());
            }
            @Override public void insertUpdate(DocumentEvent e) { actualizar(); }
            @Override public void removeUpdate(DocumentEvent e) { actualizar(); }
            @Override public void changedUpdate(DocumentEvent e) { actualizar(); }
        });

        JScrollPane scrollCodigo = new JScrollPane(areaCodigoOptimizado);
        scrollCodigo.setRowHeaderView(areaNumerosOptimizado);

        // ---- Parte inferior: cambios / tiempos / salida ----
        areaCambios = new JTextArea();
        areaCambios.setEditable(false);
        areaCambios.setFont(new Font("Consolas", Font.PLAIN, 12));
        areaCambios.setMargin(new Insets(6, 8, 6, 8));

        tablaTiempos = crearTabla("Versión", "Líneas de código", "Cuádruplos", "Operaciones evaluadas", "Tiempo de ejecución");

        areaSalidaPrograma = new JTextArea();
        areaSalidaPrograma.setEditable(false);
        areaSalidaPrograma.setFont(new Font("Consolas", Font.PLAIN, 12));
        areaSalidaPrograma.setMargin(new Insets(6, 8, 6, 8));

        JTabbedPane inferior = new JTabbedPane();
        panelAnalisisOptimizado = new PanelAnalisisCodigo();
        inferior.addTab("Cambios aplicados", new JScrollPane(areaCambios));
        inferior.addTab("Análisis del código optimizado", panelAnalisisOptimizado);
        inferior.addTab("Comparación de tiempos", new JScrollPane(tablaTiempos));
        inferior.addTab("Salida del programa", new JScrollPane(areaSalidaPrograma));
        for (int i = 0; i < inferior.getTabCount(); i++) {
            JLabel lbl = new JLabel(inferior.getTitleAt(i));
            lbl.setForeground(Color.BLACK);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            inferior.setTabComponentAt(i, lbl);
        }
        pestanasInferioresOptimizado = inferior;

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollCodigo, inferior);
        split.setResizeWeight(0.62);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(barra, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        reiniciarPestanaOptimizada();
        return panel;
    }

    private JTabbedPane pestanasInferioresOptimizado;

    private void reiniciarPestanaOptimizada() {
        ultimosTokens = null;
        ultimoCodigoAnalizado = null;
        programaOptimizado = null;
        codigoOptimizado = null;
        if (areaCodigoOptimizado == null) return;
        areaCodigoOptimizado.setText("// Presiona \"Optimizar código\" para generar el código optimizado.\n"
            + "// (Antes, ejecuta el análisis con F5 sin errores.)");
        areaCambios.setText("");
        areaSalidaPrograma.setText("");
        ((DefaultTableModel) tablaTiempos.getModel()).setRowCount(0);
        panelAnalisisOptimizado.limpiar();
        resultadoAnalisisOptimizado = null;
        labelEstadoOptimizacion.setText(" ");
        btnExportar.setEnabled(false);
    }

    /** El análisis debe haberse ejecutado sin errores sobre el texto que hay ahora en el editor. */
    private boolean hayAnalisisVigente() {
        if (ultimosTokens == null || ultimoCodigoAnalizado == null) {
            JOptionPane.showMessageDialog(this,
                "Primero ejecuta el análisis (F5) y corrige los errores léxicos, sintácticos y semánticos.",
                "Aún no se puede optimizar", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (!ultimoCodigoAnalizado.equals(areaCodigo.getText())) {
            JOptionPane.showMessageDialog(this,
                "El código del editor cambió desde el último análisis.\nPresiona F5 para analizarlo de nuevo antes de optimizar.",
                "Análisis desactualizado", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    private void optimizarCodigo() {
        if (!hayAnalisisVigente()) return;
        boolean plegado = chkPlegado.isSelected(), muerto = chkCodigoMuerto.isSelected();
        if (!plegado && !muerto) {
            JOptionPane.showMessageDialog(this, "Selecciona al menos una optimización para aplicar.",
                "Nada que aplicar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // El optimizador modifica el AST, por eso se parsea una copia nueva de los tokens.
        AST.Programa copia = new RustParser(ultimosTokens).parsear().programa;
        ResultadoOptimizacion res = Optimizador.optimizar(copia, plegado, muerto, chkConservarResultado.isSelected());
        programaOptimizado = copia;
        codigoOptimizado = res.codigo;

        areaCodigoOptimizado.setText(res.codigo);
        areaCodigoOptimizado.setCaretPosition(0);

        StringBuilder sb = new StringBuilder();
        if (res.cambios.isEmpty()) {
            sb.append("No se encontró nada que optimizar con las opciones seleccionadas.\n");
        } else {
            agregarCambios(sb, res, Optimizador.PLEGADO, plegado);
            agregarCambios(sb, res, Optimizador.CODIGO_MUERTO, muerto);
        }
        areaCambios.setText(sb.toString());
        areaCambios.setCaretPosition(0);

        ((DefaultTableModel) tablaTiempos.getModel()).setRowCount(0);
        areaSalidaPrograma.setText("");
        pestanasInferioresOptimizado.setSelectedIndex(0);

        // El código optimizado se evalúa igual que el original: léxico, sintáctico, semántico e intermedio
        resultadoAnalisisOptimizado = panelAnalisisOptimizado.analizar(res.codigo);
        PanelAnalisisCodigo.Resultado ra = resultadoAnalisisOptimizado;

        int antes = contarLineas(ultimoCodigoAnalizado), despues = contarLineas(res.codigo);
        labelEstadoOptimizacion.setText(String.format("Constant folding: %d  |  Código muerto: %d  |  líneas: %d → %d  |  análisis del optimizado: %s",
            res.contar(Optimizador.PLEGADO), res.contar(Optimizador.CODIGO_MUERTO), antes, despues,
            ra.correcto() ? "correcto" : "CON ERRORES"));
        btnExportar.setEnabled(true);
        consola.append(String.format("Optimización: %d cambio(s) de constant folding y %d de código muerto; líneas %d → %d.%n",
            res.contar(Optimizador.PLEGADO), res.contar(Optimizador.CODIGO_MUERTO), antes, despues));
        if (ra.correcto()) {
            consola.append(String.format("Código optimizado analizado: %d token(s), sintaxis y semántica correctas, %d cuádruplo(s).%n",
                ra.tokens, ra.cuadruplos));
        } else {
            consola.append("¡El código optimizado NO pasó el análisis! Revisa la pestaña \"Análisis del código optimizado\".\n");
        }
    }

    private void agregarCambios(StringBuilder sb, ResultadoOptimizacion res, String optimizacion, boolean aplicada) {
        if (!aplicada) return;
        sb.append(String.format("=== %s (%d cambio(s)) ===%n", optimizacion, res.contar(optimizacion)));
        for (Cambio c : res.cambios) {
            if (!c.optimizacion.equals(optimizacion)) continue;
            sb.append(String.format("Línea %d  [%s]%n      %s%n", c.linea, c.tipo, c.detalle));
        }
        sb.append("\n");
    }

    private void compararTiempos() {
        if (!hayAnalisisVigente()) return;
        // Siempre se optimiza de nuevo para que lo medido coincida con las casillas seleccionadas
        programaOptimizado = null;
        optimizarCodigo();
        if (programaOptimizado == null) return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        ComparadorEjecucion.Medicion m;
        try {
            AST.Programa original = new RustParser(ultimosTokens).parsear().programa;
            m = ComparadorEjecucion.comparar(original, programaOptimizado);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }

        DefaultTableModel modelo = (DefaultTableModel) tablaTiempos.getModel();
        modelo.setRowCount(0);
        StringBuilder salida = new StringBuilder();

        if (m.error != null) {
            areaSalidaPrograma.setText(m.error);
            pestanasInferioresOptimizado.setSelectedIndex(3);
            labelEstadoOptimizacion.setText("No se pudo ejecutar el programa.");
            return;
        }

        int lineasOrig = contarLineas(ultimoCodigoAnalizado), lineasOpt = contarLineas(codigoOptimizado);
        int cuadOrig = ultimoCodigoIntermedio != null ? ultimoCodigoIntermedio.cuadruplosGlobales.size() : -1;
        int cuadOpt = resultadoAnalisisOptimizado != null ? resultadoAnalisisOptimizado.cuadruplos : -1;
        modelo.addRow(new Object[]{ "Original", lineasOrig, cuadOrig >= 0 ? cuadOrig : "—",
            m.original.operaciones, formatearTiempo(m.nanosOriginal) });
        modelo.addRow(new Object[]{ "Optimizado", lineasOpt, cuadOpt >= 0 ? cuadOpt : "—",
            m.optimizado.operaciones, formatearTiempo(m.nanosOptimizado) });
        modelo.addRow(new Object[]{ "Mejora",
            porcentajeMejora(lineasOrig, lineasOpt),
            (cuadOrig >= 0 && cuadOpt >= 0) ? porcentajeMejora(cuadOrig, cuadOpt) : "—",
            porcentajeMejora(m.original.operaciones, m.optimizado.operaciones),
            porcentajeMejora(m.nanosOriginal, m.nanosOptimizado) });

        salida.append("===== Salida del programa ORIGINAL =====\n").append(m.original.salida);
        if (m.original.error != null) salida.append("\n[ERROR] ").append(m.original.error).append("\n");
        salida.append("\n===== Salida del programa OPTIMIZADO =====\n").append(m.optimizado.salida);
        if (m.optimizado.error != null) salida.append("\n[ERROR] ").append(m.optimizado.error).append("\n");
        salida.append("\n").append(m.mismaSalida
            ? "✔ Ambas versiones producen exactamente la misma salida."
            : "✘ ¡Las salidas son DIFERENTES! Revisa la optimización.").append("\n");
        areaSalidaPrograma.setText(salida.toString());
        areaSalidaPrograma.setCaretPosition(0);

        pestanasInferioresOptimizado.setSelectedIndex(2);
        labelEstadoOptimizacion.setText(m.repeticiones > 0
            ? String.format("Tiempo = mediana de %d ejecuciones (con calentamiento previo).", m.repeticiones)
            : "El programa terminó con error: no se midieron tiempos repetidos.");
        consola.append(String.format("Tiempos de ejecución: original %s, optimizado %s (%s).%n",
            formatearTiempo(m.nanosOriginal), formatearTiempo(m.nanosOptimizado),
            porcentajeMejora(m.nanosOriginal, m.nanosOptimizado)));
    }

    private void exportarCodigoOptimizado() {
        if (codigoOptimizado == null) {
            JOptionPane.showMessageDialog(this, "Primero presiona \"Optimizar código\".",
                "Nada que exportar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Exportar código optimizado");
        String base = "programa";
        if (archivoActual != null) {
            base = archivoActual.getName();
            int punto = base.lastIndexOf('.');
            if (punto > 0) base = base.substring(0, punto);
        }
        fc.setSelectedFile(new File(base + "_optimizado.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        File destino = fc.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".txt")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".txt");
        }
        if (destino.exists()) {
            int r = JOptionPane.showConfirmDialog(this, "El archivo \"" + destino.getName() + "\" ya existe.\n¿Deseas reemplazarlo?",
                "Confirmar", JOptionPane.YES_NO_OPTION);
            if (r != JOptionPane.YES_OPTION) return;
        }
        try {
            Files.write(destino.toPath(), codigoOptimizado.getBytes(StandardCharsets.UTF_8));
            labelEstadoOptimizacion.setText("Exportado: " + destino.getName());
            consola.append("Código optimizado exportado a: " + destino.getAbsolutePath() + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Cuenta TODAS las líneas del texto (incluidas las vacías); un salto de línea final no suma una línea extra. */
    private int contarLineas(String texto) {
        if (texto == null || texto.isEmpty()) return 0;
        String[] lineas = texto.split("\\R", -1);
        int n = lineas.length;
        if (lineas[n - 1].isEmpty()) n--;
        return n;
    }

    private String formatearTiempo(double nanos) {
        if (nanos < 1_000) return String.format("%.0f ns", nanos);
        if (nanos < 1_000_000) return String.format("%.1f µs", nanos / 1_000);
        return String.format("%.2f ms", nanos / 1_000_000);
    }

    /** Texto como "48.2 % menos" comparando el original contra el optimizado. */
    private String porcentajeMejora(double original, double optimizado) {
        if (original <= 0) return "—";
        double pct = (original - optimizado) / original * 100.0;
        if (Math.abs(pct) < 0.05) return "sin cambio";
        return String.format("%.1f %% %s", Math.abs(pct), pct > 0 ? "menos" : "más");
    }

    private void mostrarBloqueSeleccionado() {
        int idx = listaBloquesIntermedio.getSelectedIndex();
        DefaultTableModel modelo = (DefaultTableModel) tablaCuadruplosBloque.getModel();
        modelo.setRowCount(0);
        if (idx < 0 || ultimoCodigoIntermedio == null || idx >= ultimoCodigoIntermedio.bloques.size()) {
            areaDetalleBloque.setText("");
            return;
        }
        BloqueExpresion b = ultimoCodigoIntermedio.bloques.get(idx);
        StringBuilder sb = new StringBuilder();
        sb.append("Línea: ").append(b.linea).append("   Contexto: ").append(b.contexto).append("\n\n");
        sb.append("Infijo  : ").append(b.infijo).append("\n");
        sb.append("Prefijo : ").append(b.prefijo).append("\n");
        sb.append("Posfijo : ").append(b.posfijo).append("\n");
        if (b.resultadoEvaluado != null) {
            sb.append("\n").append(b.resultadoEvaluado).append("\n");
        } else {
            sb.append("\n(El valor final no se puede determinar en tiempo de compilación: depende de un parámetro, una llamada a función u otro dato que no es conocido de antemano.)\n");
        }
        areaDetalleBloque.setText(sb.toString());

        for (Cuadruplo c : b.cuadruplos) {
            modelo.addRow(new Object[]{ c.numero, c.operador, c.arg1, c.arg2 == null ? "" : c.arg2, c.resultado });
        }
    }

    private JTable crearTabla(String... columnas) {
        DefaultTableModel modelo = new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable tabla = new JTable(modelo);
        configurarEstiloTabla(tabla);
        return tabla;
    }

    private JTextArea crearAreaErrores() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setForeground(EstilosCompilador.COLOR_ERROR);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        return area;
    }

    private void agregarNavegacionPorLinea(JTable tabla, int columnaLinea) {
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tabla.getSelectedRow();
                    if (row != -1) {
                        try {
                            Object val = tabla.getValueAt(row, columnaLinea);
                            int linea = Integer.parseInt(val.toString());
                            irALineaEditor(linea);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        });
    }

    private void configurarEstiloTabla(JTable tabla) {
        tabla.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabla.setRowHeight(24);
        tabla.setGridColor(EstilosCompilador.COLOR_BORDE);
        tabla.setSelectionBackground(EstilosCompilador.COLOR_SELECCION);
        tabla.setSelectionForeground(EstilosCompilador.COLOR_PRIMARIO);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabla.getTableHeader().setBackground(EstilosCompilador.COLOR_SIDEBAR);
    }

    private void crearBarraEstado() {
        JPanel panelEstado = new JPanel(new BorderLayout());
        panelEstado.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, EstilosCompilador.COLOR_BORDE));
        panelEstado.setPreferredSize(new Dimension(getWidth(), 28));

        labelEstado = new JLabel("  Listo");
        labelEstado.setForeground(EstilosCompilador.COLOR_TEXTO);
        labelEstado.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        labelPosicion = new JLabel("Línea 1, Columna 1  ");
        labelPosicion.setForeground(EstilosCompilador.COLOR_TEXTO_SUAVE);
        labelPosicion.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        panelEstado.add(labelEstado, BorderLayout.WEST);
        panelEstado.add(labelPosicion, BorderLayout.EAST);
        add(panelEstado, BorderLayout.SOUTH);
    }

    private void resaltarLineaActual() {
        try {
            Highlighter hl = areaCodigo.getHighlighter();
            if (marcaLineaActual != null) hl.removeHighlight(marcaLineaActual);
            int pos = areaCodigo.getCaretPosition();
            int inicioLinea = areaCodigo.getLineStartOffset(areaCodigo.getLineOfOffset(pos));
            int finLinea = areaCodigo.getLineEndOffset(areaCodigo.getLineOfOffset(pos));
            marcaLineaActual = hl.addHighlight(inicioLinea, finLinea, pintorLinea);
            areaCodigo.repaint();
        } catch (BadLocationException ignored) {}
    }

    private void irALineaEditor(int linea) {
        try {
            int startOffset = areaCodigo.getLineStartOffset(linea - 1);
            int endOffset = areaCodigo.getLineEndOffset(linea - 1);
            areaCodigo.setCaretPosition(startOffset);
            areaCodigo.select(startOffset, endOffset);
            areaCodigo.requestFocus();
        } catch (BadLocationException ignored) {}
    }

    private boolean confirmarDescartar() {
        if (!modificado) return true;
        int resp = JOptionPane.showConfirmDialog(this,
            "El archivo actual tiene cambios sin guardar.\n¿Deseas guardarlos antes de continuar?",
            "Cambios sin guardar", JOptionPane.YES_NO_CANCEL_OPTION);
        if (resp == JOptionPane.CANCEL_OPTION || resp == JOptionPane.CLOSED_OPTION) return false;
        if (resp == JOptionPane.YES_OPTION) return guardarArchivo();
        return true;
    }

    private void archivoNuevo() {
        if (!confirmarDescartar()) return;
        areaCodigo.setText("");
        archivoActual = null;
        modificado = false;
        limpiarResultados();
        actualizarTitulo();
        labelEstado.setText("  Nuevo archivo creado.");
    }

    private void abrirArchivo() {
        if (!confirmarDescartar()) return;
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                archivoActual = fc.getSelectedFile();
                areaCodigo.setText(new String(Files.readAllBytes(archivoActual.toPath()), StandardCharsets.UTF_8));
                modificado = false;
                limpiarResultados();
                actualizarTitulo();
                labelEstado.setText("  Archivo abierto: " + archivoActual.getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "No se pudo abrir el archivo:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private boolean guardarArchivo() {
        if (archivoActual == null) return guardarComo();
        try {
            Files.write(archivoActual.toPath(), areaCodigo.getText().getBytes(StandardCharsets.UTF_8));
            modificado = false;
            actualizarTitulo();
            labelEstado.setText("  Guardado: " + archivoActual.getName());
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar el archivo:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private boolean guardarComo() {
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            archivoActual = fc.getSelectedFile();
            return guardarArchivo();
        }
        return false;
    }

    private void salir() {
        if (confirmarDescartar()) System.exit(0);
    }

    // =========================================================================
    // PROCESAMIENTO COMPLETO: LÉXICO -> SINTÁCTICO -> SEMÁNTICO -> INTERMEDIO
    // =========================================================================
    private void procesarCodigo() {
        String codigo = areaCodigo.getText();
        if (codigo.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "El editor está vacío. Escribe código antes de analizar.", "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        limpiarResultados();

        // 1. LÉXICO
        RustLexer lexer = new RustLexer(codigo);
        lexer.analizar();
        List<Token> tokens = lexer.getTokens();
        List<LexicalError> erroresLex = lexer.getErrores();

        DefaultTableModel modeloLexico = (DefaultTableModel) tablaLexico.getModel();
        for (Token t : tokens) {
            String nombreHumano = EstilosCompilador.NOMBRES_TOKEN.getOrDefault(t.tipo, t.tipo);
            modeloLexico.addRow(new Object[]{ t.linea, t.lexema, nombreHumano, t.id });
        }

        StringBuilder sbErrLex = new StringBuilder();
        for (LexicalError e : erroresLex) {
            sbErrLex.append(String.format("Línea %d | Error %d: %-40s | Lexema: '%s'%n",
                e.linea, e.codigoError, e.descripcion, e.lexema));
        }
        areaErroresLexicos.setText(sbErrLex.toString());
        consola.append(String.format("Análisis léxico: %d tokens, %d errores.%n", tokens.size(), erroresLex.size()));

        if (!erroresLex.isEmpty()) {
            consola.append("Análisis sintáctico, semántico y código intermedio omitidos: corrige los errores léxicos primero.\n");
            labelEstado.setText("  Análisis finalizado con " + erroresLex.size() + " error(es) léxicos.");
            labelEstado.setForeground(EstilosCompilador.COLOR_ERROR);
            return;
        }

        // 2. SINTÁCTICO
        RustParser parser = new RustParser(tokens);
        RustParser.ResultadoSintactico rs = parser.parsear();
        DefaultTableModel modeloSintactico = (DefaultTableModel) tablaSintactico.getModel();

        if (rs.esValido) {
            modeloSintactico.addRow(new Object[]{ "-", "Programa principal", "Correcto" });
            consola.append("Análisis sintáctico completado con éxito.\n");

            // 3. SEMÁNTICO
            AnalizadorSemantico semantico = new AnalizadorSemantico();
            AnalizadorSemantico.ResultadoSemantico resSem = semantico.analizar(rs.programa);
            procesarSemantico(resSem);

            // 4. CÓDIGO INTERMEDIO (solo si el semántico no reportó errores)
            if (resSem.errores.isEmpty()) {
                ultimosTokens = tokens;
                ultimoCodigoAnalizado = codigo;
                procesarCodigoIntermedio(rs.programa);
            } else {
                ultimoCodigoIntermedio = null;
                modeloListaBloques.clear();
                ((DefaultTableModel) tablaCuadruplosGlobal.getModel()).setRowCount(0);
                consola.append("Generación de código intermedio omitida: corrige los errores semánticos primero.\n");
            }
        } else {
            StringBuilder sbErrSin = new StringBuilder();
            for (RustParser.SyntaxError se : rs.errores) {
                modeloSintactico.addRow(new Object[]{ se.linea, "Error de sintaxis", "Rechazado" });
                sbErrSin.append("Línea ").append(se.linea).append(": ").append(se.mensaje).append("\n");
            }
            areaErroresSintacticos.setText(sbErrSin.toString());
            consola.append("Análisis sintáctico finalizado con " + rs.errores.size() + " error(es).\n");
            consola.append("Análisis semántico y código intermedio omitidos: corrige la sintaxis primero.\n");
            labelEstado.setText("  Error sintáctico detectado.");
            labelEstado.setForeground(EstilosCompilador.COLOR_ERROR);
        }
    }

    private void procesarSemantico(AnalizadorSemantico.ResultadoSemantico res) {
        DefaultTableModel modeloSemantico = (DefaultTableModel) tablaSemantico.getModel();
        for (AnalizadorSemantico.EstructuraSemantica est : res.estructuras) {
            modeloSemantico.addRow(new Object[]{ est.linea, est.tipoEstructura, est.detalle, est.ambito, est.resultado });
        }

        DefaultTableModel modeloSimbolos = (DefaultTableModel) tablaSimbolos.getModel();
        for (Simbolo s : res.simbolos) {
            modeloSimbolos.addRow(new Object[]{
                s.nombre, s.categoria, s.tipo, s.esMutable ? "Sí" : "No", s.ambito,
                s.lineaDeclaracion, s.inicializado ? "Sí" : "No", s.usos,
                s.direccion >= 0 ? String.format("0x%08X", s.direccion) : "-"
            });
        }

        DefaultTableModel modeloDirecciones = (DefaultTableModel) tablaDirecciones.getModel();
        for (TablaDirecciones.Entrada d : res.direcciones) {
            modeloDirecciones.addRow(new Object[]{
                d.nombre, d.segmento, d.direccionHex(), d.desplazamientoHex(), d.tamano, d.tipo, d.ambito, d.linea
            });
        }

        if (res.errores.isEmpty()) {
            areaErroresSemanticos.setText("");
            consola.append("Análisis semántico completado: consistencia correcta.\n");
            labelEstado.setText("  Léxico, sintáctico y semántico completados con éxito.");
            labelEstado.setForeground(EstilosCompilador.COLOR_EXITO);
        } else {
            StringBuilder sb = new StringBuilder();
            for (AnalizadorSemantico.SemanticError err : res.errores) {
                sb.append("Línea ").append(err.linea).append(": ").append(err.mensaje).append("\n");
            }
            areaErroresSemanticos.setText(sb.toString());
            consola.append(String.format("Análisis semántico finalizado con %d error(es).%n", res.errores.size()));
            labelEstado.setText("  Error semántico detectado.");
            labelEstado.setForeground(EstilosCompilador.COLOR_ERROR);
        }
    }

    private void procesarCodigoIntermedio(AST.Programa programa) {
        GeneradorCodigoIntermedio gen = new GeneradorCodigoIntermedio();
        ultimoCodigoIntermedio = gen.generar(programa);

        modeloListaBloques.clear();
        int n = 1;
        for (BloqueExpresion b : ultimoCodigoIntermedio.bloques) {
            modeloListaBloques.addElement("Tabla " + n + "  (línea " + b.linea + ")  " + b.contexto);
            n++;
        }

        DefaultTableModel modeloGlobal = (DefaultTableModel) tablaCuadruplosGlobal.getModel();
        modeloGlobal.setRowCount(0);
        for (Cuadruplo c : ultimoCodigoIntermedio.cuadruplosGlobales) {
            modeloGlobal.addRow(new Object[]{ c.numero, c.operador, c.arg1, c.arg2 == null ? "" : c.arg2, c.resultado });
        }

        if (!modeloListaBloques.isEmpty()) {
            listaBloquesIntermedio.setSelectedIndex(0);
        }

        consola.append(String.format("Código intermedio generado: %d tabla(s) de operaciones aritméticas, %d cuádruplo(s) en total.%n",
            ultimoCodigoIntermedio.bloques.size(), ultimoCodigoIntermedio.cuadruplosGlobales.size()));
    }

    private void limpiarResultados() {
        ((DefaultTableModel) tablaLexico.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaSintactico.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaSemantico.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaSimbolos.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaDirecciones.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaCuadruplosGlobal.getModel()).setRowCount(0);
        ((DefaultTableModel) tablaCuadruplosBloque.getModel()).setRowCount(0);
        modeloListaBloques.clear();
        areaDetalleBloque.setText("");
        ultimoCodigoIntermedio = null;
        reiniciarPestanaOptimizada();

        areaErroresLexicos.setText("");
        areaErroresSintacticos.setText("");
        areaErroresSemanticos.setText("");
        consola.setText("");

        labelEstado.setText("  Resultados limpios.");
        labelEstado.setForeground(EstilosCompilador.COLOR_TEXTO);
    }

    private void mostrarAcercaDe() {
        JDialog dialog = new JDialog(this, "Acerca de", true);
        dialog.setSize(480, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        panel.setBackground(Color.WHITE);

        JLabel titulo = new JLabel("Compilador Rust");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JLabel subtitulo = new JLabel("Léxico · Sintáctico · Semántico · Código Intermedio");
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitulo.setForeground(EstilosCompilador.COLOR_PRIMARIO);

        panel.add(titulo);
        panel.add(subtitulo);
        panel.add(Box.createRigidArea(new Dimension(0, 15)));

        String[] lineas = {
            "Tecnológico Nacional de México",
            "Instituto Tecnológico de Toluca",
            "Ingeniería en Sistemas Computacionales",
            "Lenguajes y Autómatas II\n",
            "Compilador Rust GUI"
        };
        for (String line : lineas) {
            JLabel lbl = new JLabel(line);
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            panel.add(lbl);
        }

        JButton btnCerrar = new JButton("Cerrar");
        btnCerrar.addActionListener(e -> dialog.dispose());

        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnCerrar, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void configurarAtajos() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F5"), "ejecutarAnalisis");
        getRootPane().getActionMap().put("ejecutarAnalisis", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { procesarCodigo(); }
        });
    }

    private void actualizarTitulo() {
        String nombre = (archivoActual == null) ? "Sin título" : archivoActual.getName();
        String marca = modificado ? " •" : "";
        setTitle(nombre + marca + " - Compilador Rust");
        if (labelArchivo != null) labelArchivo.setText(nombre + marca);
    }

    private void marcarComoModificado() {
        if (!modificado) { modificado = true; actualizarTitulo(); }
    }

    private void actualizarNumeros() {
        int lineas = areaCodigo.getLineCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lineas; i++) sb.append(i).append("\n");
        if (!areaNumerosLinea.getText().equals(sb.toString())) areaNumerosLinea.setText(sb.toString());
    }

    private void actualizarPosicion() {
        try {
            int caretPos = areaCodigo.getCaretPosition();
            int linea = areaCodigo.getLineOfOffset(caretPos) + 1;
            int col = caretPos - areaCodigo.getLineStartOffset(linea - 1) + 1;
            labelPosicion.setText(String.format("Línea %d, Columna %d  ", linea, col));
        } catch (BadLocationException ignored) {}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CompiladorGUI().setVisible(true));
    }
}
