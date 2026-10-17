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
    NO_FLOW("SIN_CAUDAL", true),
    /** Pasa más agua de la que la válvula debería dejar pasar (fuga o válvula trabada). */
    EXCESS_FLOW("CAUDAL_EXCESIVO", true),
    /** La malla no tocó el final de carrera que esperaba: el motor se rindió. */
    SHADE_LIMIT_SWITCH("MEDIASOMBRA_FIN_DE_CARRERA", false);

    private final String wireValue;
    private final boolean hydraulic;

    NodeErrorType(String wireValue, boolean hydraulic) {
        this.wireValue = wireValue;
        this.hydraulic = hydraulic;
    }

    /** El {@code tipo} tal como viaja en el payload. */
    public String wireValue() {
        return wireValue;
    }

    /** {@code true} si es una falla del circuito de agua de un sector; {@code false} si es de la malla de la macro-zona. */
    public boolean isHydraulic() {
        return hydraulic;
    }

    /** El tipo del contrato con ese valor exacto, o vacío si no existe (un tipo que el contrato no define se descarta). */
    public static Optional<NodeErrorType> fromWire(String wireValue) {
        return Arrays.stream(values()).filter(t -> t.wireValue.equals(wireValue)).findFirst();
    }
}
