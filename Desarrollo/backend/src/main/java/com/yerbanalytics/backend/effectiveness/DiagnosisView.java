package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.Severity;

/**
 * El diagnóstico del sector al momento de evaluar. Sólo cuenta si es vigente (P-D1): uno viejo o no concluyente no dice
 * nada de cómo evolucionó el tratamiento.
 *
 * @param state    estado del diagnóstico; {@code null} si no hay uno
 * @param severity severidad, o {@code null} si no tiene (Sano) o no se reconoce
 * @param valid    si el diagnóstico es vigente
 * @param reason   por qué no lo es; {@code null} si lo es
 */
public record DiagnosisView(String state, Severity severity, boolean valid, String reason) {

    public static final String HEALTHY = "Sano";

    public boolean isHealthy() {
        return valid && HEALTHY.equals(state);
    }
}
