package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Criterio agronómico del diagnóstico ({@code GET/PUT /api/configuracion/diagnostico}).
 *
 * @param severityBands una por estado anómalo, siempre en el orden Clorosis, Estrés solar,
 *                      Plaga foliar, Daño fúngico
 * @param criticality   los cuatro estados del más crítico ({@code [0]}) al menos crítico
 */
public record DiagnosisCriteria(List<SeverityBand> severityBands, List<String> criticality) {
}
