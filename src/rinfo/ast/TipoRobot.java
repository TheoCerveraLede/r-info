package rinfo.ast;

import java.util.List;

/** Un tipo de robot declarado en el bloque {@code robots}. */
public record TipoRobot(String nombre, List<DeclVar> variables, List<Sent> cuerpo) {}
