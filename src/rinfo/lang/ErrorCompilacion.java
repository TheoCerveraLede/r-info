package rinfo.lang;

/** Error detectado durante el análisis léxico o sintáctico. */
public class ErrorCompilacion extends Exception {
    private final int fila;
    private final int columna;

    public ErrorCompilacion(String mensaje, int fila, int columna) {
        super(fila > 0 ? "línea " + fila + ", columna " + columna + ": " + mensaje : mensaje);
        this.fila = fila;
        this.columna = columna;
    }

    public int getFila() {
        return fila;
    }

    public int getColumna() {
        return columna;
    }
}
