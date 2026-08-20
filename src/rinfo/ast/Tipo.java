package rinfo.ast;

/** Tipos de dato declarables en r-Info. */
public enum Tipo {
    NUMERO("numero"),
    BOOLEAN("boolean"),
    /** Variable cuyo tipo es un tipo de robot declarado en el bloque {@code robots}. */
    ROBOT("robot");

    public final String nombre;

    Tipo(String nombre) {
        this.nombre = nombre;
    }

    @Override
    public String toString() {
        return nombre;
    }
}
