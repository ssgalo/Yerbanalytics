package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.HardwareFaults;
import com.yerbanalytics.backend.engine.MallaZona;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.traza.AccionTrazada;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Corre las {@link ZoneRule} <b>una vez por macro-zona</b>, cuando el motor termina de evaluar sus sectores en un
 * pulso con motor (reglas_v2: el motor decide sólo en el pulso). Es el camino compartido de las reglas que no son de un
 * sector: el plan de nutrición (N-01), la CE baja (N-02) y la mediasombra, que es de la macro-zona.
 *
 * <p>Las reglas corren por prioridad y cada una ejecuta sus acciones antes de que corra la siguiente: N-02 puede
 * adelantar el plan y N-01, que viene después, ya lo ve vencido en el mismo pulso. Una regla apagada con un override no
 * corre; queda en la traza. Como en la cadena por sector, un {@code ABORT_MEDIASOMBRA} corta las reglas que siguen de la
 * rama MEDIASOMBRA (quedan {@code OMITIDA_RAMA_BLOQUEADA}).
 *
 * <p>Si una regla lanza, la excepción se propaga (como en las reglas por sector: la transacción de la ingesta revierte
 * y el pulso siguiente lo reintenta) y antes se guarda la traza parcial con esa regla en {@code ERROR}.
 */
@Service
public class ZoneRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(ZoneRuleEngine.class);

    private final List<ZoneRule> rules;
    private final CatalogoParametrosService parameters;
    private final ZoneActionExecutor executor;
    private final ZoneTraceStore traces;
    private final ManualLockRepository locks;
    private final RuleOverrides overrides;

    @Autowired
    public ZoneRuleEngine(List<ZoneRule> rules, CatalogoParametrosService parameters, ZoneActionExecutor executor,
                          ZoneTraceStore traces, ManualLockRepository locks, RuleOverrides overrides) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(ZoneRule::priority)).toList();
        this.parameters = parameters;
        this.executor = executor;
        this.traces = traces;
        this.locks = locks;
        this.overrides = overrides;
        log.info("ZoneRuleEngine inicializado con {} regla(s): {}", this.rules.size(),
                this.rules.stream().map(ZoneRule::name).toList());
    }

    /**
     * Arma el contexto de la zona (incluidos los bloqueos manuales vigentes) y la evalúa. Es lo que llama la ingesta.
     *
     * @param hardware lo que ya armó el motor por sector (S-04)
     * @param faults   lo que ya armó el motor por sector (S-05)
     * @param forecast el pronóstico del pulso; {@code null} si no hay uno cacheado
     * @param malla    la mediasombra de la zona
     */
    public List<TrazaRegla> evaluate(ZonaEntity zone, List<SectorEntity> sectors, Instant now,
                                     Map<String, HardwareCoverage> hardware, Map<String, HardwareFaults> faults,
                                     WeatherForecast forecast, MallaZona malla) {
        boolean zoneLocked = false;
        Set<String> lockedSectors = new HashSet<>();
        for (ManualLockEntity lock : locks.findByActiveTrue()) {
            if (lock.getSectorId() != null) {
                lockedSectors.add(lock.getSectorId());
            } else if (zone.getId().equals(lock.getZonaId())) {
                zoneLocked = true;
            }
        }
        return evaluate(new ZoneRuleContext(zone, sectors, now, hardware, faults, zoneLocked, lockedSectors, overrides,
                forecast, malla));
    }

    /** Evalúa todas las reglas de zona, en orden de prioridad, y guarda la traza. */
    public List<TrazaRegla> evaluate(ZoneRuleContext original) {
        // Los overrides los pone el motor: quien arma el contexto no sabe que existen.
        ZoneRuleContext ctx = original.withOverrides(overrides);
        ParametrosVigentes current = parameters.vigentes();
        List<TrazaRegla> trace = new ArrayList<>(rules.size());
        String abortMediasombra = null;

        for (ZoneRule rule : rules) {
            if (!overrides.isEnabled(rule.name())) {
                trace.add(skipped(rule, EstadoRegla.OMITIDA_DESHABILITADA));
                continue;
            }
            if (rule.branch() == RuleBranch.MEDIASOMBRA && abortMediasombra != null) {
                trace.add(new TrazaRegla(rule.name(), rule.branch(), rule.priority(), EstadoRegla.OMITIDA_RAMA_BLOQUEADA,
                        List.of(), List.of(), abortMediasombra));
                continue;
            }
            Evaluacion ev = new Evaluacion(rule, current);
            List<RuleAction> actions;
            try {
                actions = rule.evaluate(ctx, ev);
                executor.execute(actions, ctx);
            } catch (RuntimeException e) {
                recordFailure(ctx, trace, rule, ev, e);
                throw e;
            }
            trace.add(new TrazaRegla(rule.name(), rule.branch(), rule.priority(), EstadoRegla.EVALUADA,
                    List.copyOf(ev.comparaciones()),
                    actions.stream().map(a -> new AccionTrazada(a.type(), a.motivo())).toList(), null));
            if (abortMediasombra == null && actions.stream().anyMatch(a -> a.type() == ActionType.ABORT_MEDIASOMBRA)) {
                abortMediasombra = rule.name();
            }
        }

        traces.save(ctx.zone().getId(), trace);
        return List.copyOf(trace);
    }

    /** Las reglas ordenadas por prioridad: el esquema del Inspector y el catálogo de parámetros las muestran. */
    public List<ZoneRule> getRules() {
        return rules;
    }

    private void recordFailure(ZoneRuleContext ctx, List<TrazaRegla> done, ZoneRule failed, Evaluacion ev,
                               RuntimeException e) {
        List<TrazaRegla> trace = new ArrayList<>(done);
        trace.add(new TrazaRegla(failed.name(), failed.branch(), failed.priority(), EstadoRegla.ERROR,
                List.copyOf(ev.comparaciones()), List.of(), null, e.getClass().getSimpleName() + ": " + e.getMessage()));
        boolean after = false;
        for (ZoneRule r : rules) {
            if (r == failed) {
                after = true;
            } else if (after) {
                trace.add(new TrazaRegla(r.name(), r.branch(), r.priority(), EstadoRegla.NO_ALCANZADA, List.of(),
                        List.of(), failed.name()));
            }
        }
        traces.save(ctx.zone().getId(), trace);
    }

    private static TrazaRegla skipped(ZoneRule rule, EstadoRegla state) {
        return new TrazaRegla(rule.name(), rule.branch(), rule.priority(), state, List.of(), List.of(), null);
    }
}
