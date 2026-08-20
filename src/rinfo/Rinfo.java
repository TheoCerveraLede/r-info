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
import rinfo.runtime.ErrorEjecucion;
import rinfo.runtime.Robot;
import rinfo.runtime.Simulacion;

/**
 * Ejecuta un programa r-Info sin interfaz gráfica.
 *
 * <pre>
 * java -cp out rinfo.Rinfo programa.rinfo [--velocidad ms]
 * </pre>
 *
 * <p>Sin argumentos abre la interfaz gráfica, que es el modo habitual.
 */
public final class Rinfo {

    private Rinfo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            rinfo.ui.Main.main(args);
            return;
        }

        Path archivo = Path.of(args[0]);
        int velocidad = 0;
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equals("--velocidad")) {
                velocidad = Integer.parseInt(args[i + 1]);
            }
        }

        String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
        Programa programa;
        try {
            programa = Parser.compilar(fuente);
        } catch (ErrorCompilacion e) {
            System.err.println("Error de compilación en " + archivo + " — " + e.getMessage());
            System.exit(1);
            return;
        }

        Ciudad ciudad = new Ciudad();
        Simulacion simulacion = new Simulacion(programa, ciudad, new ConsolaTexto(), null);
        simulacion.getControl().setDemoraMs(velocidad);

        Object fin = new Object();
        simulacion.setAlTerminar(() -> {
            synchronized (fin) {
                fin.notifyAll();
            }
        });
        simulacion.iniciar();
        synchronized (fin) {
            while (simulacion.estaCorriendo()) {
                fin.wait(100);
            }
        }

        for (Robot r : ciudad.getRobots()) {
            System.out.printf("%s: av %d, ca %d, mirando al %s, bolsa %d flor(es) y %d papel(es)%n",
                    r.getNombre(), r.getAv(), r.getCa(), r.getDireccion().name().toLowerCase(),
                    r.getFloresEnBolsa(), r.getPapelesEnBolsa());
        }
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
