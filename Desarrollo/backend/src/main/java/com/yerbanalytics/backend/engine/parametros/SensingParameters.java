package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Plazos de la lectura del nodo testigo (reglas_v2 §11, Telemetría): cuánto se espera una respuesta, cuántas veces se
 * reintenta y cada cuánto se insiste con un nodo ya fuera de servicio. No son umbrales de una regla sino de la
 * infraestructura de sensado que las reglas suponen: las lee el pulso de sensado y el escalamiento del nodo.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum SensingParameters implements DefinicionParametro {

    RESPONSE_WAIT("sensado.espera-respuesta",
            "Espera de la respuesta del nodo",
            "Segundos que se espera la lectura después de pedirla, antes del primer reintento.",
            TipoParametro.ENTERO, "s", "60", 10.0, 300.0, 0, "reglas_v2 §11 Telemetría; S-02"),

    RETRIES("sensado.reintentos",
            "Reintentos de la lectura",
            "Veces que se vuelve a pedir la lectura a un nodo que no respondió, dentro del mismo pulso. Agotados, el pulso cuenta como sin respuesta.",
            TipoParametro.ENTERO, "reintentos", "2", 1.0, 5.0, 0, "reglas_v2 §11 Telemetría; S-02"),

    RETRY_WAIT("sensado.espera-reintento",
            "Espera entre reintentos",
            "Minutos que se espera entre un reintento de lectura y el siguiente.",
            TipoParametro.ENTERO, "min", "1", 1.0, 10.0, 0, "reglas_v2 §11 Telemetría; S-02"),

    OFFLINE_RETRY("sensado.reintento-fuera-servicio",
            "Reintento con el nodo fuera de servicio",
            "Cada cuántos minutos se vuelve a pedir la lectura a un nodo que ya quedó fuera de servicio.",
            TipoParametro.ENTERO, "min", "15", 5.0, 60.0, 0, "reglas_v2 §11 Telemetría; S-02");

    private final String clave;
    private final String etiqueta;
    private final String descripcion;
    private final TipoParametro tipo;
    private final String unidad;
    private final String fabrica;
    private final Double min;
    private final Double max;
    private final int decimales;
    private final String refSpec;

    @Override
    public FamiliaParametro familia() {
        return FamiliaParametro.SENSADO;
    }
}
