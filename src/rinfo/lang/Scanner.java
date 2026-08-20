package rinfo.lang;

/**
 * Analizador léxico de r-Info.
 *
 * <p>El lenguaje delimita los bloques por indentación: cada nivel son
 * exactamente dos espacios y el scanner emite {@link TipoToken#INDENT} /
 * {@link TipoToken#DEDENT} sintéticos al cambiar de nivel, igual que el
 * intérprete original. Una línea que arranca con una llave de apertura
 * (comentario) hereda la indentación de la línea anterior y por lo tanto no
 * abre ni cierra bloques.
 */
public final class Scanner {

    /** Se lanza internamente al agotar el fuente; se traduce a EOT. */
    private static final class FinArchivo extends Exception {
        private static final FinArchivo INSTANCIA = new FinArchivo();
    }

    private final String fuente;
    private int pos;
    private char caracterActual;
    private StringBuilder escrituraActual = new StringBuilder();

    private int indent;
    private int dedent;
    private int espaciosLineaActual;
    private int espaciosLineaAnterior;

    private int fila = 1;
    private int columna = 1;

    /** Fila y columna donde arranca el token que se está armando. */
    private int filaToken = 1;
    private int columnaToken = 1;

    public Scanner(String fuente) {
        this.fuente = fuente + "\n";
        try {
            siguienteCaracter();
        } catch (FinArchivo e) {
            caracterActual = '\0';
        }
    }

    private void siguienteCaracter() throws FinArchivo {
        if (pos >= fuente.length()) {
            throw FinArchivo.INSTANCIA;
        }
        caracterActual = fuente.charAt(pos++);
        columna++;
    }

    private void tomar() throws FinArchivo {
        escrituraActual.append(caracterActual);
        siguienteCaracter();
    }

    private static boolean esLetra(char c) {
        return Character.isLetter(c);
    }

    private static boolean esDigito(char c) {
        return Character.isDigit(c);
    }

    /** Un identificador puede contener letras, dígitos, punto y guion bajo. */
    private static boolean continuaIdentificador(char c) {
        return esLetra(c) || esDigito(c) || c == '.' || c == '_';
    }

    public Token escanear() throws ErrorCompilacion {
        try {
            return escanearInterno();
        } catch (FinArchivo e) {
            return new Token(TipoToken.EOT, "EOT", fila, columna);
        }
    }

    private Token escanearInterno() throws FinArchivo, ErrorCompilacion {
        if (caracterActual == '\r') {
            siguienteCaracter();
        }
        if (caracterActual == '\n') {
            medirIndentacion();
        }
        if (indent > 0) {
            indent--;
            return new Token(TipoToken.INDENT, "INDENT", fila, columna);
        }
        if (dedent > 0) {
            dedent--;
            return new Token(TipoToken.DEDENT, "DEDENT", fila, columna);
        }

        escrituraActual = new StringBuilder();
        filaToken = fila;
        columnaToken = columna;
        TipoToken tipo = escanearToken();

        // El resto de los espacios en blanco de la línea no son significativos.
        while (caracterActual == ' ') {
            siguienteCaracter();
        }
        return new Token(tipo, escrituraActual.toString(), filaToken, columnaToken);
    }

    /**
     * Consume el salto de línea (y los que le sigan) y calcula cuántos INDENT o
     * DEDENT hay que emitir antes del próximo token real.
     */
    private void medirIndentacion() throws FinArchivo, ErrorCompilacion {
        espaciosLineaAnterior = espaciosLineaActual;
        while (true) {
            columna = 1;
            do {
                siguienteCaracter();
                fila++;
            } while (caracterActual == '\n');

            espaciosLineaActual = 0;
            while (caracterActual == ' ') {
                siguienteCaracter();
                espaciosLineaActual++;
            }
            if (caracterActual == '\n') {
                continue; // línea sólo con espacios: se ignora
            }
            break;
        }

        if (caracterActual == '{') {
            // Las líneas de comentario no cambian el nivel de indentación.
            espaciosLineaActual = espaciosLineaAnterior;
            return;
        }
        if (espaciosLineaActual % 2 != 0) {
            throw new ErrorCompilacion(
                    "error de indentación: " + espaciosLineaActual
                            + " espacios (la línea anterior tenía " + espaciosLineaAnterior
                            + "); cada nivel son 2 espacios",
                    fila, 1);
        }
        if (espaciosLineaActual > espaciosLineaAnterior) {
            indent = (espaciosLineaActual - espaciosLineaAnterior) / 2;
        } else if (espaciosLineaActual < espaciosLineaAnterior) {
            dedent = (espaciosLineaAnterior - espaciosLineaActual) / 2;
        }
    }

    private TipoToken escanearToken() throws FinArchivo, ErrorCompilacion {
        switch (caracterActual) {
            case '{': {
                do {
                    tomar();
                } while (caracterActual != '}');
                tomar();
                return TipoToken.COMENTARIO;
            }
            case '\'':
                tomar();
                return TipoToken.COMILLASSIMPLE;
            case '(':
                tomar();
                return TipoToken.LPAREN;
            case ')':
                tomar();
                return TipoToken.RPAREN;
            case ';':
                tomar();
                return TipoToken.PUNTOYCOMA;
            case ',':
                tomar();
                return TipoToken.COMA;
            case '+':
                tomar();
                return TipoToken.MAS;
            case '-':
                tomar();
                return TipoToken.MENOS;
            case '/':
                tomar();
                return TipoToken.DIV;
            case '*':
                tomar();
                return TipoToken.MULT;
            case '~':
                tomar();
                return TipoToken.NOT;
            case '&':
                tomar();
                return TipoToken.AND;
            case '|':
                tomar();
                return TipoToken.OR;
            case '=':
                tomar();
                return TipoToken.IGUAL;
            case ':':
                tomar();
                if (caracterActual == '=') {
                    tomar();
                    return TipoToken.ASIGNACION;
                }
                return TipoToken.DOSPUNTOS;
            case '<':
                tomar();
                if (caracterActual == '=') {
                    tomar();
                    return TipoToken.MENORIGUAL;
                }
                if (caracterActual == '>') {
                    tomar();
                    return TipoToken.DISTINTO;
                }
                return TipoToken.MENOR;
            case '>':
                tomar();
                if (caracterActual == '=') {
                    tomar();
                    return TipoToken.MAYORIGUAL;
                }
                return TipoToken.MAYOR;
            default:
                break;
        }
        if (esLetra(caracterActual)) {
            tomar();
            while (continuaIdentificador(caracterActual)) {
                tomar();
            }
            return TipoToken.IDENTIFER;
        }
        if (esDigito(caracterActual)) {
            tomar();
            while (esDigito(caracterActual)) {
                tomar();
            }
            return TipoToken.INTLITERAL;
        }
        throw new ErrorCompilacion("carácter no reconocido: '" + caracterActual + "'",
                filaToken, columnaToken);
    }
}
