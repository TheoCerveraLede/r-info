package rinfo.runtime;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Un robot en ejecución.
 *
 * <p>Cada robot corre en su propio hilo y sólo puede pisar las esquinas que
 * pertenecen a alguna de las áreas que se le asignaron: sin área asignada no
 * puede arrancar.
 */
public final class Robot {

    private static final Color[] PALETA = {
        new Color(0xC62828), new Color(0x1565C0), new Color(0x2E7D32), new Color(0x6A1B9A),
        new Color(0xEF6C00), new Color(0x00838F), new Color(0xAD1457), new Color(0x4E342E)
    };

    private final Ciudad ciudad;
    private final String nombre;
    private final String tipoRobot;
    private final int id;
    private final Color color;

    private int av;
    private int ca;
    private Direccion direccion = Direccion.NORTE;

    private int floresEnBolsa;
    private int papelesEnBolsa;

    private final List<Area> areas = new ArrayList<>();
    private final Set<Long> esquinasPermitidas = new HashSet<>();

    /** Recorrido acumulado, para dibujar el rastro. */
    private final List<int[]> ruta = new ArrayList<>();

    private final Buzon buzon = new Buzon();
    private volatile boolean iniciado;
    private volatile String estado = "nuevo";

    Robot(Ciudad ciudad, String nombre, String tipoRobot, int id) {
        this.ciudad = ciudad;
        this.nombre = nombre;
        this.tipoRobot = tipoRobot;
        this.id = id;
        this.color = PALETA[id % PALETA.length];
    }

    // --- Identidad -------------------------------------------------------

    public String getNombre() {
        return nombre;
    }

    public String getTipoRobot() {
        return tipoRobot;
    }

    public int getId() {
        return id;
    }

    public Color getColor() {
        return color;
    }

    public Ciudad getCiudad() {
        return ciudad;
    }

    public Buzon getBuzon() {
        return buzon;
    }

    public boolean estaIniciado() {
        return iniciado;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    // --- Posición --------------------------------------------------------

    public synchronized int getAv() {
        return av;
    }

    public synchronized int getCa() {
        return ca;
    }

    public synchronized Direccion getDireccion() {
        return direccion;
    }

    public synchronized List<int[]> getRuta() {
        return List.copyOf(ruta);
    }

    // --- Bolsa -----------------------------------------------------------

    public synchronized int getFloresEnBolsa() {
        return floresEnBolsa;
    }

    public synchronized int getPapelesEnBolsa() {
        return papelesEnBolsa;
    }

    public synchronized boolean hayFlorEnLaBolsa() {
        return floresEnBolsa > 0;
    }

    public synchronized boolean hayPapelEnLaBolsa() {
        return papelesEnBolsa > 0;
    }

    // --- Áreas -----------------------------------------------------------

    public void asignarArea(Area area) {
        synchronized (this) {
            areas.add(area);
            for (int a = area.getAv1(); a <= area.getAv2(); a++) {
                for (int c = area.getCa1(); c <= area.getCa2(); c++) {
                    esquinasPermitidas.add(clave(a, c));
                }
            }
        }
    }

    public synchronized List<Area> getAreas() {
        return List.copyOf(areas);
    }

    public synchronized boolean sinArea() {
        return areas.isEmpty();
    }

    public synchronized boolean puedePisar(int av, int ca) {
        return esquinasPermitidas.contains(clave(av, ca));
    }

    private static long clave(int av, int ca) {
        return ((long) av << 32) | (ca & 0xFFFFFFFFL);
    }

    // --- Primitivas ------------------------------------------------------

    /** Coloca al robot en su posición inicial. */
    public void iniciar(int av, int ca) throws ErrorEjecucion {
        verificarDestino(av, ca, "Iniciar");
        synchronized (this) {
            this.av = av;
            this.ca = ca;
            this.direccion = Direccion.NORTE;
            ruta.clear();
            ruta.add(new int[] {av, ca});
            iniciado = true;
            estado = "en ejecución";
        }
        verificarChoque(av, ca);
    }

    public void mover() throws ErrorEjecucion {
        int destinoAv;
        int destinoCa;
        synchronized (this) {
            destinoAv = av + direccion.deltaAv;
            destinoCa = ca + direccion.deltaCa;
        }
        verificarDestino(destinoAv, destinoCa, "mover");
        synchronized (this) {
            av = destinoAv;
            ca = destinoCa;
            ruta.add(new int[] {destinoAv, destinoCa});
        }
        verificarChoque(destinoAv, destinoCa);
    }

    /** {@code Pos(av, ca)}: salta a otra esquina de su área. */
    public void pos(int av, int ca) throws ErrorEjecucion {
        verificarDestino(av, ca, "Pos");
        synchronized (this) {
            this.av = av;
            this.ca = ca;
            ruta.add(new int[] {av, ca});
        }
        verificarChoque(av, ca);
    }

    public synchronized void derecha() {
        direccion = direccion.derecha();
    }

    private void verificarDestino(int destinoAv, int destinoCa, String instruccion)
            throws ErrorEjecucion {
        if (!ciudad.dentro(destinoAv, destinoCa)) {
            throw new ErrorEjecucion("el robot " + nombre + " no puede ejecutar \"" + instruccion
                    + "\": se caería de la ciudad (av " + destinoAv + ", ca " + destinoCa + ")");
        }
        if (!puedePisar(destinoAv, destinoCa)) {
            throw new ErrorEjecucion("el robot " + nombre + " no puede ejecutar \"" + instruccion
                    + "\": la esquina (av " + destinoAv + ", ca " + destinoCa
                    + ") no pertenece a un área asignada");
        }
        if (ciudad.esquina(destinoAv, destinoCa).tieneObstaculo()) {
            throw new ErrorEjecucion("el robot " + nombre + " no puede ejecutar \"" + instruccion
                    + "\": hay un obstáculo en (av " + destinoAv + ", ca " + destinoCa + ")");
        }
    }

    private void verificarChoque(int enAv, int enCa) throws ErrorEjecucion {
        for (Robot otro : ciudad.getRobots()) {
            if (otro == this || !otro.estaIniciado()) {
                continue;
            }
            if (otro.getAv() == enAv && otro.getCa() == enCa) {
                throw new ErrorEjecucion("se produjo un choque entre el robot " + nombre
                        + " y el robot " + otro.getNombre()
                        + " en la avenida " + enAv + " y la calle " + enCa);
            }
        }
    }

    public void tomarFlor() throws ErrorEjecucion {
        int a = getAv();
        int c = getCa();
        if (!ciudad.esquina(a, c).tomarFlor()) {
            throw new ErrorEjecucion("el robot " + nombre
                    + " no puede ejecutar \"tomarFlor\": no hay flores en (av " + a + ", ca " + c + ")");
        }
        synchronized (this) {
            floresEnBolsa++;
        }
    }

    public void tomarPapel() throws ErrorEjecucion {
        int a = getAv();
        int c = getCa();
        if (!ciudad.esquina(a, c).tomarPapel()) {
            throw new ErrorEjecucion("el robot " + nombre
                    + " no puede ejecutar \"tomarPapel\": no hay papeles en (av " + a + ", ca " + c + ")");
        }
        synchronized (this) {
            papelesEnBolsa++;
        }
    }

    public void depositarFlor() throws ErrorEjecucion {
        synchronized (this) {
            if (floresEnBolsa <= 0) {
                throw new ErrorEjecucion("el robot " + nombre
                        + " no puede ejecutar \"depositarFlor\": no tiene flores en la bolsa");
            }
            floresEnBolsa--;
        }
        ciudad.esquina(getAv(), getCa()).dejarFlor();
    }

    public void depositarPapel() throws ErrorEjecucion {
        synchronized (this) {
            if (papelesEnBolsa <= 0) {
                throw new ErrorEjecucion("el robot " + nombre
                        + " no puede ejecutar \"depositarPapel\": no tiene papeles en la bolsa");
            }
            papelesEnBolsa--;
        }
        ciudad.esquina(getAv(), getCa()).dejarPapel();
    }

    public boolean hayFlorEnLaEsquina() {
        return ciudad.esquina(getAv(), getCa()).getFlores() > 0;
    }

    public boolean hayPapelEnLaEsquina() {
        return ciudad.esquina(getAv(), getCa()).getPapeles() > 0;
    }

    /** {@code HayObstaculo}: mira la esquina que tiene enfrente. */
    public boolean hayObstaculo() {
        int destinoAv;
        int destinoCa;
        synchronized (this) {
            destinoAv = av + direccion.deltaAv;
            destinoCa = ca + direccion.deltaCa;
        }
        if (!ciudad.dentro(destinoAv, destinoCa)) {
            return true;
        }
        return ciudad.esquina(destinoAv, destinoCa).tieneObstaculo();
    }

    @Override
    public String toString() {
        return nombre + " (" + tipoRobot + ")";
    }
}
