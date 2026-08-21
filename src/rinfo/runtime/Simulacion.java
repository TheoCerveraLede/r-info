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
            }
            huboError = false;
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
            for (Thread t : hilos) {
                t.join();
            }
            if (!huboError && !control.estaDetenido()) {
                consola.traza("Ejecución finalizada.");
            }
        } catch (ErrorEjecucion e) {
            reportarError(e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            esquinas.limpiar();
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
            robot.setEstado("detenido");
            Thread.currentThread().interrupt();
        } finally {
            esquinas.liberarTodo(robot);
            contexto.notificarCambio();
        }
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
