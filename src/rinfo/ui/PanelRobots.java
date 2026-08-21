package rinfo.ui;

import java.awt.BorderLayout;
import java.awt.Component;
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
import rinfo.runtime.Ciudad;
import rinfo.runtime.Entorno;
import rinfo.runtime.Interprete;
import rinfo.runtime.Robot;
import rinfo.runtime.Simulacion;

/** Estado de los robots y de sus variables mientras corre el programa. */
public final class PanelRobots extends JPanel {

    private final Ciudad ciudad;
    private final ModeloRobots modeloRobots = new ModeloRobots();
    private final ModeloVariables modeloVariables = new ModeloVariables();
    private final JTable tablaRobots = new JTable(modeloRobots);
    private Simulacion simulacion;

    public PanelRobots(Ciudad ciudad) {
        super(new BorderLayout());
        this.ciudad = ciudad;

        tablaRobots.setRowHeight(22);
        tablaRobots.setFillsViewportHeight(true);
        tablaRobots.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        tablaRobots.getSelectionModel().addListSelectionListener(e -> refrescarVariables());
        modeloRobots.edicionHabilitada = () -> simulacion != null && !simulacion.estaCorriendo();

        DefaultTableCellRenderer color = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tabla, Object valor,
                    boolean seleccionada, boolean foco, int fila, int columna) {
                Component c = super.getTableCellRendererComponent(
                        tabla, valor, seleccionada, foco, fila, columna);
                List<Robot> robots = modeloRobots.robots;
                if (!seleccionada && fila < robots.size()) {
                    c.setForeground(robots.get(fila).getColor());
                }
                return c;
            }
        };
        tablaRobots.getColumnModel().getColumn(0).setCellRenderer(color);

        JTable tablaVariables = new JTable(modeloVariables);
        tablaVariables.setRowHeight(20);
        tablaVariables.setFillsViewportHeight(true);

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.add(titulo("Robots"), BorderLayout.NORTH);
        arriba.add(new JScrollPane(tablaRobots), BorderLayout.CENTER);
        JLabel pista = new JLabel("Con el programa compilado y detenido se pueden editar "
                + "«Flores» y «Papeles»: son las bolsas con las que arranca cada robot.");
        pista.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        pista.setFont(pista.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        pista.setForeground(new java.awt.Color(0x60686A));
        arriba.add(pista, BorderLayout.SOUTH);

        JPanel abajo = new JPanel(new BorderLayout());
        abajo.add(titulo("Variables del robot seleccionado"), BorderLayout.NORTH);
        abajo.add(new JScrollPane(tablaVariables), BorderLayout.CENTER);

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, arriba, abajo);
        division.setResizeWeight(0.55);
        division.setBorder(null);
        add(division, BorderLayout.CENTER);
    }

    private static JLabel titulo(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        etiqueta.setFont(etiqueta.getFont().deriveFont(java.awt.Font.BOLD));
        return etiqueta;
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
        refrescarVariables();
    }

    private void refrescarVariables() {
        int fila = tablaRobots.getSelectedRow();
        List<Robot> robots = modeloRobots.robots;
        if (simulacion == null || fila < 0 || fila >= robots.size()) {
            modeloVariables.cargar(Map.of());
            return;
        }
        Interprete interprete = simulacion.getInterpretes().get(robots.get(fila));
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
                {"Robot", "Tipo", "Av", "Ca", "Mira al", "Flores", "Papeles", "Estado"};
        private static final int COL_FLORES = 5;
        private static final int COL_PAPELES = 6;

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
            return columna == COL_FLORES || columna == COL_PAPELES ? Integer.class : String.class;
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
}
