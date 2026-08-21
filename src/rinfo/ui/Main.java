package rinfo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import rinfo.ast.Programa;
import rinfo.lang.ErrorCompilacion;
import rinfo.lang.Parser;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Consola;
import rinfo.runtime.ErrorEjecucion;
import rinfo.runtime.Simulacion;

/**
 * Entorno de r-Info: editor, ciudad, panel de robots y consola.
 *
 * <p>Los hilos de robot no tocan Swing: avisan que hubo un cambio y un
 * temporizador refresca la vista desde el hilo de eventos.
 */
public final class Main extends JFrame {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Ciudad ciudad = new Ciudad();
    private final EditorRInfo editor = new EditorRInfo();
    private final VistaCiudad vistaCiudad = new VistaCiudad(ciudad);
    private final PanelRobots panelRobots = new PanelRobots(ciudad);
    private final JTextArea consola = new JTextArea();
    private final JLabel estado = new JLabel(" ");

    private final AtomicBoolean vistaSucia = new AtomicBoolean(true);
    private final Timer refresco = new Timer(40, e -> refrescarSiHaceFalta());

    private Simulacion simulacion;
    private Path archivoActual;
    private DialogoColocar dialogoColocar;

    private final JButton botonEjecutar = new JButton("Ejecutar");
    private final JButton botonPaso = new JButton("Paso");
    private final JButton botonPausa = new JButton("Pausar");
    private final JButton botonDetener = new JButton("Detener");

    public Main() {
        super("r-Info");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);

        construirInterfaz();
        setJMenuBar(construirMenu());
        editor.setText(PLANTILLA);
        editor.colorear();
        editor.setCaretPosition(0);
        actualizarBotones(false);
        refresco.start();
    }

    // ------------------------------------------------------------------
    // Construcción
    // ------------------------------------------------------------------

    private void construirInterfaz() {
        JScrollPane scrollEditor = new JScrollPane(editor);
        scrollEditor.setRowHeaderView(new NumerosDeLinea(editor));
        scrollEditor.setBorder(BorderFactory.createEmptyBorder());
        scrollEditor.setPreferredSize(new Dimension(430, 0));

        JScrollPane scrollCiudad = new JScrollPane(vistaCiudad);
        scrollCiudad.getVerticalScrollBar().setUnitIncrement(20);
        scrollCiudad.getHorizontalScrollBar().setUnitIncrement(20);

        consola.setEditable(false);
        consola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        consola.setBackground(new Color(0x1E2227));
        consola.setForeground(new Color(0xD8DEE4));
        consola.setCaretColor(Color.WHITE);
        JScrollPane scrollConsola = new JScrollPane(consola);
        scrollConsola.setPreferredSize(new Dimension(0, 150));

        JSplitPane centro = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollCiudad, panelRobots);
        centro.setResizeWeight(0.72);

        JSplitPane conConsola = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centro, scrollConsola);
        conConsola.setResizeWeight(0.78);

        JSplitPane principal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollEditor, conConsola);
        principal.setResizeWeight(0.34);

        estado.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        setLayout(new BorderLayout());
        add(construirBarra(), BorderLayout.NORTH);
        add(principal, BorderLayout.CENTER);
        add(estado, BorderLayout.SOUTH);
    }

    private JToolBar construirBarra() {
        JToolBar barra = new JToolBar();
        barra.setFloatable(false);
        barra.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        JButton compilar = new JButton("Compilar");
        compilar.setToolTipText("Analiza el programa sin ejecutarlo (F9)");
        compilar.addActionListener(e -> compilar());

        botonEjecutar.setToolTipText("Compila y ejecuta el programa (F5)");
        botonEjecutar.addActionListener(e -> ejecutar());
        botonPaso.setToolTipText("Ejecuta una acción por robot (F7)");
        botonPaso.addActionListener(e -> paso());
        botonPausa.addActionListener(e -> alternarPausa());
        botonDetener.addActionListener(e -> detener());

        barra.add(compilar);
        barra.add(botonEjecutar);
        barra.add(botonPaso);
        barra.add(botonPausa);
        barra.add(botonDetener);
        barra.addSeparator();

        barra.add(new JLabel("Velocidad "));
        JSlider velocidad = new JSlider(0, 400, 120);
        velocidad.setInverted(true);
        velocidad.setPreferredSize(new Dimension(140, 24));
        velocidad.setToolTipText("Demora entre acciones");
        velocidad.addChangeListener(e -> {
            if (simulacion != null) {
                simulacion.getControl().setDemoraMs(velocidad.getValue());
            }
        });
        barra.add(velocidad);
        barra.addSeparator();

        barra.add(new JLabel("Colocar "));
        JComboBox<VistaCiudad.Herramienta> herramienta =
                new JComboBox<>(VistaCiudad.Herramienta.values());
        herramienta.setMaximumSize(new Dimension(130, 26));
        herramienta.addActionListener(e ->
                vistaCiudad.setHerramienta((VistaCiudad.Herramienta) herramienta.getSelectedItem()));
        barra.add(herramienta);
        barra.addSeparator();

        barra.add(new JLabel("Zoom "));
        JSlider zoom = new JSlider(10, 60, vistaCiudad.getTamanioBloque());
        zoom.setPreferredSize(new Dimension(110, 24));
        zoom.addChangeListener(e -> vistaCiudad.setTamanioBloque(zoom.getValue()));
        barra.add(zoom);

        barra.add(Box.createHorizontalGlue());
        return barra;
    }

    private JMenuBar construirMenu() {
        JMenuBar menu = new JMenuBar();

        JMenu archivo = new JMenu("Archivo");
        archivo.add(item("Nuevo", KeyEvent.VK_N, this::nuevo));
        archivo.add(item("Abrir…", KeyEvent.VK_O, this::abrir));
        archivo.add(item("Guardar", KeyEvent.VK_S, this::guardar));
        archivo.add(item("Guardar como…", 0, this::guardarComo));
        archivo.addSeparator();
        archivo.add(item("Salir", 0, () -> dispose()));
        menu.add(archivo);

        JMenu ejecucion = new JMenu("Ejecución");
        JMenuItem compilar = new JMenuItem("Compilar");
        compilar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
        compilar.addActionListener(e -> compilar());
        ejecucion.add(compilar);

        JMenuItem correr = new JMenuItem("Ejecutar");
        correr.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        correr.addActionListener(e -> ejecutar());
        ejecucion.add(correr);

        JMenuItem unPaso = new JMenuItem("Paso a paso");
        unPaso.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0));
        unPaso.addActionListener(e -> paso());
        ejecucion.add(unPaso);

        ejecucion.add(item("Pausar / reanudar", 0, this::alternarPausa));
        ejecucion.add(item("Detener", 0, this::detener));
        menu.add(ejecucion);

        JMenu ciudadMenu = new JMenu("Ciudad");
        ciudadMenu.add(item("Colocar…", KeyEvent.VK_L, this::abrirColocar));
        ciudadMenu.add(item("Tamaño…", 0, this::cambiarTamanio));
        ciudadMenu.add(item("Vaciar esquinas", 0, () -> {
            ciudad.limpiarTodo();
            vistaCiudad.repaint();
            panelRobots.refrescar();
            traza("Ciudad vaciada.");
        }));
        menu.add(ciudadMenu);

        JMenu ayuda = new JMenu("Ayuda");
        ayuda.add(item("Referencia del lenguaje", 0, this::mostrarReferencia));
        menu.add(ayuda);

        return menu;
    }

    private JMenuItem item(String texto, int teclaConCtrl, Runnable accion) {
        JMenuItem item = new JMenuItem(texto);
        if (teclaConCtrl != 0) {
            item.setAccelerator(KeyStroke.getKeyStroke(teclaConCtrl,
                    java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        item.addActionListener(e -> accion.run());
        return item;
    }

    // ------------------------------------------------------------------
    // Archivo
    // ------------------------------------------------------------------

    private void nuevo() {
        if (estaCorriendo()) {
            return;
        }
        editor.setText(PLANTILLA);
        editor.colorear();
        archivoActual = null;
        setTitle("r-Info");
        ciudad.limpiarTodo();
        vistaCiudad.repaint();
        panelRobots.refrescar();
    }

    private JFileChooser selector() {
        JFileChooser selector = new JFileChooser(
                archivoActual != null ? archivoActual.getParent().toFile() : new java.io.File("."));
        selector.setFileFilter(new FileNameExtensionFilter("Programas r-Info (*.rinfo, *.txt)",
                "rinfo", "txt"));
        return selector;
    }

    private void abrir() {
        if (estaCorriendo()) {
            return;
        }
        JFileChooser selector = selector();
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path ruta = selector.getSelectedFile().toPath();
        try {
            editor.setText(Files.readString(ruta, StandardCharsets.UTF_8));
            editor.colorear();
            editor.setCaretPosition(0);
            archivoActual = ruta;
            setTitle("r-Info — " + ruta.getFileName());
            traza("Abierto " + ruta);
        } catch (IOException e) {
            error("No se pudo abrir el archivo: " + e.getMessage());
        }
    }

    private void guardar() {
        if (archivoActual == null) {
            guardarComo();
            return;
        }
        try {
            Files.writeString(archivoActual, editor.getText(), StandardCharsets.UTF_8);
            traza("Guardado " + archivoActual);
        } catch (IOException e) {
            error("No se pudo guardar: " + e.getMessage());
        }
    }

    private void guardarComo() {
        JFileChooser selector = selector();
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path ruta = selector.getSelectedFile().toPath();
        if (!ruta.toString().contains(".")) {
            ruta = ruta.resolveSibling(ruta.getFileName() + ".rinfo");
        }
        archivoActual = ruta;
        setTitle("r-Info — " + ruta.getFileName());
        guardar();
    }

    // ------------------------------------------------------------------
    // Compilar y ejecutar
    // ------------------------------------------------------------------

    private Programa compilar() {
        try {
            Programa programa = Parser.compilar(editor.getText().replace("\r\n", "\n"));
            traza("Compilación correcta: programa " + programa.nombre() + ".");
            estado.setText("Compilado sin errores");
            return programa;
        } catch (ErrorCompilacion e) {
            traza("Error de compilación — " + e.getMessage());
            estado.setText("Error de compilación");
            if (e.getFila() > 0) {
                editor.marcarLinea(e.getFila());
            }
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Error de compilación", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void ejecutar() {
        if (estaCorriendo()) {
            simulacion.getControl().reanudar();
            actualizarBotones(true);
            return;
        }
        Programa programa = compilar();
        if (programa == null) {
            return;
        }
        arrancar(programa, false);
    }

    private void paso() {
        if (estaCorriendo()) {
            simulacion.getControl().paso();
            return;
        }
        Programa programa = compilar();
        if (programa == null) {
            return;
        }
        arrancar(programa, true);
        simulacion.getControl().paso();
    }

    private void arrancar(Programa programa, boolean pasoAPaso) {
        consola.setText("");
        ciudad.limpiarEjecucion();

        simulacion = new Simulacion(programa, ciudad, new ConsolaVentana(),
                () -> vistaSucia.set(true));
        if (pasoAPaso) {
            simulacion.getControl().pausar();
        }
        simulacion.setAlTerminar(() -> SwingUtilities.invokeLater(() -> {
            actualizarBotones(false);
            estado.setText("Ejecución terminada");
            vistaSucia.set(true);
        }));

        panelRobots.setSimulacion(simulacion);
        vistaCiudad.setEdicionHabilitada(false);
        actualizarBotones(true);
        estado.setText("Ejecutando…");
        traza("Ejecutando " + programa.nombre() + "…");
        simulacion.iniciar();
    }

    private void alternarPausa() {
        if (!estaCorriendo()) {
            return;
        }
        var control = simulacion.getControl();
        if (control.estaPausado()) {
            control.reanudar();
            botonPausa.setText("Pausar");
            estado.setText("Ejecutando…");
        } else {
            control.pausar();
            botonPausa.setText("Reanudar");
            estado.setText("En pausa");
        }
    }

    private void detener() {
        if (simulacion != null) {
            simulacion.detener();
            traza("Ejecución detenida por el usuario.");
        }
        actualizarBotones(false);
    }

    private boolean estaCorriendo() {
        return simulacion != null && simulacion.estaCorriendo();
    }

    private void actualizarBotones(boolean corriendo) {
        botonEjecutar.setText(corriendo ? "Continuar" : "Ejecutar");
        botonPausa.setEnabled(corriendo);
        botonDetener.setEnabled(corriendo);
        if (!corriendo) {
            botonPausa.setText("Pausar");
            vistaCiudad.setEdicionHabilitada(true);
        }
    }

    // ------------------------------------------------------------------
    // Ciudad
    // ------------------------------------------------------------------

    /** Diálogo para poblar la ciudad sin usar el mouse sobre la grilla. */
    private void abrirColocar() {
        if (estaCorriendo()) {
            error("No se puede editar la ciudad mientras corre un programa.");
            return;
        }
        if (dialogoColocar == null) {
            dialogoColocar = new DialogoColocar(this, ciudad, this::traza, vistaCiudad::repaint);
        }
        dialogoColocar.prepararParaMostrar();
        dialogoColocar.setVisible(true);
        dialogoColocar.toFront();
    }

    private void cambiarTamanio() {
        String respuesta = JOptionPane.showInputDialog(this,
                "Avenidas x calles (máximo " + Ciudad.MAXIMO + " cada una):",
                ciudad.getNumAv() + "x" + ciudad.getNumCa());
        if (respuesta == null) {
            return;
        }
        String[] partes = respuesta.toLowerCase().split("[x, ]+");
        try {
            ciudad.setNumAv(Integer.parseInt(partes[0].trim()));
            ciudad.setNumCa(Integer.parseInt(partes[1].trim()));
            vistaCiudad.revalidate();
            vistaCiudad.repaint();
            if (dialogoColocar != null) {
                dialogoColocar.prepararParaMostrar();
            }
            traza("Ciudad de " + ciudad.getNumAv() + " avenidas por " + ciudad.getNumCa() + " calles.");
        } catch (RuntimeException e) {
            error("Formato inválido. Se espera algo como 20x15.");
        }
    }

    // ------------------------------------------------------------------
    // Refresco y consola
    // ------------------------------------------------------------------

    private void refrescarSiHaceFalta() {
        if (vistaSucia.getAndSet(false)) {
            vistaCiudad.repaint();
            panelRobots.refrescar();
        }
    }

    private void traza(String texto) {
        SwingUtilities.invokeLater(() -> {
            consola.append(LocalTime.now().format(HORA) + "  " + texto + "\n");
            consola.setCaretPosition(consola.getDocument().getLength());
        });
    }

    private void error(String mensaje) {
        traza(mensaje);
        JOptionPane.showMessageDialog(this, mensaje, "r-Info", JOptionPane.ERROR_MESSAGE);
    }

    private void mostrarReferencia() {
        JTextArea texto = new JTextArea(REFERENCIA);
        texto.setEditable(false);
        texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(texto);
        scroll.setPreferredSize(new Dimension(620, 480));
        JOptionPane.showMessageDialog(this, scroll, "Referencia de r-Info",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** Puente entre el programa en ejecución y la ventana. */
    private final class ConsolaVentana implements Consola {

        @Override
        public void informar(String robot, String texto) {
            traza("[" + robot + "] " + texto);
        }

        @Override
        public void traza(String texto) {
            Main.this.traza(texto);
        }

        @Override
        public int leer(String robot, String variable) throws ErrorEjecucion {
            AtomicReference<String> respuesta = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> respuesta.set(JOptionPane.showInputDialog(
                        Main.this, "El robot " + robot + " pide un valor para " + variable + ":",
                        "Leer", JOptionPane.QUESTION_MESSAGE)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ErrorEjecucion("lectura cancelada");
            } catch (InvocationTargetException e) {
                throw new ErrorEjecucion("no se pudo pedir el valor de " + variable);
            }
            String valor = respuesta.get();
            if (valor == null) {
                throw new ErrorEjecucion("lectura cancelada por el usuario");
            }
            try {
                return Integer.parseInt(valor.trim());
            } catch (NumberFormatException e) {
                throw new ErrorEjecucion("'" + valor + "' no es un número válido para " + variable);
            }
        }
    }

    // ------------------------------------------------------------------

    private static final String PLANTILLA = """
            programa MiPrograma
            areas
              zona: AreaP(1, 1, 10, 10)
            robots
              robot obrero
              variables
                pasos: numero
              comenzar
                pasos:= 0
                repetir 4
                  mover
                  pasos:= pasos + 1
                  derecha
                Informar('pasos', pasos)
              fin
            variables
              r1: obrero
            comenzar
              AsignarArea(r1, zona)
              Iniciar(r1, 2, 2)
            fin
            """;

    private static final String REFERENCIA = """
            ESTRUCTURA
              programa <nombre>
              procesos      (opcional)
              areas         (opcional)
              robots        (opcional)
              variables     (opcional)
              comenzar
                AsignarArea(<robot>, <area>)
                Iniciar(<robot>, <avenida>, <calle>)
              fin

            Los bloques se delimitan por indentación: dos espacios por nivel.
            Los comentarios van entre llaves y no alteran la indentación.

            DECLARACIONES
              areas
                <nombre>: AreaC|AreaP|AreaPC(av1, ca1, av2, ca2)
              robots
                robot <tipo>
                variables
                  <nombres>: numero|boolean
                comenzar
                  ...
                fin
              variables
                <nombres>: numero|boolean|<tipo de robot>

            PROCESOS
              proceso <nombre>(E x: numero; S y: numero; ES z: boolean)
              variables
                ...
              comenzar
                ...
              fin
              E copia al entrar, S devuelve al salir, ES hace las dos cosas.

            SENTENCIAS
              <variable>:= <expresión>
              si <condición> / sino
              mientras <condición>
              repetir <cantidad>
              <proceso>(<argumentos>)

            PRIMITIVAS
              mover, derecha
              tomarFlor, tomarPapel, depositarFlor, depositarPapel
              Pos(av, ca), Informar('texto', expr, ...)
              Leer(v), Random(v, desde, hasta)
              EnviarMensaje(expr, robot), RecibirMensaje(v, robot)
              BloquearEsquina(av, ca), LiberarEsquina(av, ca)

            VARIOS ROBOTS
              Cada variable de tipo robot es una instancia y corre en su propio
              hilo: "a, b, c: obrero" crea tres. Cada uno necesita su
              AsignarArea antes del Iniciar, y dos robots no pueden ocupar la
              misma esquina.

            ARMAR LA CIUDAD
              Ciudad -> Colocar... (Ctrl+L) pone flores, papeles y obstáculos
              en una esquina exacta o repartidos al azar en una zona, sin usar
              el mouse sobre la grilla.

            CONSULTAS
              PosAv, PosCa, HayObstaculo
              HayFlorEnLaEsquina, HayFlorEnLaBolsa
              HayPapelEnLaEsquina, HayPapelEnLaBolsa

            OPERADORES
              + - * /            aritméticos
              & | ~              y, o, no
              = <> < > <= >=     relacionales
              V, F               verdadero y falso

              Sólo * y / ligan más que + y -, y ~ liga más que & y |.
              El resto comparte nivel y asocia a izquierda, así que conviene
              parentizar las comparaciones: (i > 3) & (i <= 100)
            """;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
            // el aspecto por defecto sirve igual
        }
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
