package rinfo.lang;

/**
 * Clases de token del lenguaje r-Info.
 *
 * <p>El orden y la escritura de cada constante reproducen la tabla
 * {@code form.Token.spellings} del intérprete original: el scanner devuelve
 * {@link #IDENTIFER} para cualquier palabra y es el constructor de
 * {@link Token} el que la reclasifica buscando una coincidencia exacta
 * (sensible a mayúsculas) en esta tabla.
 */
public enum TipoToken {
    IDENTIFER("IDENTIFER"),
    INICIAR("Iniciar"),
    MOVER("mover"),
    DERECHA("derecha"),
    TOMARFLOR("tomarFlor"),
    TOMARPAPEL("tomarPapel"),
    DEPOSITARFLOR("depositarFlor"),
    DEPOSITARPAPEL("depositarPapel"),
    POSAV("PosAv"),
    POSCA("PosCa"),
    HAYFLORENLAESQUINA("HayFlorEnLaEsquina"),
    HAYFLORENLABOLSA("HayFlorEnLaBolsa"),
    HAYPAPELENLAESQUINA("HayPapelEnLaEsquina"),
    HAYPAPELENLABOLSA("HayPapelEnLaBolsa"),
    INFORMAR("Informar"),
    POS("Pos"),
    COMENZAR("comenzar"),
    VARIABLES("variables"),
    FIN("fin"),
    NUMERO("numero"),
    BOOLEAN("boolean"),
    LPAREN("("),
    RPAREN(")"),
    INTLITERAL("INTLITERAL"),
    COMA(","),
    ERROR("ERROR"),
    EOT("EOT"),
    EOL("EOL"),
    DOSPUNTOS(":"),
    PROGRAMA("programa"),
    IGUAL("="),
    ASIGNACION(":="),
    VERDADERO("V"),
    FALSO("F"),
    SI("si"),
    FINSI("finSi"),
    REPETIR("repetir"),
    MIENTRAS("mientras"),
    PROCESOS("procesos"),
    PROCESO("proceso"),
    FINREPETIR("finrepetir"),
    FINMIENTRAS("finmientras"),
    INDENT("INDENT"),
    DEDENT("DEDENT"),
    SINO("sino"),
    MAS("+"),
    MENOS("-"),
    DIV("/"),
    MULT("*"),
    NOT("~"),
    AND("&"),
    OR("|"),
    MENOR("<"),
    MAYOR(">"),
    DISTINTO("<>"),
    MAYORIGUAL(">="),
    MENORIGUAL("<="),
    ENTRADASALIDA("ES"),
    SALIDA("S"),
    ENTRADA("E"),
    PUNTOYCOMA(";"),
    LKEY("{"),
    RKEY("}"),
    HAYOBSTACULO("HayObstaculo"),
    OPERADOR("OP"),
    ROBOTS("robots"),
    ROBOT("robot"),
    ENVIARMENSAJE("EnviarMensaje"),
    RECIBIRMENSAJE("RecibirMensaje"),
    AREAS("areas"),
    AREAC("AreaC"),
    AREAP("AreaP"),
    AREAPC("AreaPC"),
    ASIGNARAREA("AsignarArea"),
    COMENTARIO("COMENTARIO"),
    LEER("Leer"),
    BLOQUEARESQUINA("BloquearEsquina"),
    LIBERARESQUINA("LiberarEsquina"),
    COMILLASSIMPLE("'"),
    RANDOM("Random");

    private static final TipoToken[] VALORES = values();

    public final String escritura;

    TipoToken(String escritura) {
        this.escritura = escritura;
    }

    /**
     * Busca la palabra reservada cuya escritura coincide exactamente con
     * {@code palabra}, o {@code null} si es un identificador común.
     * Se salta {@link #IDENTIFER} igual que el original, que recorre el rango
     * {@code [1, 79]}.
     */
    static TipoToken palabraReservada(String palabra) {
        for (int i = 1; i < VALORES.length; i++) {
            if (VALORES[i].escritura.equals(palabra)) {
                return VALORES[i];
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return escritura;
    }
}
