package rinfo.runtime;

/** Error detectado mientras corre un programa (choque, salir de la ciudad, etc.). */
public class ErrorEjecucion extends Exception {
    public ErrorEjecucion(String mensaje) {
        super(mensaje);
    }
}
