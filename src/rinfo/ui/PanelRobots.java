package rinfo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import rinfo.runtime.Buzon;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Entorno;
import rinfo.runtime.Interprete;
import rinfo.runtime.MonitorEsquinas;
import rinfo.runtime.Robot;
import rinfo.runtime.Simulacion;

/**
 * Estado de los robots, sus variables, el buzón de mensajes y las esquinas
 * bloqueadas mientras corre el programa.
 */
public final class PanelRobots extends JPanel {

    private final Ciudad ciudad;
    private final ModeloRobots modeloRobots = new ModeloRobots();
    private final ModeloVariables modeloVariables = new ModeloVariables();
    private final ModeloBuzon modeloBuzon = new ModeloBuzon();
    private final ModeloBloqueos modeloBloqueos = new ModeloBloqueos();
    private final JTable tablaRobots = new JTable(modeloRobots);
    private final JLabel pista;
    private final JLabel estadoBuzon;
    private Simulacion simulacion;

    public PanelRobots(Ciudad ciudad) {
        super(new BorderLayout());
        this.ciudad = ciudad;

        tablaRobots.setRowHeight(22);
        tablaRobots.setFillsViewportHeight(true);
        tablaRobots.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        tablaRobots.getSelectionModel().addListSelectionListener(e -> refrescarDetalleRobot());
        modeloRobots.edicionHabilitada = () -> simulacion != null && !simulacion.estaCorriendo();

        DefaultTableCellRenderer colorNombre = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor,
                    boolean seleccionada, boolean foco, int fila, int columna) {
                Component c = super.getTableCellRendererComponent(
                        tabla, valor, seleccionada, foco, fila, columna);
                List<Robot> robots = modeloRobots.robots;
                if (!seleccionada && fila < robots.size()) {
                    c.setForeground(Tema.actual().visible(robots.get(fila).getColor()));
                }
                return c;
            }
        };
        tablaRobots.getColumnModel().getColumn(0).setCellRenderer(colorNombre);

        DefaultTableCellRenderer colorEstado = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor,
                    boolean seleccionada, boolean foco, int fila, int columna) {
                Component c = super.getTableCellRendererComponent(
                        tabla, valor, seleccionada, foco, fila, columna);
                if (!seleccionada && valor instanceof String texto) {
                    if (texto.contains("interbloqueo") || texto.contains("error")) {
                        c.setForeground(new Color(0xD32F2F));
                    } else if (texto.startsWith("esperando")) {
                        c.setForeground(new Color(0xEF6C00));
                    } else {
                        c.setForeground(tabla.getForeground());
                    }
                }
                return c;
            }
        };
        tablaRobots.getColumnModel().getColumn(ModeloRobots.COL_ESTADO).setCellRenderer(colorEstado);

        JTable tablaVariables = new JTable(modeloVariables);
        tablaVariables.setRowHeight(20);
        tablaVariables.setFillsViewportHeight(true);

        JTable tablaBuzon = new JTable(modeloBuzon);
        tablaBuzon.setRowHeight(20);
        tablaBuzon.setFillsViewportHeight(true);
        tablaBuzon.getColumnModel().getColumn(0).setMaxWidth(36);

        JTable tablaBloqueos = new JTable(modeloBloqueos);
        tablaBloqueos.setRowHeight(20);
        tablaBloqueos.setFillsViewportHeight(true);

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(titulo("Robots"), BorderLayout.NORTH);
        arriba.add(new JScrollPane(tablaRobots), BorderLayout.CENTER);
        JLabel pista = new JLabel("Con el programa compilado y detenido se pueden editar "
                + "«Flores» y «Papeles»: son las bolsas con las que arranca cada robot.");
        pista.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        pista.setFont(pista.getFont().deriveFont(Font.PLAIN, 11f));
        pista.setForeground(Tema.actual().pista);
        this.pista = pista;
        arriba.add(pista, BorderLayout.SOUTH);

        JPanel panelVariables = new JPanel(new BorderLayout());
        panelVariables.add(titulo("Variables del robot seleccionado"), BorderLayout.NORTH);
        panelVariables.add(new JScrollPane(tablaVariables), BorderLayout.CENTER);

        JPanel panelBuzon = new JPanel(new BorderLayout());
        JPanel cabeceraBuzon = new JPanel(new BorderLayout());
        cabeceraBuzon.add(titulo("Buzón de mensajes (cola FIFO)"), BorderLayout.WEST);
        estadoBuzon = new JLabel(" ");
        estadoBuzon.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 8));
        estadoBuzon.setFont(estadoBuzon.getFont().deriveFont(Font.PLAIN, 11f));
        estadoBuzon.setForeground(Tema.actual().pista);
        cabeceraBuzon.add(estadoBuzon, BorderLayout.EAST);
        panelBuzon.add(cabeceraBuzon, BorderLayout.NORTH);
        panelBuzon.add(new JScrollPane(tablaBuzon), BorderLayout.CENTER);

        JPanel panelBloqueos = new JPanel(new BorderLayout());
        panelBloqueos.add(titulo("Esquinas bloqueadas (BloquearEsquina)"), BorderLayout.NORTH);
        panelBloqueos.add(new JScrollPane(tablaBloqueos), BorderLayout.CENTER);

        JSplitPane concurrencia = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, panelBuzon, panelBloqueos);
        concurrencia.setResizeWeight(0.55);
        concurrencia.setBorder(null);

        JSplitPane inspectores = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT, panelVariables, concurrencia);
        inspectores.setResizeWeight(0.42);
        inspectores.setBorder(null);

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, inspectores);
        division.setResizeWeight(0.38);
        division.setBorder(null);
        add(division, BorderLayout.CENTER);
    }

    private static JLabel titulo(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        etiqueta.setFont(etiqueta.getFont().deriveFont(Font.BOLD));
        return etiqueta;
    }

    /** Relee los colores del tema vigente. */
    public void aplicarTema() {
        pista.setForeground(Tema.actual().pista);
        estadoBuzon.setForeground(Tema.actual().pista);
    }

    public void setSimulacion(Simulacion simulacion) {
        this.simulacion = simulacion;
        refrescar();
    }

    /** Vuelve a leer el estado de la ciudad. Se llama desde el hilo de Swing. */
    public void refrescar() {
        if (tablaRobots.isEditing()) {
            return; // no interrumpir mientras el usuario tipea una cantidad
        }
        int seleccionada = tablaRobots.getSelectedRow();
        modeloRobots.robots = ciudad.getRobots();
        modeloRobots.fireTableDataChanged();
        if (seleccionada >= 0 && seleccionada < modeloRobots.robots.size()) {
            tablaRobots.setRowSelectionInterval(seleccionada, seleccionada);
        } else if (!modeloRobots.robots.isEmpty()) {
            tablaRobots.setRowSelectionInterval(0, 0);
        }
        refrescarDetalleRobot();
        modeloBloqueos.cargar(simulacion != null
                ? simulacion.getEsquinas().instantanea()
                : List.of());
    }

    private void refrescarDetalleRobot() {
        int fila = tablaRobots.getSelectedRow();
        List<Robot> robots = modeloRobots.robots;
        if (fila < 0 || fila >= robots.size()) {
            modeloVariables.cargar(Map.of());
            modeloBuzon.cargar(List.of());
            estadoBuzon.setText(" ");
            return;
        }
        Robot robot = robots.get(fila);
        modeloBuzon.cargar(robot.getBuzon().instantanea());
        String esperando = robot.getBuzon().getEsperandoDe();
        if (esperando != null) {
            String quien = Buzon.COMODIN.equals(esperando) ? "cualquiera (*)" : esperando;
            estadoBuzon.setText("Esperando de: " + quien);
        } else {
            int n = robot.getBuzon().cantidadPendientes();
            estadoBuzon.setText(n == 0 ? "Sin mensajes" : n + (n == 1 ? " pendiente" : " pendientes"));
        }

        if (simulacion == null) {
            modeloVariables.cargar(Map.of());
            return;
        }
        Interprete interprete = simulacion.getInterpretes().get(robot);
        if (interprete == null) {
            modeloVariables.cargar(Map.of());
            return;
        }
        Entorno entorno = interprete.getEntornoActual();
        modeloVariables.cargar(entorno.instantanea());
    }

    // ------------------------------------------------------------------

    private static final class ModeloRobots extends AbstractTableModel {
        private static final String[] COLUMNAS =
                {"Robot", "Tipo", "Av", "Ca", "Mira al", "Flores", "Papeles", "Buzón", "Estado"};
        private static final int COL_FLORES = 5;
        private static final int COL_PAPELES = 6;
        private static final int COL_BUZON = 7;
        private static final int COL_ESTADO = 8;

        private List<Robot> robots = List.of();

        /** Las bolsas sólo se pueden tocar con el programa detenido. */
        private BooleanSupplier edicionHabilitada = () -> false;

        @Override
        public int getRowCount() {
            return robots.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Class<?> getColumnClass(int columna) {
            return (columna == COL_FLORES || columna == COL_PAPELES || columna == COL_BUZON)
                    ? Integer.class
                    : String.class;
        }

        @Override
        public boolean isCellEditable(int fila, int columna) {
            return (columna == COL_FLORES || columna == COL_PAPELES) && edicionHabilitada.getAsBoolean();
        }

        @Override
        public void setValueAt(Object valor, int fila, int columna) {
            int cantidad;
            try {
                cantidad = Integer.parseInt(String.valueOf(valor).trim());
            } catch (NumberFormatException e) {
                return;
            }
            Robot r = robots.get(fila);
            if (columna == COL_FLORES) {
                r.setFloresIniciales(cantidad);
            } else {
                r.setPapelesIniciales(cantidad);
            }
            fireTableRowsUpdated(fila, fila);
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            Robot r = robots.get(fila);
            return switch (columna) {
                case 0 -> r.getNombre();
                case 1 -> r.getTipoRobot();
                case 2 -> r.estaIniciado() ? String.valueOf(r.getAv()) : "-";
                case 3 -> r.estaIniciado() ? String.valueOf(r.getCa()) : "-";
                case 4 -> r.estaIniciado() ? r.getDireccion().name().toLowerCase() : "-";
                case COL_FLORES -> r.getFloresEnBolsa();
                case COL_PAPELES -> r.getPapelesEnBolsa();
                case COL_BUZON -> r.getBuzon().cantidadPendientes();
                default -> r.getEstado();
            };
        }
    }

    private static final class ModeloVariables extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Variable", "Valor"};

        private List<String> nombres = List.of();
        private List<String> valores = List.of();

        void cargar(Map<String, Object> instantanea) {
            List<String> n = new ArrayList<>();
            List<String> v = new ArrayList<>();
            instantanea.forEach((nombre, valor) -> {
                n.add(nombre);
                v.add(Interprete.mostrar(valor));
            });
            nombres = n;
            valores = v;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return nombres.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            return columna == 0 ? nombres.get(fila) : valores.get(fila);
        }
    }

    private static final class ModeloBuzon extends AbstractTableModel {
        private static final String[] COLUMNAS = {"#", "De", "Valor"};

        private List<Buzon.Mensaje> mensajes = List.of();

        void cargar(List<Buzon.Mensaje> instantanea) {
            mensajes = instantanea;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return mensajes.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            Buzon.Mensaje m = mensajes.get(fila);
            return switch (columna) {
                case 0 -> fila + 1;
                case 1 -> m.remitente();
                default -> Interprete.mostrar(m.valor());
            };
        }
    }

    private static final class ModeloBloqueos extends AbstractTableModel {
        private static final String[] COLUMNAS = {"Esquina", "Dueño", "En espera"};

        private List<MonitorEsquinas.Bloqueo> bloqueos = List.of();

        void cargar(List<MonitorEsquinas.Bloqueo> instantanea) {
            bloqueos = instantanea;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return bloqueos.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            MonitorEsquinas.Bloqueo b = bloqueos.get(fila);
            return switch (columna) {
                case 0 -> "(av " + b.av() + ", ca " + b.ca() + ")";
                case 1 -> b.duenio().getNombre();
                default -> {
                    if (b.esperando().isEmpty()) {
                        yield "-";
                    }
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < b.esperando().size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(b.esperando().get(i).getNombre());
                    }
                    yield sb.toString();
                }
            };
        }
    }
}
