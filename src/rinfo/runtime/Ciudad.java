package rinfo.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

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

    // --- Contenido de las esquinas ---------------------------------------

    /**
     * Deja {@code cantidad} unidades de {@code que} en una esquina.
     *
     * <p>Un obstáculo y el contenido de la esquina se excluyen: poner un
     * obstáculo vacía la esquina y poner flores o papeles saca el obstáculo.
     *
     * @throws IllegalArgumentException si la esquina cae fuera de la ciudad
     */
    public void colocar(Contenido que, int av, int ca, int cantidad) {
        if (!dentro(av, ca)) {
            throw new IllegalArgumentException(
                    "la esquina (av " + av + ", ca " + ca + ") está fuera de la ciudad");
        }
        if (cantidad < 1) {
            return;
        }
        Esquina esquina = esquinas[av][ca];
        switch (que) {
            case OBSTACULO -> {
                esquina.limpiar();
                esquina.setObstaculo(true);
            }
            case FLOR -> {
                esquina.setObstaculo(false);
                esquina.setFlores(esquina.getFlores() + cantidad);
            }
            case PAPEL -> {
                esquina.setObstaculo(false);
                esquina.setPapeles(esquina.getPapeles() + cantidad);
            }
        }
    }

    /**
     * Reparte {@code cantidad} unidades de {@code que} al azar dentro del
     * rectángulo indicado.
     *
     * @param unaPorEsquina si es {@code true} cada esquina recibe como mucho
     *                      una unidad y se saltean las que ya tienen algo, de
     *                      modo que puede colocar menos de lo pedido
     * @return cuántas unidades se colocaron realmente
     */
    public int colocarAlAzar(Contenido que, int cantidad,
                             int av1, int ca1, int av2, int ca2, boolean unaPorEsquina) {
        int desdeAv = Math.min(av1, av2);
        int hastaAv = Math.max(av1, av2);
        int desdeCa = Math.min(ca1, ca2);
        int hastaCa = Math.max(ca1, ca2);
        if (!dentro(desdeAv, desdeCa) || !dentro(hastaAv, hastaCa)) {
            throw new IllegalArgumentException("el rango se sale de la ciudad");
        }
        if (cantidad < 1) {
            return 0;
        }

        if (!unaPorEsquina) {
            var azar = ThreadLocalRandom.current();
            for (int i = 0; i < cantidad; i++) {
                colocar(que, azar.nextInt(desdeAv, hastaAv + 1), azar.nextInt(desdeCa, hastaCa + 1), 1);
            }
            return cantidad;
        }

        List<int[]> libres = new ArrayList<>();
        for (int av = desdeAv; av <= hastaAv; av++) {
            for (int ca = desdeCa; ca <= hastaCa; ca++) {
                if (estaVacia(av, ca)) {
                    libres.add(new int[] {av, ca});
                }
            }
        }
        Collections.shuffle(libres);
        int colocadas = Math.min(cantidad, libres.size());
        for (int i = 0; i < colocadas; i++) {
            colocar(que, libres.get(i)[0], libres.get(i)[1], 1);
        }
        return colocadas;
    }

    /**
     * Deja {@code cantidadPorEsquina} unidades de {@code que} en <b>cada</b>
     * esquina del rectángulo. A diferencia de
     * {@link #colocarAlAzar(Contenido, int, int, int, int, int, boolean)}, el
     * resultado es siempre el mismo.
     *
     * @return cuántas esquinas se tocaron
     */
    public int rellenar(Contenido que, int cantidadPorEsquina,
                        int av1, int ca1, int av2, int ca2) {
        int desdeAv = Math.min(av1, av2);
        int hastaAv = Math.max(av1, av2);
        int desdeCa = Math.min(ca1, ca2);
        int hastaCa = Math.max(ca1, ca2);
        if (!dentro(desdeAv, desdeCa) || !dentro(hastaAv, hastaCa)) {
            throw new IllegalArgumentException("el rango se sale de la ciudad");
        }
        if (cantidadPorEsquina < 1) {
            return 0;
        }
        int esquinasTocadas = 0;
        for (int av = desdeAv; av <= hastaAv; av++) {
            for (int ca = desdeCa; ca <= hastaCa; ca++) {
                colocar(que, av, ca, cantidadPorEsquina);
                esquinasTocadas++;
            }
        }
        return esquinasTocadas;
    }

    public boolean estaVacia(int av, int ca) {
        Esquina esquina = esquinas[av][ca];
        return esquina.getFlores() == 0 && esquina.getPapeles() == 0 && !esquina.tieneObstaculo();
    }

    /** Deja sin contenido todas las esquinas del rectángulo. */
    public void vaciar(int av1, int ca1, int av2, int ca2) {
        int desdeAv = Math.clamp(Math.min(av1, av2), 1, numAv);
        int hastaAv = Math.clamp(Math.max(av1, av2), 1, numAv);
        int desdeCa = Math.clamp(Math.min(ca1, ca2), 1, numCa);
        int hastaCa = Math.clamp(Math.max(ca1, ca2), 1, numCa);
        for (int av = desdeAv; av <= hastaAv; av++) {
            for (int ca = desdeCa; ca <= hastaCa; ca++) {
                esquinas[av][ca].limpiar();
            }
        }
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
