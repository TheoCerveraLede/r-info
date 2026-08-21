package rinfo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cola de mensajes de un robot.
 *
 * <p>{@code EnviarMensaje} deposita en el buzón del destinatario y sigue de
 * largo; {@code RecibirMensaje} bloquea al robot hasta que llegue un mensaje
 * del remitente que espera. Con el comodín {@value #COMODIN} espera un mensaje
 * de cualquiera y se lleva el más viejo.
 *
 * <p>Los mensajes se guardan en una sola lista en orden de llegada, no en una
 * cola por remitente: es lo que hace que el comodín devuelva efectivamente el
 * más viejo de todos.
 */
public final class Buzon {

    /** Remitente que acepta un mensaje de cualquier robot. */
    public static final String COMODIN = "*";

    private record Mensaje(String remitente, Object valor) {}

    private final List<Mensaje> pendientes = new ArrayList<>();

    public synchronized void depositar(String remitente, Object valor) {
        pendientes.add(new Mensaje(remitente, valor));
        notifyAll();
    }

    /**
     * Espera un mensaje y lo devuelve.
     *
     * @param remitente nombre del robot esperado, o {@code null} o
     *                  {@value #COMODIN} para aceptar el de cualquiera
     * @throws InterruptedException si se detiene la ejecución mientras espera
     */
    public synchronized Object recibir(String remitente) throws InterruptedException {
        while (true) {
            int indice = buscar(remitente);
            if (indice >= 0) {
                return pendientes.remove(indice).valor();
            }
            wait();
        }
    }

    /** Índice del mensaje más viejo que sirve, o -1 si no hay ninguno. */
    private int buscar(String remitente) {
        if (remitente == null || remitente.equals(COMODIN)) {
            return pendientes.isEmpty() ? -1 : 0;
        }
        for (int i = 0; i < pendientes.size(); i++) {
            if (pendientes.get(i).remitente().equals(remitente)) {
                return i;
            }
        }
        return -1;
    }

    /** Mensajes pendientes por remitente, para mostrarlos en el inspector. */
    public synchronized List<String> resumen() {
        Map<String, Integer> porRemitente = new LinkedHashMap<>();
        for (Mensaje m : pendientes) {
            porRemitente.merge(m.remitente(), 1, Integer::sum);
        }
        List<String> lineas = new ArrayList<>();
        porRemitente.forEach((remitente, cantidad) -> lineas.add(remitente + ": " + cantidad));
        return lineas;
    }

    public synchronized void limpiar() {
        pendientes.clear();
    }
}
