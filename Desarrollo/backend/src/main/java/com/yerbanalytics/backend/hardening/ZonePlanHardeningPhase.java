package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.mediasombra.PlanRustificacionService;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.PlanRustificacionRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * La rustificación según el plan de la macro-zona ({@code add-rustificacion-por-macrozona}): hay rustificación si hoy rige
 * una etapa del plan vigente de la zona. Es la misma pregunta con la que {@code HorarioMediasombraService} mueve la malla
 * ({@link PlanRustificacionService#etapaDe}), así que la regla y la mediasombra no pueden discrepar sobre si la zona se
 * está rustificando.
 *
 * <p>Pasada la última etapa, ésta sigue rigiendo hasta que alguien elimina el plan, así que «en curso o finalizado»
 * (reglas_v2 §7) es «hay una etapa rigiendo». Sin plan, con uno programado a futuro o eliminado, no cuenta. Efecto a
 * conocer: un plan que nadie elimina tras el trasplante deja la zona en rustificación indefinidamente.
 */
@Component
public class ZonePlanHardeningPhase implements HardeningPhase {

    private final PlanRustificacionRepository plans;

    public ZonePlanHardeningPhase(PlanRustificacionRepository plans) {
        this.plans = plans;
    }

    @Override
    public boolean isUnderway(ZonaEntity zone, Instant now) {
        return plans.findFirstByZonaIdAndCanceladoEnIsNull(zone.getId())
                .flatMap(plan -> PlanRustificacionService.etapaDe(plan,
                        now.atZone(ZonaHorariaVivero.ZONA).toLocalDate()))
                .isPresent();
    }
}
