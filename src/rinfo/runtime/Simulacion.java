package rinfo.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import rinfo.ast.DeclVar;
import rinfo.ast.Programa;
import rinfo.ast.Sent;
import rinfo.ast.Tipo;
import rinfo.ast.TipoRobot;

/**
 * Corrida de un programa: crea los robots declarados, ejecuta el cuerpo
 * principal (asignar áreas e iniciar robots) y lanza un hilo por robot.
 *
 * <p>El primer error de ejecución de cualquier robot aborta toda la corrida,
 * igual que en el intérprete original.
 */
public final class Simulacion {

    private final Programa programa;
    private final Ciudad ciudad;
    private final Consola consola;
    private final ControlEjecucion control = new ControlEjecucion();
    private final MonitorEsquinas esquinas = new MonitorEsquinas();
    private final Contexto contexto;

    private final Map<Robot, Interprete> interpretes = new LinkedHashMap<>();
    private final Map<Robot, Thread> hilosPorRobot = new LinkedHashMap<>();
    private final List<Thread> hilos = new CopyOnWriteArrayList<>();

    private Thread hiloPrincipal;
    private volatile boolean huboError;
    private Runnable alTerminar;
    private boolean preparada;

    public Simulacion(Programa programa, Ciudad ciudad, Consola consola, Runnable alActualizar) {
        this.programa = programa;
        this.ciudad = ciudad;
        this.consola = consola;
        this.contexto = new Contexto(programa, ciudad, control, esquinas, consola, alActualizar);
    }

    public ControlEjecucion getControl() {
        return control;
    }

    public MonitorEsquinas getEsquinas() {
        return esquinas;
    }

    public synchronized Map<Robot, Interprete> getInterpretes() {
        return new LinkedHashMap<>(interpretes);
    }

    public void setAlTerminar(Runnable alTerminar) {
        this.alTerminar = alTerminar;
    }

    public boolean estaCorriendo() {
        return hiloPrincipal != null && hiloPrincipal.isAlive();
    }

    /**
     * Crea los robots declarados y registra las áreas, sin ejecutar nada.
     *
     * <p>Se llama apenas compila, para que los robots existan antes de correr
     * y se les pueda configurar el contenido inicial de la bolsa.
     */
    public void preparar() {
        esquinas.limpiar();
        ciudad.limpiarEjecucion();
        ciudad.setAreas(programa.areas());
        for (DeclVar v : programa.variables()) {
            if (v.tipo() == Tipo.ROBOT) {
                ciudad.agregarRobot(v.nombre(), v.tipoRobot()).setEstado("listo");
            }
        }
        preparada = true;
        contexto.notificarCambio();
    }

    /** Arranca la corrida en segundo plano. */
    public void iniciar() {
        hiloPrincipal = new Thread(this::correr, "rinfo-programa");
        hiloPrincipal.setDaemon(true);
        hiloPrincipal.start();
    }

    /** Corta la corrida y espera a que los hilos de robot suelten sus recursos. */
    public void detener() {
        control.detener();
        for (Thread t : hilos) {
            t.interrupt();
        }
        if (hiloPrincipal != null) {
            hiloPrincipal.interrupt();
        }
        esquinas.limpiar();
    }

    private void correr() {
        try {
            // Estado de una corrida anterior: la misma Simulacion se reutiliza
            // mientras el fuente no cambie, así se puede volver a ejecutar.
            hilos.clear();
            synchronized (this) {
                interpretes.clear();
                hilosPorRobot.clear();
            }
            huboError = false;
            esquinas.limpiar();
            control.reiniciar();

            if (!preparada) {
                preparar();
            }
            // Deja las bolsas en su valor configurado y borra áreas y recorrido
            // de una corrida anterior, para poder volver a ejecutar sin recompilar.
            for (Robot robot : ciudad.getRobots()) {
                robot.reset();
            }
            contexto.notificarCambio();
            ejecutarCuerpoPrincipal();
            vigilarHilos();
            if (!huboError && !control.estaDetenido()) {
                consola.traza("Ejecución finalizada.");
            }
        } catch (ErrorEjecucion e) {
            reportarError(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!huboError) {
                esquinas.limpiar();
            }
            contexto.notificarCambio();
            if (alTerminar != null) {
                alTerminar.run();
            }
        }
    }

    private void ejecutarCuerpoPrincipal() throws ErrorEjecucion {
        for (Sent sentencia : programa.cuerpo()) {
            if (control.estaDetenido()) {
                return;
            }
            switch (sentencia) {
                case Sent.AsignarArea a -> {
                    Robot robot = exigirRobot(a.robot());
                    Area area = ciudad.getArea(a.area());
                    if (area == null) {
                        throw new ErrorEjecucion("no existe el área '" + a.area() + "'");
                    }
                    robot.asignarArea(area);
                }
                case Sent.Iniciar i -> iniciarRobot(i);
                default -> throw new ErrorEjecucion(
                        "el cuerpo del programa sólo admite AsignarArea e Iniciar");
            }
        }
    }

    private Robot exigirRobot(String nombre) throws ErrorEjecucion {
        Robot robot = ciudad.getRobot(nombre);
        if (robot == null) {
            throw new ErrorEjecucion("el robot '" + nombre + "' no está declarado en variables");
        }
        return robot;
    }

    private void iniciarRobot(Sent.Iniciar sentencia) throws ErrorEjecucion {
        Robot robot = exigirRobot(sentencia.robot());
        if (robot.sinArea()) {
            throw new ErrorEjecucion("el robot " + robot.getNombre() + " no tiene área asignada");
        }
        TipoRobot tipo = programa.buscarTipoRobot(robot.getTipoRobot());
        if (tipo == null) {
            throw new ErrorEjecucion("no existe el tipo de robot '" + robot.getTipoRobot() + "'");
        }

        int av = Interprete.enteroSinRobot(sentencia.av());
        int ca = Interprete.enteroSinRobot(sentencia.ca());
        robot.iniciar(av, ca);

        Interprete interprete = new Interprete(contexto, robot, tipo.variables());
        synchronized (this) {
            interpretes.put(robot, interprete);
        }
        contexto.notificarCambio();

        Thread hilo = new Thread(() -> correrRobot(robot, interprete, tipo), "rinfo-" + robot.getNombre());
        hilo.setDaemon(true);
        synchronized (this) {
            hilosPorRobot.put(robot, hilo);
        }
        hilos.add(hilo);
        hilo.start();
    }

    private void correrRobot(Robot robot, Interprete interprete, TipoRobot tipo) {
        try {
            interprete.ejecutarCuerpo(tipo.cuerpo());
            robot.setEstado("terminado");
        } catch (ErrorEjecucion e) {
            robot.setEstado("con error");
            reportarError(e.getMessage());
        } catch (InterruptedException e) {
            if (!"interbloqueo".equals(robot.getEstado())) {
                robot.setEstado("detenido");
            }
            Thread.currentThread().interrupt();
        } finally {
            if (!huboError && !control.estaDetenido()) {
                esquinas.liberarTodo(robot);
            }
            contexto.notificarCambio();
        }
    }

    // ------------------------------------------------------------------
    // Vigilancia de concurrencia y detección de interbloqueos (deadlocks)
    // ------------------------------------------------------------------

    private record DiagnosticoInterbloqueo(String mensaje, List<Robot> involucrados) {}

    private void vigilarHilos() throws InterruptedException {
        while (!huboError && !control.estaDetenido() && algunHiloVivo()) {
            Thread.sleep(50);
            if (huboError || control.estaDetenido()) {
                break;
            }
            DiagnosticoInterbloqueo candidato = diagnosticarInterbloqueo();
            if (candidato != null) {
                // Segunda comprobación breve para descartar transiciones entre hilos.
                Thread.sleep(80);
                DiagnosticoInterbloqueo confirmado = diagnosticarInterbloqueo();
                if (confirmado != null) {
                    for (Robot r : confirmado.involucrados()) {
                        r.setEstado("interbloqueo");
                    }
                    esquinas.abortar();
                    for (Robot r : ciudad.getRobots()) {
                        r.getBuzon().abortar();
                    }
                    reportarError(confirmado.mensaje());
                    break;
                }
            }
        }
        for (Thread t : hilos) {
            t.join(200);
        }
    }

    private boolean algunHiloVivo() {
        for (Thread t : hilos) {
            if (t.isAlive()) {
                return true;
            }
        }
        return false;
    }

    private synchronized Thread hiloDe(Robot robot) {
        return hilosPorRobot.get(robot);
    }

    private boolean hiloVivo(Robot robot) {
        Thread t = hiloDe(robot);
        return t != null && t.isAlive();
    }

    /**
     * Indica si {@code robot} está detenido en un monitor de concurrencia
     * ({@code BloquearEsquina} o {@code RecibirMensaje}).
     */
    private boolean esperandoConcurrencia(Robot robot) {
        Thread t = hiloDe(robot);
        if (t == null || !t.isAlive() || t.getState() != Thread.State.WAITING) {
            return false;
        }
        if (esquinas.esperaDe(robot) != null) {
            return true;
        }
        String emisor = robot.getBuzon().getEsperandoDe();
        return emisor != null && !robot.getBuzon().tieneMensajeDe(emisor);
    }

    /** Robot concreto del que depende {@code robot} para destrabarse, o {@code null}. */
    private Robot dependenciaDirecta(Robot robot) {
        var espEsquina = esquinas.esperaDe(robot);
        if (espEsquina != null) {
            return espEsquina.duenio();
        }
        String emisor = robot.getBuzon().getEsperandoDe();
        if (emisor != null && !Buzon.COMODIN.equals(emisor)) {
            return ciudad.getRobot(emisor);
        }
        return null;
    }

    private String describirEspera(Robot robot) {
        String linea = robot.getLineaActual() > 0 ? " (línea " + robot.getLineaActual() + ")" : "";
        var espEsquina = esquinas.esperaDe(robot);
        if (espEsquina != null) {
            String duenio = espEsquina.duenio() != null
                    ? " retenida por " + espEsquina.duenio().getNombre()
                    : "";
            return robot.getNombre() + linea + " espera la esquina ("
                    + espEsquina.av() + ", " + espEsquina.ca() + ")" + duenio;
        }
        String emisor = robot.getBuzon().getEsperandoDe();
        if (emisor != null) {
            String quien = Buzon.COMODIN.equals(emisor) ? "cualquier robot (*)" : emisor;
            return robot.getNombre() + linea + " espera un mensaje de " + quien;
        }
        return robot.getNombre() + linea;
    }

    private DiagnosticoInterbloqueo diagnosticarInterbloqueo() {
        List<Robot> todos = ciudad.getRobots();

        // 1. Ciclos de espera directa o espera a un robot ya finalizado.
        for (Robot inicio : todos) {
            if (!esperandoConcurrencia(inicio)) {
                continue;
            }
            List<Robot> camino = new ArrayList<>();
            Robot actual = inicio;
            while (actual != null && esperandoConcurrencia(actual)) {
                int idx = camino.indexOf(actual);
                if (idx >= 0) {
                    List<Robot> ciclo = List.copyOf(camino.subList(idx, camino.size()));
                    StringBuilder sb = new StringBuilder("interbloqueo (deadlock) detectado: ");
                    for (int i = 0; i < ciclo.size(); i++) {
                        if (i > 0) {
                            sb.append(" -> ");
                        }
                        sb.append(describirEspera(ciclo.get(i)));
                    }
                    sb.append(" -> ").append(actual.getNombre());
                    return new DiagnosticoInterbloqueo(sb.toString(), ciclo);
                }
                camino.add(actual);
                Robot siguiente = dependenciaDirecta(actual);
                if (siguiente != null && !hiloVivo(siguiente)) {
                    // Si esperaba mensaje de un robot que ya terminó y no dejó mensaje en el buzón.
                    String esp = actual.getBuzon().getEsperandoDe();
                    if (esp != null && !actual.getBuzon().tieneMensajeDe(esp)) {
                        String msg = "bloqueo permanente: " + describirEspera(actual)
                                + ", pero " + siguiente.getNombre() + " ya " + siguiente.getEstado();
                        return new DiagnosticoInterbloqueo(msg, List.copyOf(camino));
                    }
                }
                actual = siguiente;
            }
        }

        // 2. Bloqueo global: todos los robots vivos están bloqueados en concurrencia
        // (por ejemplo, esperando RecibirMensaje con comodín * sin emisores activos).
        List<Robot> vivos = new ArrayList<>();
        for (Robot r : todos) {
            if (hiloVivo(r)) {
                vivos.add(r);
            }
        }
        if (!vivos.isEmpty()) {
            boolean todosBloqueados = true;
            for (Robot r : vivos) {
                if (!esperandoConcurrencia(r)) {
                    todosBloqueados = false;
                    break;
                }
            }
            if (todosBloqueados) {
                StringBuilder sb = new StringBuilder("interbloqueo (deadlock) detectado: ");
                for (int i = 0; i < vivos.size(); i++) {
                    if (i > 0) {
                        sb.append("; ");
                    }
                    sb.append(describirEspera(vivos.get(i)));
                }
                return new DiagnosticoInterbloqueo(sb.toString(), List.copyOf(vivos));
            }
        }

        return null;
    }

    /** El primer error corta la corrida entera. */
    private void reportarError(String mensaje) {
        if (huboError) {
            return;
        }
        huboError = true;
        consola.traza("Error de ejecución: " + mensaje);
        control.detener();
        for (Thread t : new ArrayList<>(hilos)) {
            t.interrupt();
        }
    }
}
