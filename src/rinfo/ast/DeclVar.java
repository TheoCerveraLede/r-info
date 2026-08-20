package rinfo.ast;

/**
 * Declaración de una variable.
 *
 * <p>{@code tipoRobot} sólo tiene valor cuando {@link #tipo()} es
 * {@link Tipo#ROBOT}, y nombra al tipo de robot declarado en el bloque
 * {@code robots}.
 */
public record DeclVar(String nombre, Tipo tipo, String tipoRobot) {

    public static DeclVar simple(String nombre, Tipo tipo) {
        return new DeclVar(nombre, tipo, null);
    }

    public static DeclVar deRobot(String nombre, String tipoRobot) {
        return new DeclVar(nombre, Tipo.ROBOT, tipoRobot);
    }
}
