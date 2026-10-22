package com.yerbanalytics.backend.dto;

import com.yerbanalytics.backend.readings.VaporPressureBand;

/**
 * Déficit de presión de vapor de una macro-zona: métrica derivada de su lectura (no viaja por el contrato MQTT) e
 * informativa, así que no lleva estado ni color: la interfaz elige cómo mostrar el tramo.
 *
 * @param value el DPV en kPa
 * @param band  id del tramo ({@link VaporPressureBand}): SATURATED, TRANSITION, COMFORTABLE, HIGH_DEMAND, DRY_HEAT
 * @param label el tramo en palabras ("calor seco")
 */
public record VaporDeficit(double value, String band, String label) {

    /** El DPV con su tramo, o {@code null} si la zona no tiene valor: sin dato no es cero. */
    public static VaporDeficit of(Double dpv) {
        if (dpv == null) {
            return null;
        }
        VaporPressureBand band = VaporPressureBand.of(dpv);
        return new VaporDeficit(dpv, band.name(), band.label());
    }
}
