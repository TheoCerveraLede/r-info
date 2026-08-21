package rinfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import rinfo.ast.Programa;
import rinfo.lang.ErrorCompilacion;
import rinfo.lang.Parser;
import rinfo.runtime.Ciudad;
import rinfo.runtime.Consola;
import rinfo.runtime.Contenido;
import rinfo.runtime.ErrorEjecucion;
import rinfo.runtime.Robot;
import rinfo.runtime.Simulacion;

/**
 * Ejecuta un programa r-Info sin interfaz gráfica.
 *
 * <p>Sin argumentos abre la ventana, que es el modo habitual. Con un archivo
 * corre en modo texto y acepta las mismas operaciones de armado de la ciudad
 * que el diálogo «Colocar» de la interfaz.
 */
public final class Rinfo {

    private static final String AYUDA = """
            Uso: java -cp out rinfo.Rinfo [<programa.rinfo>] [opciones]

            Sin argumentos abre la interfaz gráfica.

            Opciones:
              --ciudad AVxCA            tamaño de la ciudad (por omisión 100x100)
              --velocidad MS            demora entre acciones (por omisión 0)

              --flor AV,CA[,N]          deja N flores en esa esquina (N por omisión 1)
              --papel AV,CA[,N]         deja N papeles en esa esquina
              --obstaculo AV,CA         pone un obstáculo en esa esquina

              --azar-flor N[,ZONA]      reparte N flores al azar
              --azar-papel N[,ZONA]     reparte N papeles al azar
              --azar-obstaculo N[,ZONA] reparte N obstáculos al azar

            ZONA es AV1,CA1,AV2,CA2; si se omite, se usa toda la ciudad. El
            reparto al azar deja una unidad por esquina y no pisa esquinas
            ocupadas. Las opciones de colocación se pueden repetir.
            """;

    private Rinfo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            rinfo.ui.Main.main(args);
            return;
        }
        if (args[0].equals("--ayuda") || args[0].equals("-h") || args[0].equals("--help")) {
            System.out.print(AYUDA);
            return;
        }

        Path archivo = Path.of(args[0]);
        Ciudad ciudad = new Ciudad();
        int velocidad = 0;

        try {
            for (int i = 1; i < args.length; i++) {
                String opcion = args[i];
                switch (opcion) {
                    case "--ciudad" -> {
                        String[] partes = valor(args, ++i, opcion).toLowerCase().split("x");
                        ciudad.setNumAv(Integer.parseInt(partes[0].trim()));
                        ciudad.setNumCa(Integer.parseInt(partes[1].trim()));
                    }
                    case "--velocidad" -> velocidad = Integer.parseInt(valor(args, ++i, opcion));
                    case "--flor" -> colocar(ciudad, Contenido.FLOR, valor(args, ++i, opcion));
                    case "--papel" -> colocar(ciudad, Contenido.PAPEL, valor(args, ++i, opcion));
                    case "--obstaculo" -> colocar(ciudad, Contenido.OBSTACULO, valor(args, ++i, opcion));
                    case "--azar-flor" -> alAzar(ciudad, Contenido.FLOR, valor(args, ++i, opcion));
                    case "--azar-papel" -> alAzar(ciudad, Contenido.PAPEL, valor(args, ++i, opcion));
                    case "--azar-obstaculo" ->
                            alAzar(ciudad, Contenido.OBSTACULO, valor(args, ++i, opcion));
                    default -> throw new IllegalArgumentException("opción desconocida: " + opcion);
                }
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            System.err.println("Error en los argumentos: " + e.getMessage());
            System.err.println();
            System.err.print(AYUDA);
            System.exit(2);
            return;
        }

        String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
        Programa programa;
        try {
            programa = Parser.compilar(fuente.replace("\r\n", "\n"));
        } catch (ErrorCompilacion e) {
            System.err.println("Error de compilación en " + archivo + " — " + e.getMessage());
            System.exit(1);
            return;
        }

        Simulacion simulacion = new Simulacion(programa, ciudad, new ConsolaTexto(), null);
        simulacion.getControl().setDemoraMs(velocidad);
        simulacion.iniciar();
        while (simulacion.estaCorriendo()) {
            Thread.sleep(20);
        }

        for (Robot r : ciudad.getRobots()) {
            System.out.printf("%s: av %d, ca %d, mirando al %s, bolsa %d flor(es) y %d papel(es)%n",
                    r.getNombre(), r.getAv(), r.getCa(), r.getDireccion().name().toLowerCase(),
                    r.getFloresEnBolsa(), r.getPapelesEnBolsa());
        }
    }

    private static String valor(String[] args, int indice, String opcion) {
        if (indice >= args.length) {
            throw new IllegalArgumentException(opcion + " necesita un valor");
        }
        return args[indice];
    }

    /** {@code AV,CA[,N]} */
    private static void colocar(Ciudad ciudad, Contenido que, String argumento) {
        int[] partes = numeros(argumento, que + " se escribe AV,CA[,N]", 2, 3);
        int cantidad = partes.length == 3 ? partes[2] : 1;
        ciudad.colocar(que, partes[0], partes[1], cantidad);
    }

    /** {@code N[,AV1,CA1,AV2,CA2]} */
    private static void alAzar(Ciudad ciudad, Contenido que, String argumento) {
        int[] partes = numeros(argumento, "el reparto al azar se escribe N[,AV1,CA1,AV2,CA2]", 1, 5);
        int cantidad = partes[0];
        int av1 = partes.length == 5 ? partes[1] : 1;
        int ca1 = partes.length == 5 ? partes[2] : 1;
        int av2 = partes.length == 5 ? partes[3] : ciudad.getNumAv();
        int ca2 = partes.length == 5 ? partes[4] : ciudad.getNumCa();

        int colocadas = ciudad.colocarAlAzar(que, cantidad, av1, ca1, av2, ca2, true);
        if (colocadas < cantidad) {
            System.out.println("* Sólo entraron " + colocadas + " de " + cantidad + " "
                    + que.plural + ": no quedaban esquinas libres.");
        }
    }

    /** Parte {@code argumento} por comas y exige uno de los largos permitidos. */
    private static int[] numeros(String argumento, String formato, int... largosPermitidos) {
        String[] partes = argumento.split(",");
        boolean largoValido = false;
        for (int largo : largosPermitidos) {
            largoValido |= partes.length == largo;
        }
        if (!largoValido) {
            throw new IllegalArgumentException(formato);
        }
        int[] valores = new int[partes.length];
        for (int i = 0; i < partes.length; i++) {
            try {
                valores[i] = Integer.parseInt(partes[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(formato);
            }
        }
        return valores;
    }

    /** Consola de texto: Informar va a stdout y Leer a stdin. */
    private static final class ConsolaTexto implements Consola {
        private final BufferedReader entrada =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        @Override
        public void informar(String robot, String texto) {
            System.out.println("[" + robot + "] " + texto);
        }

        @Override
        public void traza(String texto) {
            System.out.println("* " + texto);
        }

        @Override
        public int leer(String robot, String variable) throws ErrorEjecucion {
            System.out.print("[" + robot + "] " + variable + " = ");
            System.out.flush();
            try {
                String linea = entrada.readLine();
                if (linea == null) {
                    throw new ErrorEjecucion("no hay más entrada para Leer(" + variable + ")");
                }
                return Integer.parseInt(linea.trim());
            } catch (IOException | NumberFormatException e) {
                throw new ErrorEjecucion("valor inválido para Leer(" + variable + ")");
            }
        }
    }
}
