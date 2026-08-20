package rinfo.runtime;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import rinfo.ast.DeclVar;
import rinfo.ast.Expr;
import rinfo.ast.Modo;
import rinfo.ast.Operador;
import rinfo.ast.ParamFormal;
import rinfo.ast.Proceso;
import rinfo.ast.Sent;
import rinfo.ast.Tipo;

/**
 * Intérprete del cuerpo de un robot.
 *
 * <p>Hay una instancia por robot y corre en el hilo de ese robot. Los valores
 * son {@link Integer} o {@link Boolean}; el original los guardaba como texto
 * ("V" y "F" para los booleanos), que es la forma en que se siguen mostrando.
 */
public final class Interprete {

    private final Contexto ctx;
    private final Robot robot;
    private final Entorno variablesDelRobot;

    /** Entorno del proceso en ejecución, para el inspector de variables. */
    private volatile Entorno entornoActual;

    public Interprete(Contexto ctx, Robot robot, List<DeclVar> variables) {
        this.ctx = ctx;
        this.robot = robot;
        this.variablesDelRobot = new Entorno();
        for (DeclVar v : variables) {
            variablesDelRobot.declarar(v.nombre(), v.tipo());
        }
        this.entornoActual = variablesDelRobot;
    }

    public Entorno getVariablesDelRobot() {
        return variablesDelRobot;
    }

    public Entorno getEntornoActual() {
        return entornoActual;
    }

    public void ejecutarCuerpo(List<Sent> cuerpo) throws ErrorEjecucion, InterruptedException {
        ejecutar(cuerpo, variablesDelRobot);
    }

    // ------------------------------------------------------------------
    // Sentencias
    // ------------------------------------------------------------------

    private void ejecutar(List<Sent> sentencias, Entorno env)
            throws ErrorEjecucion, InterruptedException {
        for (Sent s : sentencias) {
            ejecutar(s, env);
        }
    }

    private void ejecutar(Sent sentencia, Entorno env) throws ErrorEjecucion, InterruptedException {
        switch (sentencia) {
            case Sent.Accion a -> ejecutarAccion(a);

            case Sent.Pos p -> {
                robot.pos(entero(p.av(), env), entero(p.ca(), env));
                tras();
            }

            case Sent.Asignacion a -> env.escribir(a.variable(), evaluar(a.valor(), env));

            case Sent.Si s -> {
                if (booleano(s.condicion(), env)) {
                    ejecutar(s.entonces(), env);
                } else {
                    ejecutar(s.sino(), env);
                }
            }

            case Sent.Mientras m -> {
                while (booleano(m.condicion(), env)) {
                    ejecutar(m.cuerpo(), env);
                }
            }

            case Sent.Repetir r -> {
                int veces = entero(r.veces(), env);
                for (int i = 0; i < veces; i++) {
                    ejecutar(r.cuerpo(), env);
                }
            }

            case Sent.LlamadaProceso ll -> invocar(ll, env);

            case Sent.Informar inf -> {
                StringBuilder texto = new StringBuilder();
                for (Object parte : inf.partes()) {
                    if (!texto.isEmpty()) {
                        texto.append(' ');
                    }
                    texto.append(parte instanceof Expr e ? mostrar(evaluar(e, env)) : parte);
                }
                ctx.consola.informar(robot.getNombre(), texto.toString());
            }

            case Sent.Leer l -> env.escribir(l.variable(),
                    ctx.consola.leer(robot.getNombre(), l.variable()));

            case Sent.Random r -> {
                int desde = entero(r.desde(), env);
                int hasta = entero(r.hasta(), env);
                if (desde > hasta) {
                    throw new ErrorEjecucion("Random: el mínimo (" + desde
                            + ") es mayor que el máximo (" + hasta + ")");
                }
                env.escribir(r.variable(), ThreadLocalRandom.current().nextInt(desde, hasta + 1));
            }

            case Sent.EnviarMensaje e -> {
                Robot destino = ctx.ciudad.getRobot(e.destino());
                if (destino == null) {
                    throw new ErrorEjecucion("EnviarMensaje: no existe el robot '" + e.destino() + "'");
                }
                destino.getBuzon().depositar(robot.getNombre(), evaluar(e.valor(), env));
            }

            case Sent.RecibirMensaje r -> {
                if (ctx.ciudad.getRobot(r.origen()) == null) {
                    throw new ErrorEjecucion("RecibirMensaje: no existe el robot '" + r.origen() + "'");
                }
                robot.setEstado("esperando mensaje de " + r.origen());
                Object valor = robot.getBuzon().recibir(r.origen());
                robot.setEstado("en ejecución");
                env.escribir(r.variable(), valor);
            }

            case Sent.BloquearEsquina b -> {
                int av = entero(b.av(), env);
                int ca = entero(b.ca(), env);
                robot.setEstado("esperando la esquina (" + av + ", " + ca + ")");
                ctx.esquinas.bloquear(robot, av, ca);
                robot.setEstado("en ejecución");
            }

            case Sent.LiberarEsquina l ->
                    ctx.esquinas.liberar(robot, entero(l.av(), env), entero(l.ca(), env));

            case Sent.AsignarArea ignored ->
                    throw new ErrorEjecucion("AsignarArea sólo se puede usar en el cuerpo del programa");

            case Sent.Iniciar ignored ->
                    throw new ErrorEjecucion("Iniciar sólo se puede usar en el cuerpo del programa");
        }
    }

    private void ejecutarAccion(Sent.Accion accion) throws ErrorEjecucion, InterruptedException {
        switch (accion.primitiva()) {
            case MOVER -> robot.mover();
            case DERECHA -> robot.derecha();
            case TOMAR_FLOR -> robot.tomarFlor();
            case TOMAR_PAPEL -> robot.tomarPapel();
            case DEPOSITAR_FLOR -> robot.depositarFlor();
            case DEPOSITAR_PAPEL -> robot.depositarPapel();
        }
        tras();
    }

    /** Refresca la vista y aplica velocidad, pausa o paso a paso. */
    private void tras() throws InterruptedException {
        ctx.notificarCambio();
        ctx.control.puntoDeControl();
    }

    // ------------------------------------------------------------------
    // Invocación de procesos
    // ------------------------------------------------------------------

    private void invocar(Sent.LlamadaProceso llamada, Entorno env)
            throws ErrorEjecucion, InterruptedException {
        Proceso proceso = ctx.programa.buscarProceso(llamada.nombre());
        if (proceso == null) {
            throw new ErrorEjecucion("no existe el proceso '" + llamada.nombre() + "'");
        }
        List<ParamFormal> formales = proceso.parametros();
        List<Expr> actuales = llamada.argumentos();
        if (formales.size() != actuales.size()) {
            throw new ErrorEjecucion("los parámetros actuales no coinciden con los formales "
                    + "en el proceso " + proceso.nombre() + " (esperaba " + formales.size()
                    + ", recibió " + actuales.size() + ")");
        }

        Entorno local = new Entorno();
        for (ParamFormal p : formales) {
            local.declarar(p.nombre(), p.tipo());
        }
        for (DeclVar v : proceso.locales()) {
            local.declarar(v.nombre(), v.tipo());
        }

        // Entrada: se copia el valor del argumento al parámetro.
        for (int i = 0; i < formales.size(); i++) {
            ParamFormal p = formales.get(i);
            if (p.modo() == Modo.ENTRADA || p.modo() == Modo.ENTRADA_SALIDA) {
                local.escribir(p.nombre(), evaluar(actuales.get(i), env));
            }
        }

        Entorno anterior = entornoActual;
        entornoActual = local;
        try {
            ejecutar(proceso.cuerpo(), local);
        } finally {
            entornoActual = anterior;
        }

        // Salida: se devuelve el valor del parámetro a la variable del llamador.
        for (int i = 0; i < formales.size(); i++) {
            ParamFormal p = formales.get(i);
            if (p.modo() == Modo.SALIDA || p.modo() == Modo.ENTRADA_SALIDA) {
                if (!(actuales.get(i) instanceof Expr.Var destino)) {
                    throw new ErrorEjecucion("el parámetro " + p.modo() + " '" + p.nombre()
                            + "' del proceso " + proceso.nombre() + " necesita una variable, "
                            + "no una expresión");
                }
                env.escribir(destino.nombre(), local.leer(p.nombre()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Expresiones
    // ------------------------------------------------------------------

    public Object evaluar(Expr expresion, Entorno env) throws ErrorEjecucion {
        return switch (expresion) {
            case Expr.Numero n -> n.valor();
            case Expr.Booleano b -> b.valor();
            case Expr.Var v -> env.leer(v.nombre());
            case Expr.Consulta c -> switch (c.sensor()) {
                case POS_AV -> robot.getAv();
                case POS_CA -> robot.getCa();
                case HAY_FLOR_EN_LA_ESQUINA -> robot.hayFlorEnLaEsquina();
                case HAY_FLOR_EN_LA_BOLSA -> robot.hayFlorEnLaBolsa();
                case HAY_PAPEL_EN_LA_ESQUINA -> robot.hayPapelEnLaEsquina();
                case HAY_PAPEL_EN_LA_BOLSA -> robot.hayPapelEnLaBolsa();
                case HAY_OBSTACULO -> robot.hayObstaculo();
            };
            case Expr.Unaria u -> aplicarUnario(u.op(), evaluar(u.operando(), env));
            case Expr.Binaria b -> aplicarBinario(b.op(), evaluar(b.izq(), env), evaluar(b.der(), env));
        };
    }

    private static Object aplicarUnario(Operador op, Object valor) throws ErrorEjecucion {
        return switch (op) {
            case NOT -> !exigirBooleano(valor, "la negación");
            case RESTA -> -exigirEntero(valor, "el menos unario");
            case SUMA -> exigirEntero(valor, "el más unario");
            default -> throw new ErrorEjecucion("el operador '" + op + "' no es unario");
        };
    }

    private static Object aplicarBinario(Operador op, Object izq, Object der) throws ErrorEjecucion {
        if (op.esAritmetico()) {
            int a = exigirEntero(izq, "el operador '" + op + "'");
            int b = exigirEntero(der, "el operador '" + op + "'");
            return switch (op) {
                case SUMA -> a + b;
                case RESTA -> a - b;
                case MULTIPLICACION -> a * b;
                case DIVISION -> {
                    if (b == 0) {
                        throw new ErrorEjecucion("división por cero");
                    }
                    yield a / b;
                }
                default -> throw new ErrorEjecucion("operador aritmético desconocido: " + op);
            };
        }
        if (op == Operador.AND || op == Operador.OR) {
            boolean a = exigirBooleano(izq, "el operador '" + op + "'");
            boolean b = exigirBooleano(der, "el operador '" + op + "'");
            return op == Operador.AND ? (a && b) : (a || b);
        }
        // Relacionales: = y <> valen para ambos tipos, el resto sólo para números.
        if (op == Operador.IGUAL || op == Operador.DISTINTO) {
            if (izq.getClass() != der.getClass()) {
                throw new ErrorEjecucion("no se pueden comparar un número y un booleano");
            }
            boolean iguales = izq.equals(der);
            return op == Operador.IGUAL ? iguales : !iguales;
        }
        int a = exigirEntero(izq, "el operador '" + op + "'");
        int b = exigirEntero(der, "el operador '" + op + "'");
        return switch (op) {
            case MENOR -> a < b;
            case MAYOR -> a > b;
            case MENOR_IGUAL -> a <= b;
            case MAYOR_IGUAL -> a >= b;
            default -> throw new ErrorEjecucion("operador desconocido: " + op);
        };
    }

    private int entero(Expr e, Entorno env) throws ErrorEjecucion {
        return exigirEntero(evaluar(e, env), "la expresión");
    }

    private boolean booleano(Expr e, Entorno env) throws ErrorEjecucion {
        return exigirBooleano(evaluar(e, env), "la condición");
    }

    private static int exigirEntero(Object valor, String contexto) throws ErrorEjecucion {
        if (valor instanceof Integer i) {
            return i;
        }
        throw new ErrorEjecucion("se esperaba un valor numérico en " + contexto);
    }

    private static boolean exigirBooleano(Object valor, String contexto) throws ErrorEjecucion {
        if (valor instanceof Boolean b) {
            return b;
        }
        throw new ErrorEjecucion("se esperaba un valor booleano en " + contexto);
    }

    /**
     * Evalúa una expresión que no puede depender de variables ni del robot.
     * Es lo que necesitan las coordenadas de {@code Iniciar} y de las áreas.
     */
    public static Object evaluarSinRobot(Expr expresion) throws ErrorEjecucion {
        return switch (expresion) {
            case Expr.Numero n -> n.valor();
            case Expr.Booleano b -> b.valor();
            case Expr.Unaria u -> aplicarUnario(u.op(), evaluarSinRobot(u.operando()));
            case Expr.Binaria b ->
                    aplicarBinario(b.op(), evaluarSinRobot(b.izq()), evaluarSinRobot(b.der()));
            case Expr.Var v -> throw new ErrorEjecucion(
                    "no se puede usar la variable '" + v.nombre() + "' en el cuerpo del programa");
            case Expr.Consulta c -> throw new ErrorEjecucion(
                    "no se puede consultar el entorno en el cuerpo del programa");
        };
    }

    /** Igual que {@link #evaluarSinRobot(Expr)} pero exigiendo un número. */
    public static int enteroSinRobot(Expr expresion) throws ErrorEjecucion {
        return exigirEntero(evaluarSinRobot(expresion), "la expresión");
    }

    /** Los booleanos se muestran como V y F, igual que en el fuente. */
    public static String mostrar(Object valor) {
        if (valor instanceof Boolean b) {
            return b ? "V" : "F";
        }
        return String.valueOf(valor);
    }

    /** Tipo de un valor ya calculado, para el inspector. */
    public static Tipo tipoDe(Object valor) {
        return valor instanceof Boolean ? Tipo.BOOLEAN : Tipo.NUMERO;
    }
}
