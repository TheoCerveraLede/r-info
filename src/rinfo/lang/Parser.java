package rinfo.lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import rinfo.ast.DeclVar;
import rinfo.ast.Expr;
import rinfo.ast.Modo;
import rinfo.ast.Operador;
import rinfo.ast.ParamFormal;
import rinfo.ast.Primitiva;
import rinfo.ast.Proceso;
import rinfo.ast.Programa;
import rinfo.ast.Sensor;
import rinfo.ast.Sent;
import rinfo.ast.Tipo;
import rinfo.ast.TipoRobot;
import rinfo.runtime.Area;
import rinfo.runtime.Buzon;
import rinfo.runtime.Ciudad;
import rinfo.runtime.TipoArea;

/**
 * Analizador sintáctico de r-Info: descendente recursivo para las
 * declaraciones y las sentencias, y {@code shunting-yard} para las
 * expresiones.
 *
 * <p>Una expresión termina donde termina su línea: el parser deja de consumir
 * tokens en cuanto aparece uno de otra fila, que es lo que permite escribir
 * {@code si HayFlorEnLaEsquina} sin ningún delimitador de cierre.
 */
public final class Parser {

    private final Scanner scanner;
    private Token tokenActual;

    private final List<Proceso> procesos = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private final List<TipoRobot> tiposRobot = new ArrayList<>();

    public Parser(String fuente) throws ErrorCompilacion {
        this.scanner = new Scanner(fuente);
        siguienteToken();
    }

    /** Atajo: compila un fuente y devuelve su árbol. */
    public static Programa compilar(String fuente) throws ErrorCompilacion {
        return new Parser(fuente).parse();
    }

    // ------------------------------------------------------------------
    // Manejo del flujo de tokens
    // ------------------------------------------------------------------

    private void siguienteToken() throws ErrorCompilacion {
        do {
            tokenActual = scanner.escanear();
        } while (tokenActual.es(TipoToken.COMENTARIO));
    }

    private void tomar(TipoToken esperado) throws ErrorCompilacion {
        if (!tokenActual.es(esperado)) {
            throw error("se esperaba " + esperado.escritura + " en lugar de " + descripcion(tokenActual));
        }
        siguienteToken();
    }

    private String tomarIdentificador() throws ErrorCompilacion {
        if (!tokenActual.es(TipoToken.IDENTIFER)) {
            throw error("se esperaba un identificador en lugar de " + descripcion(tokenActual));
        }
        String nombre = tokenActual.escritura;
        siguienteToken();
        return nombre;
    }

    /**
     * Remitente de un {@code RecibirMensaje}: el nombre de un robot o el
     * comodín {@code *}, que acepta un mensaje de cualquiera.
     */
    private String tomarRemitente() throws ErrorCompilacion {
        if (tokenActual.es(TipoToken.MULT)) {
            siguienteToken();
            return Buzon.COMODIN;
        }
        if (!tokenActual.es(TipoToken.IDENTIFER)) {
            throw error("se esperaba el nombre de un robot o el comodín " + Buzon.COMODIN
                    + " en lugar de " + descripcion(tokenActual));
        }
        String nombre = tokenActual.escritura;
        siguienteToken();
        return nombre;
    }

    private static String descripcion(Token t) {
        return switch (t.tipo) {
            case INDENT -> "una sangría mayor";
            case DEDENT -> "una sangría menor";
            case EOT -> "el fin del archivo";
            default -> "'" + t.escritura + "'";
        };
    }

    private ErrorCompilacion error(String mensaje) {
        return new ErrorCompilacion(mensaje, tokenActual.fila, tokenActual.columna);
    }

    // ------------------------------------------------------------------
    // Programa
    // ------------------------------------------------------------------

    public Programa parse() throws ErrorCompilacion {
        tomar(TipoToken.PROGRAMA);
        String nombre = tomarIdentificador();

        if (tokenActual.es(TipoToken.PROCESOS)) {
            parseProcesos();
        }
        if (tokenActual.es(TipoToken.AREAS)) {
            parseAreas();
        }
        if (tokenActual.es(TipoToken.ROBOTS)) {
            parseRobots();
        }
        List<DeclVar> variables = tokenActual.es(TipoToken.VARIABLES)
                ? parseVariables(true, List.of())
                : List.of();

        List<Sent> cuerpo = parseCuerpoPrograma(variables);
        return new Programa(nombre, List.copyOf(procesos), List.copyOf(areas),
                List.copyOf(tiposRobot), variables, cuerpo);
    }

    /**
     * Cuerpo del programa principal. Sólo admite asignar áreas e iniciar
     * robots: toda la lógica vive dentro de los robots.
     */
    private List<Sent> parseCuerpoPrograma(List<DeclVar> variables) throws ErrorCompilacion {
        List<Sent> sentencias = new ArrayList<>();
        tomar(TipoToken.COMENZAR);
        tomar(TipoToken.INDENT);

        while (tokenActual.es(TipoToken.ASIGNARAREA) || tokenActual.es(TipoToken.INICIAR)) {
            int fila = tokenActual.fila;
            if (tokenActual.es(TipoToken.ASIGNARAREA)) {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                String robot = tomarIdentificador();
                tomar(TipoToken.COMA);
                String area = tomarIdentificador();
                tomar(TipoToken.RPAREN);
                verificarVariableDeRobot(robot, variables, fila);
                if (buscarArea(area) == null) {
                    throw new ErrorCompilacion("el área '" + area + "' no está declarada", fila, 1);
                }
                sentencias.add(new Sent.AsignarArea(robot, area, fila));
            } else {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                String robot = tomarIdentificador();
                tomar(TipoToken.COMA);
                Expr av = parseExpresion();
                tomar(TipoToken.COMA);
                Expr ca = parseExpresion();
                tomar(TipoToken.RPAREN);
                verificarVariableDeRobot(robot, variables, fila);
                sentencias.add(new Sent.Iniciar(robot, av, ca, fila));
            }
        }

        tomar(TipoToken.DEDENT);
        tomar(TipoToken.FIN);
        return sentencias;
    }

    private void verificarVariableDeRobot(String nombre, List<DeclVar> variables, int fila)
            throws ErrorCompilacion {
        for (DeclVar v : variables) {
            if (v.nombre().equals(nombre)) {
                if (v.tipo() != Tipo.ROBOT) {
                    throw new ErrorCompilacion("'" + nombre + "' no es una variable de tipo robot", fila, 1);
                }
                return;
            }
        }
        throw new ErrorCompilacion("el robot '" + nombre + "' tiene que estar declarado en variables", fila, 1);
    }

    // ------------------------------------------------------------------
    // Bloque procesos
    // ------------------------------------------------------------------

    private void parseProcesos() throws ErrorCompilacion {
        siguienteToken(); // 'procesos'
        tomar(TipoToken.INDENT);
        do {
            Proceso p = parseUnProceso();
            for (Proceso previo : procesos) {
                if (previo.nombre().equals(p.nombre())) {
                    throw error("no se puede declarar dos procesos con el mismo nombre: " + p.nombre());
                }
            }
            procesos.add(p);
        } while (!tokenActual.es(TipoToken.INDENT) && !tokenActual.es(TipoToken.DEDENT));
        tomar(TipoToken.DEDENT);
    }

    private Proceso parseUnProceso() throws ErrorCompilacion {
        tomar(TipoToken.PROCESO);
        String nombre = tomarIdentificador();

        List<ParamFormal> parametros = new ArrayList<>();
        if (tokenActual.es(TipoToken.LPAREN)) {
            siguienteToken();
            while (!tokenActual.es(TipoToken.RPAREN)) {
                parametros.add(parseParametroFormal());
                if (tokenActual.es(TipoToken.PUNTOYCOMA) || tokenActual.es(TipoToken.COMA)) {
                    siguienteToken();
                }
            }
            tomar(TipoToken.RPAREN);
        }

        List<DeclVar> locales = tokenActual.es(TipoToken.VARIABLES)
                ? parseVariables(false, parametros)
                : List.of();

        List<Sent> cuerpo = parseCuerpoConComenzar();
        return new Proceso(nombre, List.copyOf(parametros), locales, cuerpo);
    }

    private ParamFormal parseParametroFormal() throws ErrorCompilacion {
        Modo modo = switch (tokenActual.tipo) {
            case ENTRADA -> Modo.ENTRADA;
            case SALIDA -> Modo.SALIDA;
            case ENTRADASALIDA -> Modo.ENTRADA_SALIDA;
            default -> throw error("se esperaba un parámetro formal (E, S o ES)");
        };
        siguienteToken();
        String nombre = tomarIdentificador();
        tomar(TipoToken.DOSPUNTOS);
        Tipo tipo = switch (tokenActual.tipo) {
            case NUMERO -> Tipo.NUMERO;
            case BOOLEAN -> Tipo.BOOLEAN;
            default -> throw error("hay que definir el tipo del parámetro formal '" + nombre + "'");
        };
        siguienteToken();
        return new ParamFormal(modo, nombre, tipo);
    }

    // ------------------------------------------------------------------
    // Bloque areas
    // ------------------------------------------------------------------

    private void parseAreas() throws ErrorCompilacion {
        siguienteToken(); // 'areas'
        tomar(TipoToken.INDENT);

        while (tokenActual.es(TipoToken.IDENTIFER)) {
            int fila = tokenActual.fila;
            String nombre = tomarIdentificador();
            tomar(TipoToken.DOSPUNTOS);

            TipoArea tipo = switch (tokenActual.tipo) {
                case AREAC -> TipoArea.AREA_C;
                case AREAP -> TipoArea.AREA_P;
                case AREAPC -> TipoArea.AREA_PC;
                default -> throw error("se esperaba un tipo de área (AreaC, AreaP o AreaPC)");
            };
            siguienteToken();

            tomar(TipoToken.LPAREN);
            int av1 = constante(parseExpresion(), "área " + nombre);
            tomar(TipoToken.COMA);
            int ca1 = constante(parseExpresion(), "área " + nombre);
            tomar(TipoToken.COMA);
            int av2 = constante(parseExpresion(), "área " + nombre);
            tomar(TipoToken.COMA);
            int ca2 = constante(parseExpresion(), "área " + nombre);
            tomar(TipoToken.RPAREN);

            if (av2 < av1 || ca2 < ca1
                    || fueraDeRango(av1) || fueraDeRango(ca1)
                    || fueraDeRango(av2) || fueraDeRango(ca2)) {
                throw new ErrorCompilacion("valores no válidos para el área '" + nombre + "'", fila, 1);
            }

            Area area = new Area(nombre, tipo, av1, ca1, av2, ca2);
            for (Area previa : areas) {
                if (previa.getNombre().equals(nombre)) {
                    throw new ErrorCompilacion(
                            "no se pueden declarar dos áreas con el mismo nombre: " + nombre, fila, 1);
                }
                if (previa.seSolapaCon(area)) {
                    throw new ErrorCompilacion("no se puede declarar el área '" + nombre
                            + "': se superpone con el área '" + previa.getNombre() + "'", fila, 1);
                }
            }
            areas.add(area);
        }
        tomar(TipoToken.DEDENT);
    }

    private static boolean fueraDeRango(int valor) {
        return valor < 1 || valor > Ciudad.MAXIMO;
    }

    private Area buscarArea(String nombre) {
        for (Area a : areas) {
            if (a.getNombre().equals(nombre)) {
                return a;
            }
        }
        return null;
    }

    /** Las dimensiones de un área tienen que ser constantes conocidas al compilar. */
    private int constante(Expr e, String contexto) throws ErrorCompilacion {
        Integer valor = plegar(e);
        if (valor == null) {
            throw error("el " + contexto + " necesita valores numéricos constantes");
        }
        return valor;
    }

    private static Integer plegar(Expr e) {
        return switch (e) {
            case Expr.Numero n -> n.valor();
            case Expr.Unaria u when u.op() == Operador.RESTA -> {
                Integer v = plegar(u.operando());
                yield v == null ? null : -v;
            }
            case Expr.Binaria b when b.op().esAritmetico() -> {
                Integer i = plegar(b.izq());
                Integer d = plegar(b.der());
                if (i == null || d == null) {
                    yield null;
                }
                yield switch (b.op()) {
                    case SUMA -> i + d;
                    case RESTA -> i - d;
                    case MULTIPLICACION -> i * d;
                    case DIVISION -> d == 0 ? null : i / d;
                    default -> null;
                };
            }
            case null, default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Bloque robots
    // ------------------------------------------------------------------

    private void parseRobots() throws ErrorCompilacion {
        siguienteToken(); // 'robots'
        tomar(TipoToken.INDENT);
        do {
            TipoRobot r = parseUnRobot();
            for (TipoRobot previo : tiposRobot) {
                if (previo.nombre().equals(r.nombre())) {
                    throw error("no se puede declarar dos robots con el mismo nombre: " + r.nombre());
                }
            }
            tiposRobot.add(r);
        } while (!tokenActual.es(TipoToken.INDENT) && !tokenActual.es(TipoToken.DEDENT));
        tomar(TipoToken.DEDENT);
    }

    private TipoRobot parseUnRobot() throws ErrorCompilacion {
        tomar(TipoToken.ROBOT);
        String nombre = tomarIdentificador();
        List<DeclVar> variables = tokenActual.es(TipoToken.VARIABLES)
                ? parseVariables(false, List.of())
                : List.of();
        List<Sent> cuerpo = parseCuerpoConComenzar();
        return new TipoRobot(nombre, variables, cuerpo);
    }

    // ------------------------------------------------------------------
    // Bloque variables
    // ------------------------------------------------------------------

    /**
     * @param permitirRobots si se aceptan variables cuyo tipo es un tipo de
     *                       robot; sólo el programa principal puede hacerlo
     * @param parametros     parámetros formales con los que no puede chocar el
     *                       nombre de una variable local
     */
    private List<DeclVar> parseVariables(boolean permitirRobots, List<ParamFormal> parametros)
            throws ErrorCompilacion {
        siguienteToken(); // 'variables'
        tomar(TipoToken.INDENT);

        List<DeclVar> variables = new ArrayList<>();
        do {
            List<String> nombres = new ArrayList<>();
            while (tokenActual.es(TipoToken.IDENTIFER)) {
                nombres.add(tokenActual.escritura);
                siguienteToken();
                if (tokenActual.es(TipoToken.COMA)) {
                    siguienteToken();
                }
            }
            if (nombres.isEmpty()) {
                throw error("se esperaba el nombre de una variable");
            }
            tomar(TipoToken.DOSPUNTOS);

            Tipo tipo;
            String tipoRobot = null;
            switch (tokenActual.tipo) {
                case NUMERO -> tipo = Tipo.NUMERO;
                case BOOLEAN -> tipo = Tipo.BOOLEAN;
                case IDENTIFER -> {
                    if (!permitirRobots || buscarTipoRobot(tokenActual.escritura) == null) {
                        throw error("error de tipo en la declaración de variables: '"
                                + tokenActual.escritura + "' no es un tipo válido");
                    }
                    tipo = Tipo.ROBOT;
                    tipoRobot = tokenActual.escritura;
                }
                default -> throw error("error de tipo en la declaración de variables");
            }
            siguienteToken();

            for (String nombre : nombres) {
                for (DeclVar previa : variables) {
                    if (previa.nombre().equals(nombre)) {
                        throw error("la variable '" + nombre + "' ya existe");
                    }
                }
                for (ParamFormal p : parametros) {
                    if (p.nombre().equals(nombre)) {
                        throw error("no se puede declarar una variable con el mismo nombre "
                                + "que un parámetro: " + nombre);
                    }
                }
                variables.add(new DeclVar(nombre, tipo, tipoRobot));
            }
        } while (!tokenActual.es(TipoToken.INDENT) && !tokenActual.es(TipoToken.DEDENT));

        tomar(TipoToken.DEDENT);
        return List.copyOf(variables);
    }

    private TipoRobot buscarTipoRobot(String nombre) {
        for (TipoRobot t : tiposRobot) {
            if (t.nombre().equals(nombre)) {
                return t;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Sentencias
    // ------------------------------------------------------------------

    /** {@code comenzar} INDENT sentencias DEDENT {@code fin}. */
    private List<Sent> parseCuerpoConComenzar() throws ErrorCompilacion {
        tomar(TipoToken.COMENZAR);
        tomar(TipoToken.INDENT);
        List<Sent> sentencias = parseSecuenciaDeSentencias();
        tomar(TipoToken.DEDENT);
        tomar(TipoToken.FIN);
        return sentencias;
    }

    private List<Sent> parseSecuenciaDeSentencias() throws ErrorCompilacion {
        List<Sent> sentencias = new ArrayList<>();
        while (!tokenActual.es(TipoToken.DEDENT)
                && !tokenActual.es(TipoToken.INDENT)
                && !tokenActual.es(TipoToken.FIN)
                && !tokenActual.es(TipoToken.EOT)) {
            sentencias.add(parseSentencia());
        }
        return sentencias;
    }

    private Sent parseSentencia() throws ErrorCompilacion {
        return switch (tokenActual.tipo) {
            case MOVER, DERECHA, TOMARFLOR, TOMARPAPEL, DEPOSITARFLOR, DEPOSITARPAPEL,
                 ENVIARMENSAJE, RECIBIRMENSAJE, LEER, BLOQUEARESQUINA, LIBERARESQUINA, RANDOM ->
                    parsePrimitiva();
            case IDENTIFER, INFORMAR, POS -> parseSentenciaSimple();
            case SI, REPETIR, MIENTRAS -> parseSentenciaCompuesta();
            default -> throw error("error de sentencia: no se esperaba " + descripcion(tokenActual));
        };
    }

    private Sent parseSentenciaSimple() throws ErrorCompilacion {
        int fila = tokenActual.fila;
        if (tokenActual.es(TipoToken.INFORMAR)) {
            return parseInformar();
        }
        if (tokenActual.es(TipoToken.POS)) {
            siguienteToken();
            tomar(TipoToken.LPAREN);
            Expr av = parseExpresion();
            tomar(TipoToken.COMA);
            Expr ca = parseExpresion();
            tomar(TipoToken.RPAREN);
            return new Sent.Pos(av, ca, fila);
        }

        String nombre = tomarIdentificador();
        if (tokenActual.es(TipoToken.ASIGNACION)) {
            siguienteToken();
            return new Sent.Asignacion(nombre, parseExpresion(), fila);
        }
        return parseInvocacionProceso(nombre, fila);
    }

    private Sent parseInvocacionProceso(String nombre, int fila) throws ErrorCompilacion {
        List<Expr> argumentos = new ArrayList<>();
        if (tokenActual.es(TipoToken.LPAREN)) {
            siguienteToken();
            while (!tokenActual.es(TipoToken.RPAREN)) {
                argumentos.add(parseExpresion());
                if (tokenActual.es(TipoToken.COMA)) {
                    siguienteToken();
                }
            }
            tomar(TipoToken.RPAREN);
        }
        return new Sent.LlamadaProceso(nombre, List.copyOf(argumentos), fila);
    }

    /**
     * {@code Informar('texto', expr, ...)}. El texto literal va entre comillas
     * simples y, igual que en el original, es una única palabra.
     */
    private Sent parseInformar() throws ErrorCompilacion {
        int fila = tokenActual.fila;
        siguienteToken();
        tomar(TipoToken.LPAREN);

        List<Object> partes = new ArrayList<>();
        while (!tokenActual.es(TipoToken.RPAREN)) {
            if (tokenActual.es(TipoToken.COMILLASSIMPLE)) {
                siguienteToken();
                StringBuilder texto = new StringBuilder();
                while (!tokenActual.es(TipoToken.COMILLASSIMPLE) && !tokenActual.es(TipoToken.RPAREN)) {
                    if (!texto.isEmpty()) {
                        texto.append(' ');
                    }
                    texto.append(tokenActual.escritura);
                    siguienteToken();
                }
                tomar(TipoToken.COMILLASSIMPLE);
                partes.add(texto.toString());
            } else {
                partes.add(parseExpresion());
            }
            if (tokenActual.es(TipoToken.COMA)) {
                siguienteToken();
            }
        }
        tomar(TipoToken.RPAREN);
        return new Sent.Informar(List.copyOf(partes), fila);
    }

    private Sent parsePrimitiva() throws ErrorCompilacion {
        int fila = tokenActual.fila;
        switch (tokenActual.tipo) {
            case MOVER -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.MOVER, fila);
            }
            case DERECHA -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.DERECHA, fila);
            }
            case TOMARFLOR -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.TOMAR_FLOR, fila);
            }
            case TOMARPAPEL -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.TOMAR_PAPEL, fila);
            }
            case DEPOSITARFLOR -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.DEPOSITAR_FLOR, fila);
            }
            case DEPOSITARPAPEL -> {
                siguienteToken();
                return new Sent.Accion(Primitiva.DEPOSITAR_PAPEL, fila);
            }
            case BLOQUEARESQUINA, LIBERARESQUINA -> {
                boolean bloquear = tokenActual.es(TipoToken.BLOQUEARESQUINA);
                siguienteToken();
                tomar(TipoToken.LPAREN);
                Expr av = parseExpresion();
                tomar(TipoToken.COMA);
                Expr ca = parseExpresion();
                tomar(TipoToken.RPAREN);
                return bloquear
                        ? new Sent.BloquearEsquina(av, ca, fila)
                        : new Sent.LiberarEsquina(av, ca, fila);
            }
            case LEER -> {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                String variable = tomarIdentificador();
                tomar(TipoToken.RPAREN);
                return new Sent.Leer(variable, fila);
            }
            case RANDOM -> {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                String variable = tomarIdentificador();
                tomar(TipoToken.COMA);
                Expr desde = parseExpresion();
                tomar(TipoToken.COMA);
                Expr hasta = parseExpresion();
                tomar(TipoToken.RPAREN);
                return new Sent.Random(variable, desde, hasta, fila);
            }
            case ENVIARMENSAJE -> {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                Expr valor = parseExpresion();
                tomar(TipoToken.COMA);
                if (tokenActual.es(TipoToken.MULT)) {
                    throw error("el comodín " + Buzon.COMODIN + " sólo sirve para recibir: "
                            + "EnviarMensaje necesita el nombre de un robot");
                }
                String destino = tomarIdentificador();
                tomar(TipoToken.RPAREN);
                return new Sent.EnviarMensaje(valor, destino, fila);
            }
            case RECIBIRMENSAJE -> {
                siguienteToken();
                tomar(TipoToken.LPAREN);
                String variable = tomarIdentificador();
                tomar(TipoToken.COMA);
                String origen = tomarRemitente();
                tomar(TipoToken.RPAREN);
                return new Sent.RecibirMensaje(variable, origen, fila);
            }
            default -> throw error("se esperaba una primitiva");
        }
    }

    private Sent parseSentenciaCompuesta() throws ErrorCompilacion {
        int fila = tokenActual.fila;
        switch (tokenActual.tipo) {
            case SI -> {
                siguienteToken();
                Expr condicion = parseExpresion();
                tomar(TipoToken.INDENT);
                List<Sent> entonces = parseSecuenciaDeSentencias();
                tomar(TipoToken.DEDENT);

                List<Sent> sino = List.of();
                if (tokenActual.es(TipoToken.SINO)) {
                    siguienteToken();
                    tomar(TipoToken.INDENT);
                    sino = parseSecuenciaDeSentencias();
                    tomar(TipoToken.DEDENT);
                }
                return new Sent.Si(condicion, entonces, sino, fila);
            }
            case REPETIR -> {
                siguienteToken();
                Expr veces = parseExpresion();
                tomar(TipoToken.INDENT);
                List<Sent> cuerpo = parseSecuenciaDeSentencias();
                tomar(TipoToken.DEDENT);
                return new Sent.Repetir(veces, cuerpo, fila);
            }
            case MIENTRAS -> {
                siguienteToken();
                Expr condicion = parseExpresion();
                tomar(TipoToken.INDENT);
                List<Sent> cuerpo = parseSecuenciaDeSentencias();
                tomar(TipoToken.DEDENT);
                return new Sent.Mientras(condicion, cuerpo, fila);
            }
            default -> throw error("se esperaba si, mientras o repetir");
        }
    }

    // ------------------------------------------------------------------
    // Expresiones
    // ------------------------------------------------------------------

    private static boolean esOperando(Token t) {
        return t.es(TipoToken.INTLITERAL) || t.es(TipoToken.VERDADERO) || t.es(TipoToken.FALSO);
    }

    private static boolean esConsulta(Token t) {
        return sensorDe(t) != null;
    }

    private static Sensor sensorDe(Token t) {
        return switch (t.tipo) {
            case POSAV -> Sensor.POS_AV;
            case POSCA -> Sensor.POS_CA;
            case HAYFLORENLAESQUINA -> Sensor.HAY_FLOR_EN_LA_ESQUINA;
            case HAYFLORENLABOLSA -> Sensor.HAY_FLOR_EN_LA_BOLSA;
            case HAYPAPELENLAESQUINA -> Sensor.HAY_PAPEL_EN_LA_ESQUINA;
            case HAYPAPELENLABOLSA -> Sensor.HAY_PAPEL_EN_LA_BOLSA;
            case HAYOBSTACULO -> Sensor.HAY_OBSTACULO;
            default -> null;
        };
    }

    private static Operador operadorDe(Token t) {
        return switch (t.tipo) {
            case MAS -> Operador.SUMA;
            case MENOS -> Operador.RESTA;
            case MULT -> Operador.MULTIPLICACION;
            case DIV -> Operador.DIVISION;
            case AND -> Operador.AND;
            case OR -> Operador.OR;
            case NOT -> Operador.NOT;
            case IGUAL -> Operador.IGUAL;
            case DISTINTO -> Operador.DISTINTO;
            case MENOR -> Operador.MENOR;
            case MAYOR -> Operador.MAYOR;
            case MENORIGUAL -> Operador.MENOR_IGUAL;
            case MAYORIGUAL -> Operador.MAYOR_IGUAL;
            default -> null;
        };
    }

    private static boolean esOperador(Token t) {
        return operadorDe(t) != null;
    }

    private static boolean formaParteDeExpresion(Token t) {
        return t.es(TipoToken.IDENTIFER) || esOperando(t) || esConsulta(t) || esOperador(t)
                || t.es(TipoToken.LPAREN) || t.es(TipoToken.RPAREN);
    }

    /**
     * Reproduce {@code thereIsHighOrEqualPrecedence}: se desapila mientras el
     * operador del tope no ligue menos que el entrante. Sólo hay dos
     * desempates, el resto de los operadores comparten nivel.
     */
    private static boolean desapilar(Token tope, Token entrante) {
        if (tope.es(TipoToken.LPAREN)) {
            return false;
        }
        if (entrante.es(TipoToken.NOT) && (tope.es(TipoToken.AND) || tope.es(TipoToken.OR))) {
            return false;
        }
        boolean entranteMultiplicativo = entrante.es(TipoToken.MULT) || entrante.es(TipoToken.DIV);
        boolean topeAditivo = tope.es(TipoToken.MAS) || tope.es(TipoToken.MENOS);
        return !(entranteMultiplicativo && topeAditivo);
    }

    /**
     * Convierte la expresión a notación polaca inversa y la vuelve a armar como
     * árbol. Se detiene en el primer token que no pertenece a la expresión o
     * que está en otra línea.
     */
    public Expr parseExpresion() throws ErrorCompilacion {
        Deque<Token> operadores = new ArrayDeque<>();
        Deque<Token> salida = new ArrayDeque<>();
        int linea = tokenActual.fila;

        while (formaParteDeExpresion(tokenActual) && tokenActual.fila == linea) {
            Token t = tokenActual;

            if (t.es(TipoToken.RPAREN)) {
                if (operadores.isEmpty()) {
                    break; // el paréntesis cierra la llamada, no la expresión
                }
                Token tope;
                boolean encontroApertura = false;
                do {
                    tope = operadores.pop();
                    if (tope.es(TipoToken.LPAREN)) {
                        encontroApertura = true;
                    } else {
                        salida.push(tope);
                    }
                } while (!encontroApertura && !operadores.isEmpty());
                if (!encontroApertura) {
                    break;
                }
            } else if (t.es(TipoToken.LPAREN)) {
                operadores.push(t);
            } else if (esOperador(t)) {
                while (!operadores.isEmpty() && desapilar(operadores.peek(), t)) {
                    salida.push(operadores.pop());
                }
                operadores.push(t);
            } else {
                salida.push(t);
            }
            siguienteToken();
        }

        while (!operadores.isEmpty()) {
            Token t = operadores.pop();
            if (!t.es(TipoToken.LPAREN)) {
                salida.push(t);
            }
        }
        if (salida.isEmpty()) {
            throw error("se esperaba una expresión");
        }
        return construir(salida);
    }

    /** Desarma la pila en notación polaca inversa y arma el árbol. */
    private Expr construir(Deque<Token> salida) throws ErrorCompilacion {
        if (salida.isEmpty()) {
            throw error("expresión incompleta");
        }
        Token t = salida.pop();
        Operador op = operadorDe(t);
        if (op != null) {
            Expr derecha = salida.isEmpty() ? null : construir(salida);
            Expr izquierda = (salida.isEmpty() || op == Operador.NOT) ? null : construir(salida);
            if (derecha == null) {
                throw new ErrorCompilacion("falta un operando para '" + t.escritura + "'",
                        t.fila, t.columna);
            }
            return izquierda == null
                    ? new Expr.Unaria(op, derecha, t.fila)
                    : new Expr.Binaria(op, izquierda, derecha, t.fila);
        }
        Sensor sensor = sensorDe(t);
        if (sensor != null) {
            return new Expr.Consulta(sensor, t.fila);
        }
        return switch (t.tipo) {
            case INTLITERAL -> new Expr.Numero(Integer.parseInt(t.escritura), t.fila);
            case VERDADERO -> new Expr.Booleano(true, t.fila);
            case FALSO -> new Expr.Booleano(false, t.fila);
            case IDENTIFER -> new Expr.Var(t.escritura, t.fila);
            default -> throw new ErrorCompilacion(
                    "no se esperaba '" + t.escritura + "' dentro de una expresión", t.fila, t.columna);
        };
    }
}
