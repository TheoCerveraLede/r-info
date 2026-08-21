package rinfo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Contenido;

/**
 * Diálogo para poblar la ciudad sin usar el mouse sobre la grilla.
 *
 * <p>Dos formas de trabajo: una esquina, donde la avenida y la calle aceptan
 * {@value #COMODIN} para que esa coordenada salga al azar, y una zona
 * rectangular en la que el contenido se puede poner en todas las esquinas o
 * repartir al azar. Es no modal a propósito: se deja abierto mientras se arma
 * el escenario y cada acción refresca la vista.
 */
public final class DialogoColocar extends JDialog {

    /** En una coordenada, pide que la posición se sortee. */
    public static final String COMODIN = "*";

    /** Valor interno de una coordenada escrita como {@value #COMODIN}. */
    private static final int AL_AZAR = -1;

    private final Ciudad ciudad;
    private final Consumer<String> informar;
    private final Runnable alCambiar;

    // Esquina
    private final JComboBox<Contenido> queEsquina = new JComboBox<>(Contenido.values());
    private final JSpinner cantidadEsquina = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
    private final JTextField avenida = new JTextField("1", 4);
    private final JTextField calle = new JTextField("1", 4);
    private final JLabel resumenEsquina = new JLabel(" ");

    // Zona
    private final JSpinner desdeAv = new JSpinner();
    private final JSpinner desdeCa = new JSpinner();
    private final JSpinner hastaAv = new JSpinner();
    private final JSpinner hastaCa = new JSpinner();
    private final JComboBox<Contenido> queZona = new JComboBox<>(Contenido.values());
    private final JSpinner cantidadZona = new JSpinner(new SpinnerNumberModel(1, 1, 9999, 1));
    private final JRadioButton enCadaEsquina = new JRadioButton("en cada esquina de la zona", true);
    private final JRadioButton alAzar = new JRadioButton("repartidos al azar en la zona");
    private final JCheckBox sinPisar = new JCheckBox("una por esquina, sin pisar lo que ya hay", true);
    private final JLabel resumenZona = new JLabel(" ");

    public DialogoColocar(Frame duenio, Ciudad ciudad, Consumer<String> informar, Runnable alCambiar) {
        super(duenio, "Colocar en la ciudad", false);
        this.ciudad = ciudad;
        this.informar = informar;
        this.alCambiar = alCambiar;

        JPanel contenido = new JPanel();
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        contenido.add(panelEsquina());
        contenido.add(Box.createVerticalStrut(10));
        contenido.add(panelZona());

        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> setVisible(false));
        pie.add(cerrar);

        setLayout(new BorderLayout());
        add(contenido, BorderLayout.CENTER);
        add(pie, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(duenio);
    }

    /** Reajusta los topes de los spinners al tamaño actual de la ciudad. */
    public void prepararParaMostrar() {
        int maxAv = ciudad.getNumAv();
        int maxCa = ciudad.getNumCa();
        rango(desdeAv, maxAv, valorActual(desdeAv, maxAv, 1));
        rango(desdeCa, maxCa, valorActual(desdeCa, maxCa, 1));
        rango(hastaAv, maxAv, valorActual(hastaAv, maxAv, maxAv));
        rango(hastaCa, maxCa, valorActual(hastaCa, maxCa, maxCa));
        actualizarResumenEsquina();
        actualizarResumenZona();
    }

    private static void rango(JSpinner spinner, int maximo, int valor) {
        spinner.setModel(new SpinnerNumberModel(Math.clamp(valor, 1, maximo), 1, maximo, 1));
        spinner.setPreferredSize(new Dimension(64, spinner.getPreferredSize().height));
    }

    private static int valorActual(JSpinner spinner, int maximo, int porOmision) {
        Object v = spinner.getValue();
        return v instanceof Integer i ? Math.clamp(i, 1, maximo) : porOmision;
    }

    private static int entero(JSpinner spinner) {
        return (Integer) spinner.getValue();
    }

    /**
     * Lee una coordenada escrita a mano.
     *
     * @return el número, o {@link #AL_AZAR} si el campo tiene el comodín
     * @throws IllegalArgumentException si el texto no es ninguna de las dos cosas
     */
    private static int coordenada(JTextField campo, int maximo, String nombre) {
        String texto = campo.getText().trim();
        if (texto.equals(COMODIN)) {
            return AL_AZAR;
        }
        try {
            int valor = Integer.parseInt(texto);
            if (valor < 1 || valor > maximo) {
                throw new NumberFormatException();
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("La " + nombre + " tiene que ser un número entre 1 y "
                    + maximo + ", o " + COMODIN + " para que salga al azar.");
        }
    }

    // ------------------------------------------------------------------
    // Paneles
    // ------------------------------------------------------------------

    private JPanel panelEsquina() {
        JPanel panel = conTitulo("En una esquina");
        GridBagConstraints c = restricciones();

        panel.add(new JLabel("Qué:"), c);
        c.gridx = 1;
        panel.add(queEsquina, c);
        c.gridx = 2;
        panel.add(new JLabel("Cantidad:"), c);
        c.gridx = 3;
        panel.add(cantidadEsquina, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Avenida:"), c);
        c.gridx = 1;
        panel.add(avenida, c);
        c.gridx = 2;
        panel.add(new JLabel("Calle:"), c);
        c.gridx = 3;
        panel.add(calle, c);

        JButton colocar = new JButton("Colocar");
        colocar.addActionListener(e -> colocarEnEsquina());
        c.gridx = 4;
        c.gridy = 0;
        c.gridheight = 2;
        panel.add(colocar, c);

        c.gridx = 0;
        c.gridy = 2;
        c.gridheight = 1;
        c.gridwidth = 5;
        panel.add(enChico("Escribí " + COMODIN + " en la avenida o en la calle para que esa "
                + "coordenada salga al azar."), c);
        c.gridy = 3;
        panel.add(resumenEsquina, c);

        cantidadEsquina.setPreferredSize(new Dimension(64, cantidadEsquina.getPreferredSize().height));
        String pista = "Un número, o " + COMODIN + " para que salga al azar";
        avenida.setToolTipText(pista);
        calle.setToolTipText(pista);

        queEsquina.addActionListener(e -> actualizarResumenEsquina());
        cantidadEsquina.addChangeListener(e -> actualizarResumenEsquina());
        alEscribir(avenida, this::actualizarResumenEsquina);
        alEscribir(calle, this::actualizarResumenEsquina);
        actualizarResumenEsquina();
        return panel;
    }

    private JPanel panelZona() {
        JPanel panel = conTitulo("En una zona");
        GridBagConstraints c = restricciones();

        panel.add(new JLabel("Desde av/ca:"), c);
        c.gridx = 1;
        panel.add(desdeAv, c);
        c.gridx = 2;
        panel.add(desdeCa, c);
        c.gridx = 3;
        panel.add(new JLabel("Hasta av/ca:"), c);
        c.gridx = 4;
        panel.add(hastaAv, c);
        c.gridx = 5;
        panel.add(hastaCa, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Qué:"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        panel.add(queZona, c);
        c.gridx = 3;
        c.gridwidth = 1;
        panel.add(new JLabel("Cantidad:"), c);
        c.gridx = 4;
        panel.add(cantidadZona, c);

        ButtonGroup modo = new ButtonGroup();
        modo.add(enCadaEsquina);
        modo.add(alAzar);
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 6;
        panel.add(enCadaEsquina, c);
        c.gridy = 3;
        panel.add(alAzar, c);

        c.gridy = 4;
        c.insets = new Insets(0, 26, 3, 4);
        panel.add(sinPisar, c);

        c.gridy = 5;
        c.insets = new Insets(6, 4, 3, 4);
        panel.add(resumenZona, c);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton colocar = new JButton("Colocar en la zona");
        colocar.addActionListener(e -> colocarEnZona());
        JButton vaciar = new JButton("Vaciar zona");
        vaciar.addActionListener(e -> vaciarZona());
        botones.add(colocar);
        botones.add(vaciar);
        c.gridy = 6;
        panel.add(botones, c);

        cantidadZona.setPreferredSize(new Dimension(64, cantidadZona.getPreferredSize().height));
        enCadaEsquina.addActionListener(e -> actualizarResumenZona());
        alAzar.addActionListener(e -> actualizarResumenZona());
        queZona.addActionListener(e -> actualizarResumenZona());
        cantidadZona.addChangeListener(e -> actualizarResumenZona());
        for (JSpinner s : new JSpinner[] {desdeAv, desdeCa, hastaAv, hastaCa}) {
            s.addChangeListener(e -> actualizarResumenZona());
        }
        actualizarResumenZona();
        return panel;
    }

    private static JPanel conTitulo(String titulo) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(titulo));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static GridBagConstraints restricciones() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        return c;
    }

    private static JLabel enChico(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.PLAIN, 11f));
        etiqueta.setForeground(new java.awt.Color(0x60686A));
        return etiqueta;
    }

    private static void alEscribir(JTextField campo, Runnable accion) {
        campo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                accion.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                accion.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                accion.run();
            }
        });
    }

    // ------------------------------------------------------------------
    // Resúmenes: anticipan lo que va a hacer cada botón
    // ------------------------------------------------------------------

    private void actualizarResumenEsquina() {
        Contenido que = (Contenido) queEsquina.getSelectedItem();
        if (que == null) {
            return;
        }
        cantidadEsquina.setEnabled(que != Contenido.OBSTACULO);
        int cantidad = que == Contenido.OBSTACULO ? 1 : entero(cantidadEsquina);
        String plural = cantidad == 1 ? que.singular : que.plural;

        int av;
        int ca;
        try {
            av = coordenada(avenida, ciudad.getNumAv(), "avenida");
            ca = coordenada(calle, ciudad.getNumCa(), "calle");
        } catch (IllegalArgumentException e) {
            resumenEsquina.setText(" ");
            return;
        }
        if (av != AL_AZAR && ca != AL_AZAR) {
            resumenEsquina.setText(cantidad + " " + plural + " en (av " + av + ", ca " + ca + ").");
        } else {
            resumenEsquina.setText(cantidad + " " + plural + " en "
                    + (cantidad == 1 ? "una esquina" : cantidad + " esquinas distintas")
                    + " al azar " + dondeAlAzar(av, ca) + ".");
        }
    }

    private String dondeAlAzar(int av, int ca) {
        if (av == AL_AZAR && ca == AL_AZAR) {
            return "de toda la ciudad";
        }
        return av == AL_AZAR ? "de la calle " + ca : "de la avenida " + av;
    }

    private void actualizarResumenZona() {
        sinPisar.setEnabled(alAzar.isSelected());
        Contenido que = (Contenido) queZona.getSelectedItem();
        if (que == null) {
            return;
        }
        cantidadZona.setEnabled(que != Contenido.OBSTACULO);
        int cantidad = que == Contenido.OBSTACULO ? 1 : entero(cantidadZona);
        int esquinas = (Math.abs(entero(hastaAv) - entero(desdeAv)) + 1)
                * (Math.abs(entero(hastaCa) - entero(desdeCa)) + 1);

        if (enCadaEsquina.isSelected()) {
            int total = cantidad * esquinas;
            resumenZona.setText("La zona tiene " + esquinas + " esquinas: van a quedar "
                    + total + " " + (total == 1 ? que.singular : que.plural) + " en total.");
        } else {
            resumenZona.setText("Se reparten " + cantidad + " "
                    + (cantidad == 1 ? que.singular : que.plural)
                    + " entre las " + esquinas + " esquinas de la zona.");
        }
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void colocarEnEsquina() {
        Contenido que = (Contenido) queEsquina.getSelectedItem();
        int cantidad = que == Contenido.OBSTACULO ? 1 : entero(cantidadEsquina);
        int av;
        int ca;
        try {
            av = coordenada(avenida, ciudad.getNumAv(), "avenida");
            ca = coordenada(calle, ciudad.getNumCa(), "calle");
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Colocar", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (av != AL_AZAR && ca != AL_AZAR) {
            ciudad.colocar(que, av, ca, cantidad);
            informar.accept(cantidad + " " + (cantidad == 1 ? que.singular : que.plural)
                    + " en (av " + av + ", ca " + ca + ").");
        } else {
            // El comodín fija la coordenada conocida y sortea la otra.
            int av1 = av == AL_AZAR ? 1 : av;
            int av2 = av == AL_AZAR ? ciudad.getNumAv() : av;
            int ca1 = ca == AL_AZAR ? 1 : ca;
            int ca2 = ca == AL_AZAR ? ciudad.getNumCa() : ca;
            int colocadas = ciudad.colocarAlAzar(que, cantidad, av1, ca1, av2, ca2, true);

            String mensaje = colocadas + " " + (colocadas == 1 ? que.singular : que.plural)
                    + " al azar " + dondeAlAzar(av, ca) + ".";
            if (colocadas < cantidad) {
                mensaje += " No entraron " + cantidad + ": no quedaban esquinas libres.";
            }
            informar.accept(mensaje);
        }
        alCambiar.run();
    }

    private void colocarEnZona() {
        Contenido que = (Contenido) queZona.getSelectedItem();
        int cantidad = que == Contenido.OBSTACULO ? 1 : entero(cantidadZona);
        int av1 = entero(desdeAv);
        int ca1 = entero(desdeCa);
        int av2 = entero(hastaAv);
        int ca2 = entero(hastaCa);
        String zona = "(av " + av1 + ", ca " + ca1 + ") y (av " + av2 + ", ca " + ca2 + ")";

        try {
            if (enCadaEsquina.isSelected()) {
                int esquinas = ciudad.rellenar(que, cantidad, av1, ca1, av2, ca2);
                informar.accept(cantidad + " " + (cantidad == 1 ? que.singular : que.plural)
                        + " en cada una de las " + esquinas + " esquinas entre " + zona + ".");
            } else {
                int colocadas = ciudad.colocarAlAzar(que, cantidad, av1, ca1, av2, ca2,
                        sinPisar.isSelected());
                String mensaje = colocadas + " " + (colocadas == 1 ? que.singular : que.plural)
                        + " al azar entre " + zona + ".";
                if (colocadas < cantidad) {
                    mensaje += " No entraron las " + cantidad
                            + " pedidas: no quedaban esquinas libres.";
                }
                informar.accept(mensaje);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Colocar en la zona",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        alCambiar.run();
    }

    private void vaciarZona() {
        ciudad.vaciar(entero(desdeAv), entero(desdeCa), entero(hastaAv), entero(hastaCa));
        informar.accept("Zona vaciada entre (av " + entero(desdeAv) + ", ca " + entero(desdeCa)
                + ") y (av " + entero(hastaAv) + ", ca " + entero(hastaCa) + ").");
        alCambiar.run();
    }
}
