package rinfo.runtime;

import java.awt.Color;

/** Los tres tipos de área que reconoce r-Info, con el color con que se dibujan. */
public enum TipoArea {
    /** Área compartida: varios robots pueden tenerla asignada. */
    AREA_C("AreaC", Color.GRAY),
    /** Área privada: exclusiva de un robot. */
    AREA_P("AreaP", Color.BLUE),
    /** Área parcialmente compartida. */
    AREA_PC("AreaPC", Color.ORANGE);

    public final String nombre;
    public final Color color;

    TipoArea(String nombre, Color color) {
        this.nombre = nombre;
        this.color = color;
    }

    public static TipoArea porNombre(String nombre) {
        for (TipoArea t : values()) {
            if (t.nombre.equals(nombre)) {
                return t;
            }
        }
        return null;
    }
}
