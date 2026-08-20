package rinfo.runtime;

import rinfo.ast.Programa;

/** Todo lo que comparten los hilos de robot de una misma corrida. */
public final class Contexto {

    public final Programa programa;
    public final Ciudad ciudad;
    public final ControlEjecucion control;
    public final MonitorEsquinas esquinas;
    public final Consola consola;

    /** Se invoca después de cada acción visible para refrescar la vista. */
    private final Runnable alActualizar;

    public Contexto(Programa programa, Ciudad ciudad, ControlEjecucion control,
                    MonitorEsquinas esquinas, Consola consola, Runnable alActualizar) {
        this.programa = programa;
        this.ciudad = ciudad;
        this.control = control;
        this.esquinas = esquinas;
        this.consola = consola;
        this.alActualizar = alActualizar;
    }

    public void notificarCambio() {
        if (alActualizar != null) {
            alActualizar.run();
        }
    }
}
