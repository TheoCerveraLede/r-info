package rinfo.ast;

/**
 * Operadores del lenguaje, con la precedencia que usa el parser original.
 *
 * <p>La implementación de referencia sólo distingue dos desempates:
 * {@code *} y {@code /} ligan más fuerte que {@code +} y {@code -}, y la
 * negación {@code ~} liga más fuerte que {@code &} y {@code |}. El resto de
 * los operadores comparten nivel y asocian a izquierda.
 */
public enum Operador {
    SUMA("+", 2),
    RESTA("-", 2),
    MULTIPLICACION("*", 3),
    DIVISION("/", 3),
    AND("&", 1),
    OR("|", 1),
    NOT("~", 4),
    IGUAL("=", 2),
    DISTINTO("<>", 2),
    MENOR("<", 2),
    MAYOR(">", 2),
    MENOR_IGUAL("<=", 2),
    MAYOR_IGUAL(">=", 2);

    public final String simbolo;
    public final int precedencia;

    Operador(String simbolo, int precedencia) {
        this.simbolo = simbolo;
        this.precedencia = precedencia;
    }

    public boolean esAritmetico() {
        return this == SUMA || this == RESTA || this == MULTIPLICACION || this == DIVISION;
    }

    public boolean esRelacional() {
        return this == IGUAL || this == DISTINTO || this == MENOR
                || this == MAYOR || this == MENOR_IGUAL || this == MAYOR_IGUAL;
    }

    public boolean esBooleano() {
        return this == AND || this == OR || this == NOT;
    }

    @Override
    public String toString() {
        return simbolo;
    }
}
