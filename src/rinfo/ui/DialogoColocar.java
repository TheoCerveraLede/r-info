package rinfo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.SpinnerNumberModel;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Contenido;

/**
 * Diálogo para poblar la ciudad sin usar el mouse sobre la grilla.
 *
 * <p>Tiene dos formas de trabajo: una esquina puntual, y una zona rectangular
 * en la que el contenido se puede poner en todas las esquinas o repartir al
 * azar. Es no modal a propósito: se deja abierto mientras se arma el escenario
 * y cada acción refresca la vista.
 */
public final class DialogoColocar extends JDialog {

    private final Ciudad ciudad;
    private final Consumer<String> informar;
    private final Runnable alCambiar;

    // Esquina puntual
    private final JComboBox<Contenido> queEsquina = new JComboBox<>(Contenido.values());
    private final JSpinner cantidadEsquina = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final JSpinner avenida = new JSpinner();
    private final JSpinner calle = new JSpinner();

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
        rango(avenida, maxAv, valorActual(avenida, maxAv, 1));
        rango(calle, maxCa, valorActual(calle, maxCa, 1));
        rango(desdeAv, maxAv, valorActual(desdeAv, maxAv, 1));
        rango(desdeCa, maxCa, valorActual(desdeCa, maxCa, 1));
        rango(hastaAv, maxAv, valorActual(hastaAv, maxAv, maxAv));
        rango(hastaCa, maxCa, valorActual(hastaCa, maxCa, maxCa));
        actualizarResumen();
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

        cantidadEsquina.setPreferredSize(new Dimension(64, cantidadEsquina.getPreferredSize().height));
        queEsquina.addActionListener(e -> cantidadEsquina.setEnabled(
                queEsquina.getSelectedItem() != Contenido.OBSTACULO));
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
        enCadaEsquina.addActionListener(e -> actualizarResumen());
        alAzar.addActionListener(e -> actualizarResumen());
        queZona.addActionListener(e -> actualizarResumen());
        cantidadZona.addChangeListener(e -> actualizarResumen());
        for (JSpinner s : new JSpinner[] {desdeAv, desdeCa, hastaAv, hastaCa}) {
            s.addChangeListener(e -> actualizarResumen());
        }
        actualizarResumen();
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

    /** Anticipa cuántas unidades va a poner el modo elegido. */
    private void actualizarResumen() {
        sinPisar.setEnabled(alAzar.isSelected());
        Contenido que = (Contenido) queZona.getSelectedItem();
        if (que == null) {
            return;
        }
        int cantidad = entero(cantidadZona);
        int esquinas = (Math.abs(entero(hastaAv) - entero(desdeAv)) + 1)
                * (Math.abs(entero(hastaCa) - entero(desdeCa)) + 1);

        if (enCadaEsquina.isSelected()) {
            int total = (que == Contenido.OBSTACULO ? 1 : cantidad) * esquinas;
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
        int av = entero(avenida);
        int ca = entero(calle);
        try {
            ciudad.colocar(que, av, ca, cantidad);
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Colocar", JOptionPane.WARNING_MESSAGE);
            return;
        }
        informar.accept(cantidad + " " + (cantidad == 1 ? que.singular : que.plural)
                + " en (av " + av + ", ca " + ca + ").");
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
                int pedidas = entero(cantidadZona);
                int colocadas = ciudad.colocarAlAzar(que, pedidas, av1, ca1, av2, ca2,
                        sinPisar.isSelected());
                String mensaje = colocadas + " " + (colocadas == 1 ? que.singular : que.plural)
                        + " al azar entre " + zona + ".";
                if (colocadas < pedidas) {
                    mensaje += " No entraron las " + pedidas
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
