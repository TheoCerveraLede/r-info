package rinfo.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import rinfo.runtime.Area;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Direccion;
import rinfo.runtime.Esquina;
import rinfo.runtime.Robot;

/**
 * Dibujo de la ciudad: calles, áreas, contenido de las esquinas y robots.
 *
 * <p>La calle 1 va abajo y la avenida 1 a la izquierda, de modo que el norte
 * apunta hacia arriba de la pantalla.
 */
public final class VistaCiudad extends JPanel {

    /** Qué deposita el clic del mouse sobre una esquina. */
    public enum Herramienta {
        FLOR("Flor"),
        PAPEL("Papel"),
        OBSTACULO("Obstáculo"),
        BORRAR("Borrar");

        private final String etiqueta;

        Herramienta(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return etiqueta;
        }
    }

    private static final int MARGEN = 22;
    private static final Color COLOR_FONDO = new Color(0xF7F7F4);
    private static final Color COLOR_MANZANA = new Color(0xE3E6E0);
    private static final Color COLOR_CALLE = new Color(0xB9BDB6);
    private static final Color COLOR_FLOR = new Color(0xD81B60);
    private static final Color COLOR_PAPEL = new Color(0x1E88E5);
    private static final Color COLOR_OBSTACULO = new Color(0x37474F);

    private final Ciudad ciudad;
    private int tamanioBloque = 26;
    private Herramienta herramienta = Herramienta.FLOR;
    private boolean edicionHabilitada = true;
    private Runnable alEditar;

    public VistaCiudad(Ciudad ciudad) {
        this.ciudad = ciudad;
        setBackground(COLOR_FONDO);
        setOpaque(true);

        MouseAdapter raton = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                manejarClic(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                manejarClic(e);
            }
        };
        addMouseListener(raton);
        addMouseMotionListener(raton);
    }

    public void setHerramienta(Herramienta herramienta) {
        this.herramienta = herramienta;
    }

    public void setEdicionHabilitada(boolean edicionHabilitada) {
        this.edicionHabilitada = edicionHabilitada;
    }

    public void setAlEditar(Runnable alEditar) {
        this.alEditar = alEditar;
    }

    public int getTamanioBloque() {
        return tamanioBloque;
    }

    public void setTamanioBloque(int tamanioBloque) {
        this.tamanioBloque = Math.clamp(tamanioBloque, 10, 60);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------
    // Coordenadas
    // ------------------------------------------------------------------

    private int avAx(int av) {
        return MARGEN + (av - 1) * tamanioBloque;
    }

    private int caAy(int ca) {
        return MARGEN + (ciudad.getNumCa() - ca) * tamanioBloque;
    }

    private Point esquinaEn(int x, int y) {
        int av = Math.round((x - MARGEN) / (float) tamanioBloque) + 1;
        int ca = ciudad.getNumCa() - Math.round((y - MARGEN) / (float) tamanioBloque);
        if (!ciudad.dentro(av, ca)) {
            return null;
        }
        return new Point(av, ca);
    }

    private void manejarClic(MouseEvent e) {
        if (!edicionHabilitada) {
            return;
        }
        Point p = esquinaEn(e.getX(), e.getY());
        if (p == null) {
            return;
        }
        Esquina esquina = ciudad.esquina(p.x, p.y);
        boolean quitar = SwingUtilities.isRightMouseButton(e) || herramienta == Herramienta.BORRAR;

        if (quitar) {
            esquina.limpiar();
        } else {
            switch (herramienta) {
                case FLOR -> esquina.dejarFlor();
                case PAPEL -> esquina.dejarPapel();
                case OBSTACULO -> esquina.setObstaculo(true);
                case BORRAR -> esquina.limpiar();
            }
        }
        repaint();
        if (alEditar != null) {
            alEditar.run();
        }
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(2 * MARGEN + ciudad.getNumAv() * tamanioBloque,
                2 * MARGEN + ciudad.getNumCa() * tamanioBloque);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        dibujarManzanas(g2);
        dibujarAreas(g2);
        dibujarCalles(g2);
        dibujarContenido(g2);
        dibujarRutas(g2);
        dibujarRobots(g2);
        dibujarNumeracion(g2);

        g2.dispose();
    }

    private void dibujarManzanas(Graphics2D g2) {
        g2.setColor(COLOR_MANZANA);
        for (int av = 1; av < ciudad.getNumAv(); av++) {
            for (int ca = 1; ca < ciudad.getNumCa(); ca++) {
                int x = avAx(av) + tamanioBloque / 6;
                int y = caAy(ca + 1) + tamanioBloque / 6;
                int lado = tamanioBloque - tamanioBloque / 3;
                g2.fillRect(x, y, lado, lado);
            }
        }
    }

    private void dibujarAreas(Graphics2D g2) {
        for (Area area : ciudad.getAreas()) {
            Color base = area.getColor();
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 46));
            int x = avAx(area.getAv1()) - tamanioBloque / 3;
            int y = caAy(area.getCa2()) - tamanioBloque / 3;
            int ancho = (area.getAv2() - area.getAv1()) * tamanioBloque + 2 * tamanioBloque / 3;
            int alto = (area.getCa2() - area.getCa1()) * tamanioBloque + 2 * tamanioBloque / 3;
            g2.fillRoundRect(x, y, ancho, alto, 8, 8);
            g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 150));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, ancho, alto, 8, 8);
        }
    }

    private void dibujarCalles(Graphics2D g2) {
        g2.setColor(COLOR_CALLE);
        g2.setStroke(new BasicStroke(1f));
        int x1 = avAx(1);
        int x2 = avAx(ciudad.getNumAv());
        for (int ca = 1; ca <= ciudad.getNumCa(); ca++) {
            int y = caAy(ca);
            g2.drawLine(x1, y, x2, y);
        }
        int y1 = caAy(ciudad.getNumCa());
        int y2 = caAy(1);
        for (int av = 1; av <= ciudad.getNumAv(); av++) {
            int x = avAx(av);
            g2.drawLine(x, y1, x, y2);
        }
    }

    private void dibujarContenido(Graphics2D g2) {
        int radio = Math.max(3, tamanioBloque / 5);
        Font fuente = getFont().deriveFont(Font.PLAIN, Math.max(8f, tamanioBloque * 0.34f));
        g2.setFont(fuente);

        for (int av = 1; av <= ciudad.getNumAv(); av++) {
            for (int ca = 1; ca <= ciudad.getNumCa(); ca++) {
                Esquina esquina = ciudad.esquinaCruda(av, ca);
                int x = avAx(av);
                int y = caAy(ca);

                if (esquina.tieneObstaculo()) {
                    g2.setColor(COLOR_OBSTACULO);
                    int lado = tamanioBloque / 2;
                    g2.fillRect(x - lado / 2, y - lado / 2, lado, lado);
                    continue;
                }
                int flores = esquina.getFlores();
                int papeles = esquina.getPapeles();
                if (flores > 0) {
                    g2.setColor(COLOR_FLOR);
                    g2.fillOval(x - radio, y - radio, 2 * radio, 2 * radio);
                    if (flores > 1 && tamanioBloque >= 20) {
                        g2.drawString(String.valueOf(flores), x + radio, y - radio);
                    }
                }
                if (papeles > 0) {
                    g2.setColor(COLOR_PAPEL);
                    g2.fillRect(x - radio, y - radio, 2 * radio, 2 * radio);
                    if (papeles > 1 && tamanioBloque >= 20) {
                        g2.drawString(String.valueOf(papeles), x + radio, y + 2 * radio);
                    }
                }
            }
        }
    }

    private void dibujarRutas(Graphics2D g2) {
        g2.setStroke(new BasicStroke(2f));
        for (Robot robot : ciudad.getRobots()) {
            var ruta = robot.getRuta();
            if (ruta.size() < 2) {
                continue;
            }
            Color c = robot.getColor();
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 70));
            for (int i = 1; i < ruta.size(); i++) {
                int[] a = ruta.get(i - 1);
                int[] b = ruta.get(i);
                g2.drawLine(avAx(a[0]), caAy(a[1]), avAx(b[0]), caAy(b[1]));
            }
        }
    }

    private void dibujarRobots(Graphics2D g2) {
        for (Robot robot : ciudad.getRobots()) {
            if (!robot.estaIniciado()) {
                continue;
            }
            int x = avAx(robot.getAv());
            int y = caAy(robot.getCa());
            int lado = Math.max(8, (int) (tamanioBloque * 0.62));

            g2.setColor(robot.getColor());
            g2.fill(flecha(x, y, lado, robot.getDireccion()));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(flecha(x, y, lado, robot.getDireccion()));

            if (tamanioBloque >= 20) {
                g2.setColor(robot.getColor().darker());
                g2.setFont(getFont().deriveFont(Font.BOLD, Math.max(9f, tamanioBloque * 0.36f)));
                g2.drawString(robot.getNombre(), x + lado / 2 + 2, y - lado / 2);
            }
        }
    }

    /** Triángulo apuntando en la dirección en que mira el robot. */
    private static Path2D flecha(int x, int y, int lado, Direccion direccion) {
        double mitad = lado / 2.0;
        Path2D p = new Path2D.Double();
        p.moveTo(0, -mitad);
        p.lineTo(mitad * 0.8, mitad * 0.7);
        p.lineTo(0, mitad * 0.35);
        p.lineTo(-mitad * 0.8, mitad * 0.7);
        p.closePath();

        double giro = switch (direccion) {
            case NORTE -> 0;
            case ESTE -> Math.PI / 2;
            case SUR -> Math.PI;
            case OESTE -> -Math.PI / 2;
        };
        var transformada = new java.awt.geom.AffineTransform();
        transformada.translate(x, y);
        transformada.rotate(giro);
        return new Path2D.Double(transformada.createTransformedShape(p));
    }

    private void dibujarNumeracion(Graphics2D g2) {
        if (tamanioBloque < 18) {
            return;
        }
        g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        g2.setColor(new Color(0x707870));
        int paso = tamanioBloque < 26 ? 5 : 1;
        for (int av = 1; av <= ciudad.getNumAv(); av += paso) {
            g2.drawString(String.valueOf(av), avAx(av) - 3, caAy(1) + 16);
        }
        for (int ca = 1; ca <= ciudad.getNumCa(); ca += paso) {
            g2.drawString(String.valueOf(ca), avAx(1) - MARGEN + 2, caAy(ca) + 4);
        }
    }
}
