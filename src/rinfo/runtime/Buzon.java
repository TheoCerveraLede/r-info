package rinfo.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cola de mensajes de un robot.
 *
 * <p>{@code EnviarMensaje} deposita en el buzón del destinatario y sigue de
 * largo; {@code RecibirMensaje} bloquea al robot hasta que llegue un mensaje
 * del remitente que espera. Los mensajes de cada remitente se atienden en
 * orden de llegada.
 */
public final class Buzon {

    private final Map<String, Deque<Object>> porRemitente = new HashMap<>();

    public synchronized void depositar(String remitente, Object valor) {
        porRemitente.computeIfAbsent(remitente, k -> new ArrayDeque<>()).addLast(valor);
        notifyAll();
    }

    /**
     * Espera un mensaje de {@code remitente} y lo devuelve.
     *
     * @throws InterruptedException si se detiene la ejecución mientras espera
     */
    public synchronized Object recibir(String remitente) throws InterruptedException {
        Deque<Object> cola = porRemitente.computeIfAbsent(remitente, k -> new ArrayDeque<>());
        while (cola.isEmpty()) {
            wait();
        }
        return cola.removeFirst();
    }

    /** Mensajes pendientes, para mostrarlos en el inspector. */
    public synchronized List<String> pendientes() {
        List<String> resumen = new ArrayList<>();
        porRemitente.forEach((remitente, cola) -> {
            if (!cola.isEmpty()) {
                resumen.add(remitente + ": " + cola.size());
            }
        });
        return resumen;
    }

    public synchronized void limpiar() {
        porRemitente.clear();
    }
}
