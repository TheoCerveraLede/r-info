package rinfo.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Paleta de la interfaz, en versión clara y oscura.
 *
 * <p>Todo lo que la aplicación dibuja por su cuenta —la ciudad, el editor, la
 * regla de líneas y la consola— sale de acá. Los componentes estándar de Swing
 * los resuelve el look and feel: el claro usa el del sistema y el oscuro usa
 * Nimbus, que es el único incluido en el JDK cuya paleta se puede reemplazar
 * entera.
 */
public final class Tema {

    public enum Modo { CLARO, OSCURO }

    private static final Preferences PREFS = Preferences.userNodeForPackage(Tema.class);
    private static final String CLAVE_MODO = "modo";

    private static final List<Runnable> oyentes = new ArrayList<>();
    private static Tema actual;

    public final Modo modo;

    // Ciudad
    public final Color fondoCiudad;
    public final Color manzana;
    public final Color calle;
    public final Color flor;
    public final Color papel;
    public final Color obstaculo;
    public final Color numeracion;
    public final Color contornoRobot;

    /** Opacidad del relleno del área, de su borde y del rastro del robot. */
    public final int alfaArea;
    public final int alfaBordeArea;
    public final int alfaRuta;

    // Editor
    public final Color fondoEditor;
    public final Color textoEditor;
    public final Color seleccionEditor;

    // Coloreado de sintaxis
    public final Color estructura;
    public final Color control;
    public final Color primitiva;
    public final Color consulta;
    public final Color tipoDato;
    public final Color numeroLiteral;
    public final Color comentario;
    public final Color textoLiteral;

    // Regla de líneas
    public final Color gutterFondo;
    public final Color gutterBorde;
    public final Color gutterTexto;

    // Consola y textos secundarios
    public final Color consolaFondo;
    public final Color consolaTexto;
    public final Color pista;

    private Tema(Modo modo, Color[] c, int[] alfas) {
        this.modo = modo;
        this.fondoCiudad = c[0];
        this.manzana = c[1];
        this.calle = c[2];
        this.flor = c[3];
        this.papel = c[4];
        this.obstaculo = c[5];
        this.numeracion = c[6];
        this.contornoRobot = c[7];
        this.fondoEditor = c[8];
        this.textoEditor = c[9];
        this.seleccionEditor = c[10];
        this.estructura = c[11];
        this.control = c[12];
        this.primitiva = c[13];
        this.consulta = c[14];
        this.tipoDato = c[15];
        this.numeroLiteral = c[16];
        this.comentario = c[17];
        this.textoLiteral = c[18];
        this.gutterFondo = c[19];
        this.gutterBorde = c[20];
        this.gutterTexto = c[21];
        this.consolaFondo = c[22];
        this.consolaTexto = c[23];
        this.pista = c[24];
        this.alfaArea = alfas[0];
        this.alfaBordeArea = alfas[1];
        this.alfaRuta = alfas[2];
    }

    private static final Tema CLARO = new Tema(Modo.CLARO, new Color[] {
        new Color(0xF7F7F4), new Color(0xE3E6E0), new Color(0xB9BDB6), new Color(0xD81B60),
        new Color(0x1E88E5), new Color(0x37474F), new Color(0x707870), Color.WHITE,
        Color.WHITE, new Color(0x22282A), new Color(0xB4D5F5),
        new Color(0x7B1FA2), new Color(0x0D47A1), new Color(0x00695C), new Color(0xC2185B),
        new Color(0xE65100), new Color(0x1565C0), new Color(0x7A8B7A), new Color(0x2E7D32),
        new Color(0xF0F0EC), new Color(0xD5D8D2), new Color(0x98A098),
        new Color(0x1E2227), new Color(0xD8DEE4), new Color(0x60686A),
    }, new int[] {46, 150, 70});

    private static final Tema OSCURO = new Tema(Modo.OSCURO, new Color[] {
        new Color(0x22262B), new Color(0x2E3339), new Color(0x434A52), new Color(0xF06292),
        new Color(0x64B5F6), new Color(0x93A1AE), new Color(0x78828C), new Color(0x14171A),
        new Color(0x1E2227), new Color(0xD8DEE4), new Color(0x2D5C88),
        new Color(0xCE93D8), new Color(0x82AAFF), new Color(0x4DB6AC), new Color(0xF48FB1),
        new Color(0xFFB74D), new Color(0x90CAF9), new Color(0x7E8C7E), new Color(0xA5D6A7),
        new Color(0x22262B), new Color(0x33383E), new Color(0x6B747C),
        new Color(0x181B1F), new Color(0xD8DEE4), new Color(0x8A939A),
    }, new int[] {64, 170, 90});

    // ------------------------------------------------------------------

    public static Tema actual() {
        if (actual == null) {
            actual = "OSCURO".equals(PREFS.get(CLAVE_MODO, "CLARO")) ? OSCURO : CLARO;
        }
        return actual;
    }

    public static boolean esOscuro() {
        return actual().modo == Modo.OSCURO;
    }

    /** Cambia el tema, lo recuerda para la próxima vez y avisa a los oyentes. */
    public static void cambiar(Modo modo) {
        actual = modo == Modo.OSCURO ? OSCURO : CLARO;
        PREFS.put(CLAVE_MODO, modo.name());
        instalarLookAndFeel();
        for (Runnable oyente : oyentes) {
            oyente.run();
        }
    }

    /** Registra algo para que se entere cuando cambie el tema. */
    public static void alCambiar(Runnable oyente) {
        oyentes.add(oyente);
    }

    /**
     * Instala el look and feel que corresponde al tema actual.
     *
     * <p>Nimbus arma toda su paleta a partir de un puñado de claves, así que
     * hay que escribirlas antes de instalarlo.
     */
    public static void instalarLookAndFeel() {
        try {
            if (esOscuro()) {
                UIManager.put("control", new Color(0x3C3F41));
                UIManager.put("info", new Color(0x3C3F41));
                UIManager.put("text", new Color(0xD8DEE4));
                UIManager.put("nimbusBase", new Color(0x2B2B2B));
                UIManager.put("nimbusLightBackground", new Color(0x2B2B2B));
                UIManager.put("nimbusDisabledText", new Color(0x7A7A7A));
                UIManager.put("nimbusFocus", new Color(0x4A88C7));
                UIManager.put("nimbusSelectionBackground", new Color(0x2D5C88));
                UIManager.put("nimbusSelectedText", Color.WHITE);
                UIManager.put("nimbusBlueGrey", new Color(0x3A3D3F));
                UIManager.put("nimbusBorder", new Color(0x4A4E52));
                UIManager.put("nimbusInfoBlue", new Color(0x2D5C88));
                UIManager.put("nimbusRed", new Color(0xC75450));
                UIManager.put("nimbusGreen", new Color(0x5A8C3E));
                UIManager.put("nimbusOrange", new Color(0xC8794A));
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } else {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // el aspecto por defecto sirve igual
        }
    }

    // ------------------------------------------------------------------

    /**
     * Aclara un color pensado para fondo claro, para que se lea sobre uno
     * oscuro. Los colores de los robots y de las áreas están elegidos contra
     * papel blanco: sobre el fondo oscuro varios quedarían casi invisibles.
     */
    public Color visible(Color color) {
        return modo == Modo.CLARO ? color : mezclar(color, Color.WHITE, 0.45f);
    }

    private static Color mezclar(Color a, Color b, float proporcionDeB) {
        float p = proporcionDeB;
        return new Color(
                Math.round(a.getRed() * (1 - p) + b.getRed() * p),
                Math.round(a.getGreen() * (1 - p) + b.getGreen() * p),
                Math.round(a.getBlue() * (1 - p) + b.getBlue() * p));
    }

    /** El mismo color con otra opacidad. */
    public static Color conAlfa(Color color, int alfa) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alfa);
    }
}
