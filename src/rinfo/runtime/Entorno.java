package rinfo.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import rinfo.ast.Tipo;

/**
 * Conjunto de variables visibles en un punto del programa.
 *
 * <p>El alcance de r-Info es plano: el cuerpo de un robot ve sus propias
 * variables y un proceso ve únicamente sus parámetros formales y sus locales.
 * Por eso no hay cadena de entornos padre.
 */
public final class Entorno {

    private static final class Celda {
        final Tipo tipo;
        Object valor;

        Celda(Tipo tipo, Object valor) {
            this.tipo = tipo;
            this.valor = valor;
        }
    }

    private final Map<String, Celda> celdas = new LinkedHashMap<>();

    /** Declara una variable con su valor inicial: 0 para numero, F para boolean. */
    public void declarar(String nombre, Tipo tipo) {
        celdas.put(nombre, new Celda(tipo, tipo == Tipo.BOOLEAN ? Boolean.FALSE : Integer.valueOf(0)));
    }

    public boolean contiene(String nombre) {
        return celdas.containsKey(nombre);
    }

    public Tipo tipoDe(String nombre) {
        Celda c = celdas.get(nombre);
        return c == null ? null : c.tipo;
    }

    public Object leer(String nombre) throws ErrorEjecucion {
        Celda c = celdas.get(nombre);
        if (c == null) {
            throw new ErrorEjecucion("la variable '" + nombre + "' no está declarada");
        }
        return c.valor;
    }

    public void escribir(String nombre, Object valor) throws ErrorEjecucion {
        Celda c = celdas.get(nombre);
        if (c == null) {
            throw new ErrorEjecucion("la variable '" + nombre + "' no está declarada");
        }
        if (c.tipo == Tipo.NUMERO && !(valor instanceof Integer)) {
            throw new ErrorEjecucion("se esperaba un valor numérico para la variable '" + nombre + "'");
        }
        if (c.tipo == Tipo.BOOLEAN && !(valor instanceof Boolean)) {
            throw new ErrorEjecucion("se esperaba un valor booleano para la variable '" + nombre + "'");
        }
        c.valor = valor;
    }

    public Set<String> nombres() {
        return celdas.keySet();
    }

    /** Copia inmutable para mostrar en el inspector de variables. */
    public Map<String, Object> instantanea() {
        Map<String, Object> copia = new LinkedHashMap<>();
        celdas.forEach((nombre, celda) -> copia.put(nombre, celda.valor));
        return copia;
    }
}
