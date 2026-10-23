package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;

/**
 * Si una macro-zona está en rustificación: el plan de endurecimiento del plantín antes del trasplante está en curso o ya
 * terminó (reglas_v2 §7). Las reglas que se comportan distinto «durante la rustificación» (F-C2: un amarillamiento leve es
 * esperable con el cambio de luz) preguntan acá y no saben de dónde sale el plan.
 *
 * <p>Hoy lo responde el plan global ({@link GlobalPlanHardeningPhase}); cuando llegue el plan por macro-zona
 * ({@code add-rustificacion-por-macrozona}) se reemplaza esa implementación y ninguna regla cambia.
 */
public interface HardeningPhase {

    /** {@code true} si la zona está en rustificación (plan en curso o finalizado) en {@code now}. */
    boolean isUnderway(ZonaEntity zone, Instant now);
}
