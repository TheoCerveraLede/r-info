package rinfo.ast;

import java.util.List;

/** Sentencias de r-Info. */
public sealed interface Sent {

    /** Línea del fuente donde arranca la sentencia. */
    int fila();

    // --- Sentencias de control y de cálculo -----------------------------

    record Asignacion(String variable, Expr valor, int fila) implements Sent {}

    record Si(Expr condicion, List<Sent> entonces, List<Sent> sino, int fila) implements Sent {}

    record Mientras(Expr condicion, List<Sent> cuerpo, int fila) implements Sent {}

    record Repetir(Expr veces, List<Sent> cuerpo, int fila) implements Sent {}

    record LlamadaProceso(String nombre, List<Expr> argumentos, int fila) implements Sent {}

    // --- Primitivas del robot -------------------------------------------

    /** {@code mover}, {@code derecha}, {@code tomarFlor}, ... */
    record Accion(Primitiva primitiva, int fila) implements Sent {}

    /** {@code Pos(av, ca)}: reubica al robot dentro de su área. */
    record Pos(Expr av, Expr ca, int fila) implements Sent {}

    /**
     * {@code Informar('texto', expr, ...)}.
     * Cada parte es un {@link String} literal o una {@link Expr}.
     */
    record Informar(List<Object> partes, int fila) implements Sent {}

    /** {@code Leer(variable)}: pide un valor por pantalla. */
    record Leer(String variable, int fila) implements Sent {}

    /** {@code Random(variable, desde, hasta)}. */
    record Random(String variable, Expr desde, Expr hasta, int fila) implements Sent {}

    // --- Concurrencia -----------------------------------------------------

    record EnviarMensaje(Expr valor, String destino, int fila) implements Sent {}

    record RecibirMensaje(String variable, String origen, int fila) implements Sent {}

    record BloquearEsquina(Expr av, Expr ca, int fila) implements Sent {}

    record LiberarEsquina(Expr av, Expr ca, int fila) implements Sent {}

    // --- Sólo válidas en el cuerpo del programa principal -----------------

    record AsignarArea(String robot, String area, int fila) implements Sent {}

    record Iniciar(String robot, Expr av, Expr ca, int fila) implements Sent {}
}
