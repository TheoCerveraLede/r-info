package rinfo.lang;

/** Un token con su clase, su escritura literal y su posición en el fuente. */
public final class Token {
    public final TipoToken tipo;
    public final String escritura;
    public final int fila;
    public final int columna;

    public Token(TipoToken tipo, String escritura, int fila, int columna) {
        // Igual que el original: una palabra que coincide con una palabra
        // reservada deja de ser identificador.
        if (tipo == TipoToken.IDENTIFER) {
            TipoToken reservada = TipoToken.palabraReservada(escritura);
            if (reservada != null) {
                tipo = reservada;
            }
        }
        this.tipo = tipo;
        this.escritura = escritura;
        this.fila = fila;
        this.columna = columna;
    }

    public boolean es(TipoToken t) {
        return tipo == t;
    }

    @Override
    public String toString() {
        return tipo + "(" + escritura + ") @" + fila + ":" + columna;
    }
}
