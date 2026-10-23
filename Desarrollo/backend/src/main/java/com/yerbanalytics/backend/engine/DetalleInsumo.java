package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.supply.Supply;

/**
 * Qué insumo decidió aplicar la regla. La cantidad no viaja acá: la decide el {@code ApplicationService} con la dosis
 * del catálogo, después de verificar las precondiciones. La regla decide; el servicio ejecuta.
 *
 * @param supply        el insumo a aplicar
 * @param fromDiagnosis {@code true} si la regla decide con el diagnóstico de IA: el servicio exige entonces que siga vigente (P-D1)
 */
public record DetalleInsumo(Supply supply, boolean fromDiagnosis) implements DetalleAccion {
}
