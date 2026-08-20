package rinfo.runtime;

import java.awt.Color;

/**
 * Rectángulo de esquinas asignable a un robot.
 *
 * <p>Se declara como {@code nombre: AreaP(av1, ca1, av2, ca2)} y delimita las
 * únicas esquinas por las que ese robot puede circular.
 */
public final class Area {
    private final String nombre;
    private final TipoArea tipo;
    private final int av1;
    private final int ca1;
    private final int av2;
    private final int ca2;

    public Area(String nombre, TipoArea tipo, int av1, int ca1, int av2, int ca2) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.av1 = av1;
        this.ca1 = ca1;
        this.av2 = av2;
        this.ca2 = ca2;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoArea getTipo() {
        return tipo;
    }

    public int getAv1() {
        return av1;
    }

    public int getCa1() {
        return ca1;
    }

    public int getAv2() {
        return av2;
    }

    public int getCa2() {
        return ca2;
    }

    public Color getColor() {
        return tipo.color;
    }

    public boolean contiene(int av, int ca) {
        return av >= av1 && av <= av2 && ca >= ca1 && ca <= ca2;
    }

    /** Dos áreas se solapan si comparten al menos una esquina. */
    public boolean seSolapaCon(Area otra) {
        return av1 <= otra.av2 && otra.av1 <= av2 && ca1 <= otra.ca2 && otra.ca1 <= ca2;
    }

    @Override
    public String toString() {
        return nombre + ": " + tipo.nombre + "(" + av1 + ", " + ca1 + ", " + av2 + ", " + ca2 + ")";
    }
}
