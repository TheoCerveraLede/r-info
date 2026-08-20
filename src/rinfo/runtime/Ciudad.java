package rinfo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La grilla sobre la que se mueven los robots.
 *
 * <p>Las esquinas se direccionan por avenida y calle, ambas numeradas desde 1.
 * La avenida crece hacia el este y la calle hacia el norte, igual que en el
 * intérprete original, que reservaba una matriz de 101x101 y usaba el índice 0
 * como relleno.
 */
public final class Ciudad {

    /** Cantidad máxima de avenidas y de calles. */
    public static final int MAXIMO = 100;

    private final Esquina[][] esquinas = new Esquina[MAXIMO + 1][MAXIMO + 1];
    private final Map<String, Robot> robots = new LinkedHashMap<>();
    private final List<Area> areas = new ArrayList<>();

    private int numAv = MAXIMO;
    private int numCa = MAXIMO;

    public Ciudad() {
        for (int av = 0; av <= MAXIMO; av++) {
            for (int ca = 0; ca <= MAXIMO; ca++) {
                esquinas[av][ca] = new Esquina();
            }
        }
    }

    public int getNumAv() {
        return numAv;
    }

    public void setNumAv(int numAv) {
        this.numAv = Math.clamp(numAv, 1, MAXIMO);
    }

    public int getNumCa() {
        return numCa;
    }

    public void setNumCa(int numCa) {
        this.numCa = Math.clamp(numCa, 1, MAXIMO);
    }

    public boolean dentro(int av, int ca) {
        return av >= 1 && av <= numAv && ca >= 1 && ca <= numCa;
    }

    public Esquina esquina(int av, int ca) {
        if (!dentro(av, ca)) {
            throw new IndexOutOfBoundsException("esquina fuera de la ciudad: av " + av + ", ca " + ca);
        }
        return esquinas[av][ca];
    }

    /** Acceso sin validar límites, para dibujar. */
    public Esquina esquinaCruda(int av, int ca) {
        return esquinas[av][ca];
    }

    public boolean hayObstaculo(int av, int ca) {
        return dentro(av, ca) && esquinas[av][ca].tieneObstaculo();
    }

    // --- Robots ---------------------------------------------------------

    /** Registra un robot nuevo con el nombre de la variable que lo declara. */
    public Robot agregarRobot(String nombre, String tipoRobot) {
        Robot robot = new Robot(this, nombre, tipoRobot, robots.size());
        robots.put(nombre, robot);
        return robot;
    }

    public Robot getRobot(String nombre) {
        return robots.get(nombre);
    }

    public List<Robot> getRobots() {
        return List.copyOf(robots.values());
    }

    // --- Áreas ----------------------------------------------------------

    public void setAreas(List<Area> areas) {
        this.areas.clear();
        this.areas.addAll(areas);
    }

    public List<Area> getAreas() {
        return List.copyOf(areas);
    }

    public Area getArea(String nombre) {
        for (Area a : areas) {
            if (a.getNombre().equals(nombre)) {
                return a;
            }
        }
        return null;
    }

    // --- Reinicio -------------------------------------------------------

    /** Borra robots y áreas, y deja las esquinas como estaban configuradas. */
    public void limpiarEjecucion() {
        robots.clear();
        areas.clear();
    }

    /** Vacía por completo la ciudad, incluidas flores, papeles y obstáculos. */
    public void limpiarTodo() {
        limpiarEjecucion();
        for (int av = 0; av <= MAXIMO; av++) {
            for (int ca = 0; ca <= MAXIMO; ca++) {
                esquinas[av][ca].limpiar();
            }
        }
    }
}
