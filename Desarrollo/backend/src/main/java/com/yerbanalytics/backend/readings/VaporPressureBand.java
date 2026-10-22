package com.yerbanalytics.backend.readings;

/**
 * Tramo agronómico del DPV (reglas_v2 §1.4). El de 0,3–0,5 kPa no tiene etiqueta propia en la v2: se muestra como
 * transición y, como todos los tramos, es informativo (no cambia la salud de los sectores). Los umbrales que usan las
 * reglas (0,3 y 2,0) se declaran en el catálogo de cada regla, no acá.
 */
public enum VaporPressureBand {
    /** Menos de 0,3 kPa: follaje mojado por horas, riesgo fúngico. */
    SATURATED("aire saturado"),
    /** De 0,3 a 0,5 kPa. */
    TRANSITION("transición"),
    /** De 0,5 a 1,2 kPa. */
    COMFORTABLE("confortable"),
    /** De 1,2 a 2,0 kPa. */
    HIGH_DEMAND("demanda alta"),
    /** 2,0 kPa o más: favorece a los ácaros. */
    DRY_HEAT("calor seco");

    private final String label;

    VaporPressureBand(String label) {
        this.label = label;
    }

    /** Texto para la interfaz. */
    public String label() {
        return label;
    }

    /** El tramo de un DPV en kPa; el límite inferior de cada tramo le pertenece. */
    public static VaporPressureBand of(double dpv) {
        if (dpv < 0.3) {
            return SATURATED;
        }
        if (dpv < 0.5) {
            return TRANSITION;
        }
        if (dpv < 1.2) {
            return COMFORTABLE;
        }
        if (dpv < 2.0) {
            return HIGH_DEMAND;
        }
        return DRY_HEAT;
    }
}
