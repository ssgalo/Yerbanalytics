package com.yerbanalytics.backend.engine.parametros;

/** Definición mínima para tests: sólo lo que cada caso necesita fijar. */
record DefinicionDePrueba(
        String clave,
        TipoParametro tipo,
        String unidad,
        String fabrica,
        Double min,
        Double max,
        int decimales
) implements DefinicionParametro {

    static DefinicionDePrueba numero(String clave, String fabrica, Double min, Double max, int decimales) {
        return new DefinicionDePrueba(clave, TipoParametro.NUMERO, "%", fabrica, min, max, decimales);
    }

    static DefinicionDePrueba entero(String clave, String fabrica, Double min, Double max) {
        return new DefinicionDePrueba(clave, TipoParametro.ENTERO, "sectores", fabrica, min, max, 0);
    }

    static DefinicionDePrueba ventana(String clave, String fabrica) {
        return new DefinicionDePrueba(clave, TipoParametro.VENTANA_HORARIA, "", fabrica, null, null, 0);
    }

    static DefinicionDePrueba hora(String clave, String fabrica) {
        return new DefinicionDePrueba(clave, TipoParametro.HORA, "", fabrica, null, null, 0);
    }

    @Override public String etiqueta() { return "Etiqueta de " + clave; }
    @Override public String descripcion() { return "Descripción de " + clave; }
    @Override public FamiliaParametro familia() { return FamiliaParametro.RIEGO; }
    @Override public String refSpec() { return "test"; }
}
