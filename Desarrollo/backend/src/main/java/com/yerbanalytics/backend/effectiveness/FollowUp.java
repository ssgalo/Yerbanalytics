package com.yerbanalytics.backend.effectiveness;

/**
 * El seguimiento de efectividad que se abre junto con la acción en el historial: qué se evalúa y, si es un tratamiento, cómo
 * estaba el sector cuando se aplicó.
 *
 * @param kind     qué tipo de acción es
 * @param baseline el estado previo del sector (E-02, E-03); {@code null} en el riego, que compara la humedad
 */
public record FollowUp(EffectivenessKind kind, TreatmentBaseline baseline) {
}
