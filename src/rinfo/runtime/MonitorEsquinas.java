package rinfo.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Exclusión mutua por esquina.
 *
 * <p>{@code BloquearEsquina(av, ca)} espera hasta que la esquina quede libre y
 * se la queda; {@code LiberarEsquina(av, ca)} la devuelve. Un mismo robot
 * puede bloquear una esquina que ya tiene sin quedar trabado.
 */
public final class MonitorEsquinas {

    private final Map<Long, Robot> duenios = new HashMap<>();

    private static long clave(int av, int ca) {
        return ((long) av << 32) | (ca & 0xFFFFFFFFL);
    }

    public synchronized void bloquear(Robot robot, int av, int ca) throws InterruptedException {
        long k = clave(av, ca);
        while (duenios.containsKey(k) && duenios.get(k) != robot) {
            wait();
        }
        duenios.put(k, robot);
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

    /** Suelta todo lo que tenga tomado un robot que termina o se aborta. */
    public synchronized void liberarTodo(Robot robot) {
        if (duenios.values().remove(robot)) {
            while (duenios.values().remove(robot)) {
                // seguir quitando el resto de las esquinas del mismo robot
            }
            notifyAll();
        }
    }

    public synchronized void limpiar() {
        duenios.clear();
        notifyAll();
    }
}
