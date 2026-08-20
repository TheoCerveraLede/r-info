package rinfo.ast;

/** Modo de pasaje de un parámetro formal. */
public enum Modo {
    /** {@code E}: entrada, se copia el valor al entrar. */
    ENTRADA("E"),
    /** {@code S}: salida, se copia el valor al salir. */
    SALIDA("S"),
    /** {@code ES}: entrada/salida, se copia al entrar y al salir. */
    ENTRADA_SALIDA("ES");

    public final String nombre;

    Modo(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
