package rinfo.runtime;

/** Qué se puede colocar en una esquina antes de arrancar el programa. */
public enum Contenido {
    FLOR("flor", "flores"),
    PAPEL("papel", "papeles"),
    OBSTACULO("obstáculo", "obstáculos");

    public final String singular;
    public final String plural;

    Contenido(String singular, String plural) {
        this.singular = singular;
        this.plural = plural;
    }

    @Override
    public String toString() {
        return singular;
    }
}
