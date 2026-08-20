package rinfo.runtime;

/**
 * Regula el ritmo de la simulación.
 *
 * <p>Todos los hilos de robot pasan por {@link #puntoDeControl()} después de
 * cada acción visible. Ahí se aplican la velocidad elegida, la pausa y el modo
 * paso a paso, en el que cada hilo consume un permiso por acción.
 */
public final class ControlEjecucion {

    private volatile int demoraMs = 120;
    private boolean pausado;
    private boolean pasoAPaso;
    private int pasosDisponibles;
    private volatile boolean detenido;

    public int getDemoraMs() {
        return demoraMs;
    }

    public void setDemoraMs(int demoraMs) {
        this.demoraMs = Math.max(0, demoraMs);
    }

    public synchronized boolean estaPausado() {
        return pausado;
    }

    public synchronized boolean esPasoAPaso() {
        return pasoAPaso;
    }

    public boolean estaDetenido() {
        return detenido;
    }

    public synchronized void pausar() {
        pausado = true;
    }

    public synchronized void reanudar() {
        pausado = false;
        pasoAPaso = false;
        notifyAll();
    }

    /** Entra en modo paso a paso y habilita una única acción por robot. */
    public synchronized void paso() {
        pasoAPaso = true;
        pausado = false;
        pasosDisponibles++;
        notifyAll();
    }

    public synchronized void detener() {
        detenido = true;
        pausado = false;
        pasoAPaso = false;
        notifyAll();
    }

    public synchronized void reiniciar() {
        detenido = false;
        pausado = false;
        pasoAPaso = false;
        pasosDisponibles = 0;
        notifyAll();
    }

    /**
     * Punto de sincronización que ejecuta cada robot tras una acción visible.
     *
     * @throws InterruptedException si se pidió detener la simulación
     */
    public void puntoDeControl() throws InterruptedException {
        if (detenido) {
            throw new InterruptedException("ejecución detenida");
        }
        synchronized (this) {
            while (!detenido && (pausado || (pasoAPaso && pasosDisponibles == 0))) {
                wait();
            }
            if (detenido) {
                throw new InterruptedException("ejecución detenida");
            }
            if (pasoAPaso) {
                pasosDisponibles--;
            }
        }
        int demora = pasoAPaso ? 0 : demoraMs;
        if (demora > 0) {
            Thread.sleep(demora);
        }
    }
}
