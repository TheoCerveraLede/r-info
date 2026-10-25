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

    /** Un mensaje encolado en el buzón. */
    public record Mensaje(String remitente, Object valor) {}

    private final List<Mensaje> pendientes = new ArrayList<>();
    private String esperandoDe;
    private boolean abortado;

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
        return recibir(remitente, null);
    }

    /**
     * Espera un mensaje notificando a la vista si el robot queda bloqueado o
     * cuando finalmente consume el mensaje.
     */
    public synchronized Object recibir(String remitente, Runnable alCambiar)
            throws InterruptedException {
        int indice = buscar(remitente);
        if (indice >= 0) {
            Object valor = pendientes.remove(indice).valor();
            if (alCambiar != null) {
                alCambiar.run();
            }
            return valor;
        }
        esperandoDe = (remitente == null || remitente.isEmpty()) ? COMODIN : remitente;
        if (alCambiar != null) {
            alCambiar.run();
        }
        try {
            while (!abortado) {
                indice = buscar(remitente);
                if (indice >= 0) {
                    return pendientes.remove(indice).valor();
                }
                wait();
            }
            throw new InterruptedException("ejecución detenida");
        } finally {
            if (!abortado) {
                esperandoDe = null;
            }
            if (alCambiar != null) {
                alCambiar.run();
            }
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

    /** Indica si ya hay algún mensaje disponible que destrabe a {@code remitente}. */
    public synchronized boolean tieneMensajeDe(String remitente) {
        return buscar(remitente) >= 0;
    }

    /** Nombre del emisor al que este buzón está esperando, o {@code null} si no espera. */
    public synchronized String getEsperandoDe() {
        return esperandoDe;
    }

    /** Cantidad de mensajes sin leer en la cola. */
    public synchronized int cantidadPendientes() {
        return pendientes.size();
    }

    /** Copia en orden de llegada de los mensajes pendientes, para el inspector. */
    public synchronized List<Mensaje> instantanea() {
        return List.copyOf(pendientes);
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

    /** Despierta al hilo en espera sin borrar el estado visible (para interbloqueos). */
    public synchronized void abortar() {
        abortado = true;
        notifyAll();
    }

    public synchronized void limpiar() {
        pendientes.clear();
        esperandoDe = null;
        abortado = false;
        notifyAll();
    }
}
