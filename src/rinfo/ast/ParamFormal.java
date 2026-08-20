package rinfo.ast;

/** Parámetro formal de un proceso: {@code E x: numero}. */
public record ParamFormal(Modo modo, String nombre, Tipo tipo) {}
