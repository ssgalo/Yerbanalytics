package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;

/**
 * Si una macro-zona está en rustificación: el plan de endurecimiento del plantín antes del trasplante está en curso o ya
 * terminó (reglas_v2 §7). Las reglas que se comportan distinto «durante la rustificación» (F-C2: un amarillamiento leve es
 * esperable con el cambio de luz) preguntan acá y no saben de dónde sale el plan.
 *
 * <p>Lo responde el plan de la macro-zona ({@link ZonePlanHardeningPhase}, {@code add-rustificacion-por-macrozona}).
 */
public interface HardeningPhase {

    /** {@code true} si la zona está en rustificación (plan en curso o finalizado) en {@code now}. */
    boolean isUnderway(ZonaEntity zone, Instant now);
}
