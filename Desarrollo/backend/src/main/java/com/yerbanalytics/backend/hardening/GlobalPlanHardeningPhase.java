package com.yerbanalytics.backend.hardening;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * La rustificación según el plan global del vivero: la lista de etapas por día de ciclo y la fecha de siembra
 * ({@code yerbanalytics.nursery.fecha-siembra-iso}), la misma que lee {@code ShadingRule}. Hay rustificación desde el día en
 * que empieza la primera etapa y sigue contando cuando el plan terminó («en curso o finalizado», reglas_v2 §7).
 *
 * <p>Sin fecha de siembra, con una fecha ilegible o sin etapas no se puede saber, y no cuenta: la regla se comporta como
 * fuera de la rustificación. La fecha de siembra es un dato del lote, no un umbral (design D7 del catálogo): por eso conserva
 * su {@code @Value}. El plan es global y no por zona, así que la zona no cambia la respuesta.
 */
@Component
public class GlobalPlanHardeningPhase implements HardeningPhase {

    private static final Logger log = LoggerFactory.getLogger(GlobalPlanHardeningPhase.class);

    private final RustificacionEtapaRepository stages;
    private final String sowingDateIso;

    public GlobalPlanHardeningPhase(RustificacionEtapaRepository stages,
                                    @Value("${yerbanalytics.nursery.sowing-date-iso:}") String sowingDateIso) {
        this.stages = stages;
        this.sowingDateIso = sowingDateIso;
    }

    @Override
    public boolean isUnderway(ZonaEntity zone, Instant now) {
        if (sowingDateIso == null || sowingDateIso.isBlank()) {
            return false;
        }
        LocalDate sowingDate;
        try {
            sowingDate = LocalDate.parse(sowingDateIso);
        } catch (DateTimeParseException e) {
            log.warn("La fecha de siembra «{}» no es válida (yyyy-MM-dd): no se sabe si hay rustificación.", sowingDateIso);
            return false;
        }
        long cycleDay = ChronoUnit.DAYS.between(sowingDate, now.atZone(ZonaHorariaVivero.ZONA).toLocalDate()) + 1;
        List<RustificacionEtapaEntity> plan = stages.findAllByOrderByOrdenAsc();
        return plan.stream().mapToInt(RustificacionEtapaEntity::getDiaDesde).min().stream()
                .anyMatch(firstDay -> cycleDay >= firstDay);
    }
}
