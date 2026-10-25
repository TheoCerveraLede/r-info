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
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import rinfo.runtime.Area;
import rinfo.runtime.Buzon;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Direccion;
import rinfo.runtime.Esquina;
import rinfo.runtime.MonitorEsquinas;
import rinfo.runtime.Robot;
import rinfo.runtime.Simulacion;

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

    private final Ciudad ciudad;
    private Simulacion simulacion;
    private int tamanioBloque = 26;
    private Herramienta herramienta = Herramienta.FLOR;
    private boolean edicionHabilitada = true;
    private Runnable alEditar;

    public VistaCiudad(Ciudad ciudad) {
        this.ciudad = ciudad;
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        aplicarTema();

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

    /** Relee los colores del tema vigente. */
    public void aplicarTema() {
        setBackground(Tema.actual().fondoCiudad);
        repaint();
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

    public void setSimulacion(Simulacion simulacion) {
        this.simulacion = simulacion;
        repaint();
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

        Tema tema = Tema.actual();
        dibujarManzanas(g2, tema);
        dibujarAreas(g2, tema);
        dibujarCalles(g2, tema);
        dibujarBloqueos(g2, tema);
        dibujarContenido(g2, tema);
        dibujarRutas(g2, tema);
        dibujarEsperasMensajes(g2, tema);
        dibujarRobots(g2, tema);
        dibujarNumeracion(g2, tema);

        g2.dispose();
    }

    private void dibujarManzanas(Graphics2D g2, Tema tema) {
        g2.setColor(tema.manzana);
        for (int av = 1; av < ciudad.getNumAv(); av++) {
            for (int ca = 1; ca < ciudad.getNumCa(); ca++) {
                int x = avAx(av) + tamanioBloque / 6;
                int y = caAy(ca + 1) + tamanioBloque / 6;
                int lado = tamanioBloque - tamanioBloque / 3;
                g2.fillRect(x, y, lado, lado);
            }
        }
    }

    private void dibujarAreas(Graphics2D g2, Tema tema) {
        Font fuenteArea = getFont().deriveFont(Font.BOLD, Math.max(9f, tamanioBloque * 0.32f));
        for (Area area : ciudad.getAreas()) {
            Color base = tema.visible(area.getColor());
            g2.setColor(Tema.conAlfa(base, tema.alfaArea));
            int x = avAx(area.getAv1()) - tamanioBloque / 3;
            int y = caAy(area.getCa2()) - tamanioBloque / 3;
            int ancho = (area.getAv2() - area.getAv1()) * tamanioBloque + 2 * tamanioBloque / 3;
            int alto = (area.getCa2() - area.getCa1()) * tamanioBloque + 2 * tamanioBloque / 3;
            g2.fillRoundRect(x, y, ancho, alto, 8, 8);
            g2.setColor(Tema.conAlfa(base, tema.alfaBordeArea));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(x, y, ancho, alto, 8, 8);

            if (tamanioBloque >= 18) {
                g2.setFont(fuenteArea);
                g2.setColor(Tema.conAlfa(base, 220));
                String etiqueta = area.getNombre() + " (" + area.getTipo().nombre + ")";
                g2.drawString(etiqueta, x + 5, y + g2.getFontMetrics().getAscent() + 2);
            }
        }
    }

    private void dibujarCalles(Graphics2D g2, Tema tema) {
        g2.setColor(tema.calle);
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

    private void dibujarContenido(Graphics2D g2, Tema tema) {
        int radio = Math.max(3, tamanioBloque / 5);
        Font fuente = getFont().deriveFont(Font.PLAIN, Math.max(8f, tamanioBloque * 0.34f));
        g2.setFont(fuente);

        for (int av = 1; av <= ciudad.getNumAv(); av++) {
            for (int ca = 1; ca <= ciudad.getNumCa(); ca++) {
                Esquina esquina = ciudad.esquinaCruda(av, ca);
                int x = avAx(av);
                int y = caAy(ca);

                if (esquina.tieneObstaculo()) {
                    g2.setColor(tema.obstaculo);
                    int lado = tamanioBloque / 2;
                    g2.fillRect(x - lado / 2, y - lado / 2, lado, lado);
                    continue;
                }
                int flores = esquina.getFlores();
                int papeles = esquina.getPapeles();
                if (flores > 0) {
                    g2.setColor(tema.flor);
                    g2.fillOval(x - radio, y - radio, 2 * radio, 2 * radio);
                    if (flores > 1 && tamanioBloque >= 20) {
                        g2.drawString(String.valueOf(flores), x + radio, y - radio);
                    }
                }
                if (papeles > 0) {
                    g2.setColor(tema.papel);
                    g2.fillRect(x - radio, y - radio, 2 * radio, 2 * radio);
                    if (papeles > 1 && tamanioBloque >= 20) {
                        g2.drawString(String.valueOf(papeles), x + radio, y + 2 * radio);
                    }
                }
            }
        }
    }

    private void dibujarRutas(Graphics2D g2, Tema tema) {
        g2.setStroke(new BasicStroke(2f));
        for (Robot robot : ciudad.getRobots()) {
            var ruta = robot.getRuta();
            if (ruta.size() < 2) {
                continue;
            }
            Color c = tema.visible(robot.getColor());
            g2.setColor(Tema.conAlfa(c, tema.alfaRuta));
            for (int i = 1; i < ruta.size(); i++) {
                int[] a = ruta.get(i - 1);
                int[] b = ruta.get(i);
                g2.drawLine(avAx(a[0]), caAy(a[1]), avAx(b[0]), caAy(b[1]));
            }
        }
    }

    /** Dibuja las esquinas tomadas con BloquearEsquina y las flechas de quienes esperan. */
    private void dibujarBloqueos(Graphics2D g2, Tema tema) {
        if (simulacion == null) {
            return;
        }
        List<MonitorEsquinas.Bloqueo> bloqueos = simulacion.getEsquinas().instantanea();
        if (bloqueos.isEmpty()) {
            return;
        }
        int lado = Math.max(12, (int) (tamanioBloque * 0.76));
        int mitad = lado / 2;
        Font fuente = getFont().deriveFont(Font.BOLD, Math.max(8f, tamanioBloque * 0.30f));

        for (MonitorEsquinas.Bloqueo b : bloqueos) {
            int x = avAx(b.av());
            int y = caAy(b.ca());
            Color colorDuenio = tema.visible(b.duenio().getColor());

            // Recuadro de esquina tomada con el color del robot dueño
            g2.setColor(Tema.conAlfa(colorDuenio, 55));
            g2.fillRoundRect(x - mitad, y - mitad, lado, lado, 6, 6);
            g2.setColor(colorDuenio);
            g2.setStroke(new BasicStroke(2f));
            g2.drawRoundRect(x - mitad, y - mitad, lado, lado, 6, 6);

            // Candado vectorial y nombre del dueño arriba del recuadro
            int tamCandado = Math.max(7, tamanioBloque / 3);
            dibujarCandado(g2, x - mitad - 2, y - mitad - 2, tamCandado,
                    colorDuenio, tema.fondoCiudad);

            if (tamanioBloque >= 20 && (b.duenio().getAv() != b.av() || b.duenio().getCa() != b.ca())) {
                g2.setFont(fuente);
                g2.setColor(colorDuenio);
                g2.drawString(b.duenio().getNombre(), x - mitad + tamCandado + 2, y - mitad + tamCandado - 2);
            }

            // Flechas punteadas desde cada robot que está bloqueado esperando esta esquina
            for (Robot esperando : b.esperando()) {
                if (esperando.estaIniciado()) {
                    Color colorEsp = tema.visible(esperando.getColor());
                    dibujarFlechaEspera(g2, avAx(esperando.getAv()), caAy(esperando.getCa()),
                            x, y, mitad + 3, colorEsp, new float[] {5f, 4f});
                }
            }
        }
    }

    /** Dibuja flechas punteadas e indicadores cuando un robot espera o acumula mensajes. */
    private void dibujarEsperasMensajes(Graphics2D g2, Tema tema) {
        Font fuente = getFont().deriveFont(Font.BOLD, Math.max(8f, tamanioBloque * 0.30f));
        g2.setFont(fuente);

        for (Robot robot : ciudad.getRobots()) {
            if (!robot.estaIniciado()) {
                continue;
            }
            int rx = avAx(robot.getAv());
            int ry = caAy(robot.getCa());
            Color colorRobot = tema.visible(robot.getColor());

            String esperandoDe = robot.getBuzon().getEsperandoDe();
            if (esperandoDe != null && !Buzon.COMODIN.equals(esperandoDe)) {
                Robot emisor = ciudad.getRobot(esperandoDe);
                if (emisor != null && emisor.estaIniciado()) {
                    int margen = Math.max(6, (int) (tamanioBloque * 0.35));
                    dibujarFlechaEspera(g2, rx, ry, avAx(emisor.getAv()), caAy(emisor.getCa()),
                            margen, colorRobot, new float[] {2.5f, 3.5f});
                }
            }

            if (tamanioBloque >= 20) {
                int lado = Math.max(8, (int) (tamanioBloque * 0.62));
                int pendientes = robot.getBuzon().cantidadPendientes();
                if (esperandoDe != null) {
                    g2.setColor(colorRobot);
                    g2.drawString("?✉ " + esperandoDe, rx + lado / 2 + 2, ry + lado / 2 + 3);
                } else if (pendientes > 0) {
                    g2.setColor(colorRobot);
                    g2.drawString("✉" + pendientes, rx + lado / 2 + 2, ry + lado / 2 + 3);
                }
            }
        }
    }

    private static void dibujarCandado(Graphics2D g2, int x, int y, int tam, Color color, Color fondo) {
        int ancho = Math.max(6, tam);
        int altoCuerpo = Math.max(5, (tam * 3) / 5);
        int arco = Math.max(4, ancho - 2);
        int cy = y + tam - altoCuerpo;

        g2.setColor(fondo);
        g2.fillRoundRect(x - 1, cy - 1, ancho + 2, altoCuerpo + 2, 3, 3);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawArc(x + (ancho - arco) / 2, y, arco, arco, 0, 180);
        g2.fillRoundRect(x, cy, ancho, altoCuerpo, 3, 3);
    }

    private static void dibujarFlechaEspera(Graphics2D g2, int x1, int y1, int x2, int y2,
                                            int margenDestino, Color color, float[] patron) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dist = Math.hypot(dx, dy);
        if (dist <= margenDestino + 4) {
            return;
        }
        double angulo = Math.atan2(dy, dx);
        double fx = x2 - margenDestino * Math.cos(angulo);
        double fy = y2 - margenDestino * Math.sin(angulo);

        g2.setColor(Tema.conAlfa(color, 210));
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                10f, patron, 0f));
        g2.drawLine(x1, y1, (int) Math.round(fx), (int) Math.round(fy));

        double largoPunta = 7.5;
        Path2D punta = new Path2D.Double();
        punta.moveTo(fx, fy);
        punta.lineTo(fx - largoPunta * Math.cos(angulo - Math.PI / 6),
                fy - largoPunta * Math.sin(angulo - Math.PI / 6));
        punta.lineTo(fx - largoPunta * Math.cos(angulo + Math.PI / 6),
                fy - largoPunta * Math.sin(angulo + Math.PI / 6));
        punta.closePath();
        g2.setStroke(new BasicStroke(1f));
        g2.fill(punta);
    }

    private void dibujarRobots(Graphics2D g2, Tema tema) {
        for (Robot robot : ciudad.getRobots()) {
            if (!robot.estaIniciado()) {
                continue;
            }
            int x = avAx(robot.getAv());
            int y = caAy(robot.getCa());
            int lado = Math.max(8, (int) (tamanioBloque * 0.62));

            Color colorRobot = tema.visible(robot.getColor());
            g2.setColor(colorRobot);
            g2.fill(flecha(x, y, lado, robot.getDireccion()));
            g2.setColor(tema.contornoRobot);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(flecha(x, y, lado, robot.getDireccion()));

            if (tamanioBloque >= 20) {
                g2.setColor(colorRobot);
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

    private void dibujarNumeracion(Graphics2D g2, Tema tema) {
        if (tamanioBloque < 18) {
            return;
        }
        g2.setFont(getFont().deriveFont(Font.PLAIN, 10f));
        g2.setColor(tema.numeracion);
        int paso = tamanioBloque < 26 ? 5 : 1;
        for (int av = 1; av <= ciudad.getNumAv(); av += paso) {
            g2.drawString(String.valueOf(av), avAx(av) - 3, caAy(1) + 16);
        }
        for (int ca = 1; ca <= ciudad.getNumCa(); ca += paso) {
            g2.drawString(String.valueOf(ca), avAx(1) - MARGEN + 2, caAy(ca) + 4);
        }
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        Point p = esquinaEn(e.getX(), e.getY());
        if (p == null) {
            return null;
        }
        int av = p.x;
        int ca = p.y;
        StringBuilder sb = new StringBuilder("<html><b>Esquina (av ")
                .append(av).append(", ca ").append(ca).append(")</b>");

        Esquina esq = ciudad.esquinaCruda(av, ca);
        if (esq.tieneObstaculo()) {
            sb.append("<br>Obstáculo");
        } else if (esq.getFlores() > 0 || esq.getPapeles() > 0) {
            sb.append("<br>Flores: ").append(esq.getFlores())
                    .append(" · Papeles: ").append(esq.getPapeles());
        }

        for (Area a : ciudad.getAreas()) {
            if (a.contiene(av, ca)) {
                sb.append("<br>Área: <b>").append(a.getNombre()).append("</b> (")
                        .append(a.getTipo().nombre).append(")");
            }
        }

        if (simulacion != null) {
            for (MonitorEsquinas.Bloqueo b : simulacion.getEsquinas().instantanea()) {
                if (b.av() == av && b.ca() == ca) {
                    sb.append("<br>🔒 Bloqueada por <b>").append(b.duenio().getNombre()).append("</b>");
                    if (!b.esperando().isEmpty()) {
                        sb.append(" (esperando: ");
                        for (int i = 0; i < b.esperando().size(); i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(b.esperando().get(i).getNombre());
                        }
                        sb.append(")");
                    }
                }
            }
        }

        for (Robot r : ciudad.getRobots()) {
            if (r.estaIniciado() && r.getAv() == av && r.getCa() == ca) {
                sb.append("<br>Robot <b>").append(r.getNombre()).append("</b> (")
                        .append(r.getEstado()).append(")");
            }
        }
        sb.append("</html>");
        return sb.toString();
    }
}
