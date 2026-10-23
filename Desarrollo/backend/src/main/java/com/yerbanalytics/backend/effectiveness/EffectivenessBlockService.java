package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.chlorosis.ChlorosisNutritionalRule;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * El lado de consulta de S-06 (reglas_v2 §4): qué acciones están bloqueadas porque su última evaluación fue «sin efectividad».
 * El bloqueo es la marca {@code bloqueoRepeticion} que {@link EffectivenessJob} deja en el evento del historial y que sólo baja
 * {@link EffectivenessReviewService} al registrar la revisión. Esto no decide nada: las reglas de riego y las precondiciones de
 * aplicación lo leen.
 *
 * <p>Si el simulador apaga la evaluación de efectividad ({@link EffectivenessService}), o la regla S-06, no se bloquea nada. El
 * bypass {@code block} de la regla lo salta sólo para quien lo consulta (la regla de riego y las precondiciones), no acá.
 */
@Service
public class EffectivenessBlockService {

    /** Nombre de la regla S-06 en el motor y clave de sus overrides. */
    public static final String RULE_NAME = "IneffectiveActionRule";
    /** Bypass de demo: la acción se repite aunque la anterior haya quedado sin efectividad. */
    public static final String BYPASS_BLOCK = "block";

    private final HistorialRepository history;
    private final RuleOverrides overrides;

    public EffectivenessBlockService(HistorialRepository history, RuleOverrides overrides) {
        this.history = history;
        this.overrides = overrides;
    }

    /**
     * Los bloqueos de cada sector de una macro-zona, con UNA consulta: las acciones sin efectividad que siguen en pie en la zona.
     * Cada sector de {@code sectorIds} figura en el resultado.
     */
    public Map<String, EffectivenessBlocks> forZone(String zoneId, Collection<String> sectorIds) {
        Map<String, EffectivenessBlocks> out = new HashMap<>();
        if (!overrides.isEnabled(EffectivenessService.NAME)) {
            sectorIds.forEach(id -> out.put(id, EffectivenessBlocks.NONE));
            return out;
        }
        List<HistorialEventoEntity> blocking = history.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull(zoneId);
        boolean irrigation = blocking.stream().anyMatch(e -> kindOf(e) == EffectivenessKind.IRRIGATION);
        for (String sectorId : sectorIds) {
            Set<EffectivenessKind> treatments = EnumSet.noneOf(EffectivenessKind.class);
            for (HistorialEventoEntity e : blocking) {
                EffectivenessKind kind = kindOf(e);
                if (kind != null && !kind.isZoneScoped() && sectorId.equals(e.getSectorId())) {
                    treatments.add(kind);
                }
            }
            out.put(sectorId, irrigation || !treatments.isEmpty() ? new EffectivenessBlocks(irrigation, treatments)
                    : EffectivenessBlocks.NONE);
        }
        return out;
    }

    /**
     * Por qué no se le puede aplicar {@code supply} al sector, si un tratamiento anterior quedó sin efectividad. El plan de
     * nutrición (N-01, N-02) no se bloquea nunca: la v2 lo excluye de la evaluación. Vacío si se puede aplicar.
     */
    public Optional<String> supplyBlock(SectorEntity sector, Supply supply, String originRule) {
        EffectivenessKind kind = kindOf(supply, originRule);
        if (kind == null || !overrides.isEnabled(EffectivenessService.NAME) || !overrides.isEnabled(RULE_NAME)
                || overrides.isBypassed(RULE_NAME, BYPASS_BLOCK)) {
            return Optional.empty();
        }
        boolean blocked = history.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull(sector.getId()).stream()
                .anyMatch(e -> kindOf(e) == kind);
        return blocked
                ? Optional.of("S-06: el último " + kind.label().toLowerCase() + " de " + sector.getId()
                        + " quedó sin efectividad; hace falta registrar la revisión física")
                : Optional.empty();
    }

    private static EffectivenessKind kindOf(HistorialEventoEntity event) {
        return EffectivenessKind.parse(event.getEfectividadTipo()).orElse(null);
    }

    private static EffectivenessKind kindOf(Supply supply, String originRule) {
        if (supply == Supply.FUNGICIDE) {
            return EffectivenessKind.FUNGICIDE;
        }
        return ChlorosisNutritionalRule.NAME.equals(originRule) ? EffectivenessKind.CORRECTIVE_FERTILIZER : null;
    }
}
