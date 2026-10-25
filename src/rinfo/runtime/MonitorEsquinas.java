package rinfo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exclusión mutua por esquina.
 *
 * <p>{@code BloquearEsquina(av, ca)} espera hasta que la esquina quede libre y
 * se la queda; {@code LiberarEsquina(av, ca)} la devuelve. Un mismo robot
 * puede bloquear una esquina que ya tiene sin quedar trabado.
 */
public final class MonitorEsquinas {

    /** Estado de una esquina tomada y la cola de robots que esperan por ella. */
    public record Bloqueo(int av, int ca, Robot duenio, List<Robot> esperando) {}

    /** Esquina por la que está esperando un robot bloqueado. */
    public record EsperaEsquina(int av, int ca, Robot duenio) {}

    private final Map<Long, Robot> duenios = new LinkedHashMap<>();
    private final Map<Robot, Long> esperando = new LinkedHashMap<>();
    private boolean abortado;

    private static long clave(int av, int ca) {
        return ((long) av << 32) | (ca & 0xFFFFFFFFL);
    }

    private static int avDe(long clave) {
        return (int) (clave >> 32);
    }

    private static int caDe(long clave) {
        return (int) clave;
    }

    public synchronized void bloquear(Robot robot, int av, int ca) throws InterruptedException {
        bloquear(robot, av, ca, null);
    }

    public synchronized void bloquear(Robot robot, int av, int ca, Runnable alCambiar)
            throws InterruptedException {
        long k = clave(av, ca);
        if (duenios.containsKey(k) && duenios.get(k) != robot) {
            esperando.put(robot, k);
            if (alCambiar != null) {
                alCambiar.run();
            }
            try {
                while (!abortado && duenios.containsKey(k) && duenios.get(k) != robot) {
                    wait();
                }
                if (abortado) {
                    throw new InterruptedException("ejecución detenida");
                }
            } finally {
                if (!abortado) {
                    esperando.remove(robot);
                }
            }
        }
        duenios.put(k, robot);
        if (alCambiar != null) {
            alCambiar.run();
        }
    }

    public synchronized void liberar(Robot robot, int av, int ca) throws ErrorEjecucion {
        long k = clave(av, ca);
        Robot duenio = duenios.get(k);
        if (duenio == null) {
            return;
        }
        if (duenio != robot) {
            throw new ErrorEjecucion("el robot " + robot.getNombre()
                    + " intentó liberar la esquina (av " + av + ", ca " + ca
                    + ") que había bloqueado " + duenio.getNombre());
        }
        duenios.remove(k);
        notifyAll();
    }

    /** Devuelve el robot que tiene tomada la esquina, o {@code null} si está libre. */
    public synchronized Robot duenioDe(int av, int ca) {
        return duenios.get(clave(av, ca));
    }

    /** Devuelve qué esquina está esperando {@code robot}, o {@code null} si no espera ninguna. */
    public synchronized EsperaEsquina esperaDe(Robot robot) {
        Long k = esperando.get(robot);
        if (k == null) {
            return null;
        }
        return new EsperaEsquina(avDe(k), caDe(k), duenios.get(k));
    }

    /** Instantánea de todas las esquinas tomadas y quiénes las esperan. */
    public synchronized List<Bloqueo> instantanea() {
        if (duenios.isEmpty()) {
            return List.of();
        }
        List<Bloqueo> resultado = new ArrayList<>(duenios.size());
        for (var entrada : duenios.entrySet()) {
            long k = entrada.getKey();
            List<Robot> cola = new ArrayList<>();
            for (var esp : esperando.entrySet()) {
                if (esp.getValue().equals(k)) {
                    cola.add(esp.getKey());
                }
            }
            resultado.add(new Bloqueo(avDe(k), caDe(k), entrada.getValue(), List.copyOf(cola)));
        }
        return resultado;
    }

    /** Suelta todo lo que tenga tomado un robot que termina normalmente. */
    public synchronized void liberarTodo(Robot robot) {
        esperando.remove(robot);
        if (duenios.values().remove(robot)) {
            while (duenios.values().remove(robot)) {
                // seguir quitando el resto de las esquinas del mismo robot
            }
            notifyAll();
        }
    }

    /** Despierta a los hilos en espera conservando la foto del bloqueo en la vista. */
    public synchronized void abortar() {
        abortado = true;
        notifyAll();
    }

    public synchronized void limpiar() {
        duenios.clear();
        esperando.clear();
        abortado = false;
        notifyAll();
    }
}
