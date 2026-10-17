package com.yerbanalytics.backend.mqtt;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipos de error que el nodo publica en {@link ContratoNodo#TOPIC_ERRORS} (S-05, {@code reglas_v2} §4). El valor del
 * cable es del contrato y se conserva tal cual; el nombre Java es nuestro.
 *
 * <p>Espeja la sección "Errores del nodo" de {@code contrato.h} (fuente de verdad) y
 * {@code NODE_ERROR_TYPES} de {@code simulador/server/contract.ts}.
 */
public enum NodeErrorType {

    /** La válvula está abierta y no pasa agua. */
    NO_FLOW("SIN_CAUDAL", true, "sin caudal"),
    /** Pasa más agua de la que la válvula debería dejar pasar (fuga o válvula trabada). */
    EXCESS_FLOW("CAUDAL_EXCESIVO", true, "caudal excesivo"),
    /** La malla no tocó el final de carrera que esperaba: el motor se rindió. */
    SHADE_LIMIT_SWITCH("MEDIASOMBRA_FIN_DE_CARRERA", false, "fin de carrera de la mediasombra sin activar");

    private final String wireValue;
    private final boolean hydraulic;
    private final String description;

    NodeErrorType(String wireValue, boolean hydraulic, String description) {
        this.wireValue = wireValue;
        this.hydraulic = hydraulic;
        this.description = description;
    }

    /** El {@code tipo} tal como viaja en el payload. */
    public String wireValue() {
        return wireValue;
    }

    /** {@code true} si es una falla del circuito de agua de un sector; {@code false} si es de la malla de la macro-zona. */
    public boolean isHydraulic() {
        return hydraulic;
    }

    /** Cómo se dice en un texto para el operador ("sin caudal"). */
    public String description() {
        return description;
    }

    /**
     * Cómo se dice el {@code tipo} que quedó guardado en la falla de un dispositivo: su descripción, o el valor tal cual
     * si no es de este contrato (una falla cargada por otro camino).
     */
    public static String describe(String wireValue) {
        return fromWire(wireValue).map(NodeErrorType::description).orElse(wireValue);
    }

    /** El tipo del contrato con ese valor exacto, o vacío si no existe (un tipo que el contrato no define se descarta). */
    public static Optional<NodeErrorType> fromWire(String wireValue) {
        return Arrays.stream(values()).filter(t -> t.wireValue.equals(wireValue)).findFirst();
    }
}
