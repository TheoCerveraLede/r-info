package rinfo.runtime;

/** Salida y entrada del programa en ejecución. */
public interface Consola {

    /** Muestra el resultado de un {@code Informar}. */
    void informar(String robot, String texto);

    /** Traza informativa de la simulación (arranque, fin, errores). */
    void traza(String texto);

    /**
     * Resuelve un {@code Leer(variable)} pidiendo un valor al usuario.
     *
     * @throws ErrorEjecucion si el usuario cancela o el valor no es válido
     */
    int leer(String robot, String variable) throws ErrorEjecucion;
}
