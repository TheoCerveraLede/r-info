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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Contenido;

/**
 * Diálogo para poblar la ciudad sin usar el mouse sobre la grilla.
 *
 * <p>Permite colocar flores, papeles y obstáculos en una esquina concreta,
 * repartirlos al azar dentro de un rectángulo y vaciar una zona. Es no modal
 * a propósito: se deja abierto mientras se arma el escenario y cada acción
 * refresca la vista.
 */
public final class DialogoColocar extends JDialog {

    private final Ciudad ciudad;
    private final Consumer<String> informar;
    private final Runnable alCambiar;

    private final JComboBox<Contenido> queExacto = new JComboBox<>(Contenido.values());
    private final JSpinner avenida = new JSpinner();
    private final JSpinner calle = new JSpinner();
    private final JSpinner cantidadExacta = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));

    private final JComboBox<Contenido> queAzar = new JComboBox<>(Contenido.values());
    private final JSpinner cantidadAzar = new JSpinner(new SpinnerNumberModel(10, 1, 9999, 1));
    private final JSpinner desdeAv = new JSpinner();
    private final JSpinner desdeCa = new JSpinner();
    private final JSpinner hastaAv = new JSpinner();
    private final JSpinner hastaCa = new JSpinner();
    private final JCheckBox unaPorEsquina = new JCheckBox("Una por esquina, sin pisar lo que ya hay", true);

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
        contenido.add(panelAzar());
        contenido.add(Box.createVerticalStrut(10));
        contenido.add(panelVaciar());

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
        rango(avenida, 1, maxAv, valor(avenida, 1, maxAv));
        rango(calle, 1, maxCa, valor(calle, 1, maxCa));
        rango(desdeAv, 1, maxAv, 1);
        rango(desdeCa, 1, maxCa, 1);
        rango(hastaAv, 1, maxAv, maxAv);
        rango(hastaCa, 1, maxCa, maxCa);
    }

    private static void rango(JSpinner spinner, int minimo, int maximo, int valor) {
        spinner.setModel(new SpinnerNumberModel(Math.clamp(valor, minimo, maximo),
                minimo, maximo, 1));
        spinner.setPreferredSize(new Dimension(64, spinner.getPreferredSize().height));
    }

    private static int valor(JSpinner spinner, int minimo, int maximo) {
        Object v = spinner.getValue();
        return v instanceof Integer i ? Math.clamp(i, minimo, maximo) : minimo;
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
        panel.add(queExacto, c);
        c.gridx = 2;
        panel.add(new JLabel("Cantidad:"), c);
        c.gridx = 3;
        panel.add(cantidadExacta, c);

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

        cantidadExacta.setPreferredSize(new Dimension(64, cantidadExacta.getPreferredSize().height));
        queExacto.addActionListener(e -> cantidadExacta.setEnabled(
                queExacto.getSelectedItem() != Contenido.OBSTACULO));
        return panel;
    }

    private JPanel panelAzar() {
        JPanel panel = conTitulo("Al azar");
        GridBagConstraints c = restricciones();

        panel.add(new JLabel("Qué:"), c);
        c.gridx = 1;
        panel.add(queAzar, c);
        c.gridx = 2;
        panel.add(new JLabel("Cantidad:"), c);
        c.gridx = 3;
        panel.add(cantidadAzar, c);

        c.gridx = 0;
        c.gridy = 1;
        panel.add(new JLabel("Desde av/ca:"), c);
        c.gridx = 1;
        panel.add(desdeAv, c);
        c.gridx = 2;
        panel.add(desdeCa, c);

        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Hasta av/ca:"), c);
        c.gridx = 1;
        panel.add(hastaAv, c);
        c.gridx = 2;
        panel.add(hastaCa, c);

        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 4;
        panel.add(unaPorEsquina, c);

        JButton repartir = new JButton("Repartir");
        repartir.addActionListener(e -> repartirAlAzar());
        c.gridx = 4;
        c.gridy = 0;
        c.gridwidth = 1;
        c.gridheight = 4;
        panel.add(repartir, c);

        cantidadAzar.setPreferredSize(new Dimension(64, cantidadAzar.getPreferredSize().height));
        return panel;
    }

    private JPanel panelVaciar() {
        JPanel panel = conTitulo("Vaciar");
        GridBagConstraints c = restricciones();
        c.gridwidth = 4;
        panel.add(new JLabel("Deja sin contenido la zona elegida arriba en «Al azar»."), c);

        JButton vaciar = new JButton("Vaciar zona");
        vaciar.addActionListener(e -> vaciarZona());
        c.gridx = 4;
        c.gridwidth = 1;
        panel.add(vaciar, c);
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

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    private void colocarEnEsquina() {
        Contenido que = (Contenido) queExacto.getSelectedItem();
        int cantidad = que == Contenido.OBSTACULO ? 1 : entero(cantidadExacta);
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

    private void repartirAlAzar() {
        Contenido que = (Contenido) queAzar.getSelectedItem();
        int pedidas = entero(cantidadAzar);
        int colocadas;
        try {
            colocadas = ciudad.colocarAlAzar(que, pedidas,
                    entero(desdeAv), entero(desdeCa), entero(hastaAv), entero(hastaCa),
                    unaPorEsquina.isSelected());
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Repartir", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String mensaje = colocadas + " " + (colocadas == 1 ? que.singular : que.plural)
                + " al azar entre (av " + entero(desdeAv) + ", ca " + entero(desdeCa)
                + ") y (av " + entero(hastaAv) + ", ca " + entero(hastaCa) + ").";
        if (colocadas < pedidas) {
            mensaje += " No entraron las " + pedidas + " pedidas: no quedaban esquinas libres.";
        }
        informar.accept(mensaje);
        alCambiar.run();
    }

    private void vaciarZona() {
        ciudad.vaciar(entero(desdeAv), entero(desdeCa), entero(hastaAv), entero(hastaCa));
        informar.accept("Zona vaciada entre (av " + entero(desdeAv) + ", ca " + entero(desdeCa)
                + ") y (av " + entero(hastaAv) + ", ca " + entero(hastaCa) + ").");
        alCambiar.run();
    }
}
