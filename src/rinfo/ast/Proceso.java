package rinfo.ast;

import java.util.List;

/**
 * Un proceso declarado en el bloque {@code procesos}.
 *
 * <p>El alcance de un proceso son únicamente sus parámetros formales y sus
 * variables locales: no ve las variables del robot que lo invoca.
 */
public record Proceso(String nombre,
                      List<ParamFormal> parametros,
                      List<DeclVar> locales,
                      List<Sent> cuerpo) {}
