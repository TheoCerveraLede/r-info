package rinfo.ast;

import java.util.List;
import rinfo.runtime.Area;

/**
 * Árbol sintáctico completo de un programa r-Info.
 *
 * <p>La estructura del fuente es, en este orden y con todos los bloques
 * intermedios opcionales:
 *
 * <pre>
 * programa &lt;nombre&gt;
 * procesos
 *   ...
 * areas
 *   ...
 * robots
 *   ...
 * variables
 *   ...
 * comenzar
 *   ...
 * fin
 * </pre>
 */
public record Programa(String nombre,
                       List<Proceso> procesos,
                       List<Area> areas,
                       List<TipoRobot> tiposRobot,
                       List<DeclVar> variables,
                       List<Sent> cuerpo) {

    public Proceso buscarProceso(String nombre) {
        for (Proceso p : procesos) {
            if (p.nombre().equals(nombre)) {
                return p;
            }
        }
        return null;
    }

    public TipoRobot buscarTipoRobot(String nombre) {
        for (TipoRobot t : tiposRobot) {
            if (t.nombre().equals(nombre)) {
                return t;
            }
        }
        return null;
    }

    public Area buscarArea(String nombre) {
        for (Area a : areas) {
            if (a.getNombre().equals(nombre)) {
                return a;
            }
        }
        return null;
    }

    public DeclVar buscarVariable(String nombre) {
        for (DeclVar v : variables) {
            if (v.nombre().equals(nombre)) {
                return v;
            }
        }
        return null;
    }
}
