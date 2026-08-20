package rinfo.ast;

/** Expresiones de r-Info. */
public sealed interface Expr {

    /** Línea del fuente donde arranca la expresión, para reportar errores. */
    int fila();

    record Numero(int valor, int fila) implements Expr {}

    record Booleano(boolean valor, int fila) implements Expr {}

    /** Referencia a una variable o a un parámetro formal. */
    record Var(String nombre, int fila) implements Expr {}

    record Binaria(Operador op, Expr izq, Expr der, int fila) implements Expr {}

    record Unaria(Operador op, Expr operando, int fila) implements Expr {}

    /** Consulta del entorno del robot: {@code PosAv}, {@code HayObstaculo}, etc. */
    record Consulta(Sensor sensor, int fila) implements Expr {}
}
