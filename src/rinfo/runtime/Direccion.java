package rinfo.runtime;

/**
 * Orientación del robot.
 *
 * <p>Los grados reproducen las constantes de {@code form.Direction}: el norte
 * incrementa la calle y el este incrementa la avenida. Girar a la derecha
 * recorre norte, este, sur, oeste.
 */
public enum Direccion {
    NORTE(90, 0, 1),
    ESTE(0, 1, 0),
    SUR(270, 0, -1),
    OESTE(180, -1, 0);

    public final int grados;
    public final int deltaAv;
    public final int deltaCa;

    Direccion(int grados, int deltaAv, int deltaCa) {
        this.grados = grados;
        this.deltaAv = deltaAv;
        this.deltaCa = deltaCa;
    }

    /** Giro de 90 grados a la derecha. */
    public Direccion derecha() {
        return switch (this) {
            case NORTE -> ESTE;
            case ESTE -> SUR;
            case SUR -> OESTE;
            case OESTE -> NORTE;
        };
    }

    public static Direccion porGrados(int grados) {
        for (Direccion d : values()) {
            if (d.grados == grados) {
                return d;
            }
        }
        throw new IllegalArgumentException("dirección inválida: " + grados);
    }
}
