package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DetalleOutbreakTreatment;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosBiotico;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.supply.FungicideLog;
import com.yerbanalytics.backend.supply.Supply;
import com.yerbanalytics.backend.supply.ValidDiagnosis;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * F-04 · Foco (reglas_v2 §8.5): si {@code biotico.sectores-foco} o más sectores de la macro-zona tienen el mismo
 * diagnóstico biótico vigente dentro de las últimas {@code biotico.horas-foco} horas, pide fitosanitario también para los
 * sectores lindantes de cada afectado (los hasta 8 de la grilla, de la misma zona), aunque estén sanos, y avisa con un
 * {@code CRITICAL} «Foco de [diagnóstico] en MZ-N».
 *
 * <p><b>Es de la macro-zona</b> ({@link ZoneRule}): cuenta los sectores de la zona y decide sobre varios a la vez. Cada
 * lindante lleva su orden ({@link DetalleOutbreakTreatment}); el {@code ApplicationService} verifica las demás
 * precondiciones (ventana, temperatura, lluvia, humedad, intervalo y tope), todas salvo P-D1: un lindante sano no tiene diagnóstico.
 *
 * <p><b>«Mismo diagnóstico»</b> es el tipo de daño ({@link BioticKind}): la plaga foliar y el ácaro cuentan juntos; el daño
 * biótico genérico no forma foco. <b>«Vigente»</b> es concluyente y con la confianza mínima dentro de la ventana del foco,
 * que puede ser más larga que la vigencia de P-D1.
 *
 * <p><b>Un foco, un tratamiento.</b> Sin estado propio: no pide a un lindante que ya recibió el fitosanitario dentro de la
 * ventana ni al que ya lo tiene en cola, y si no queda ninguno por tratar no repite ni las órdenes ni la alerta. Un lindante
 * bloqueado, sin bomba o con falla hidráulica queda fuera de la tanda, pero el foco se avisa igual.
 *
 * <p><b>Supuesto a validar con el vivero:</b> los lindantes salen de la disposición visual de la grilla ({@link GridNeighbors}).
 *
 * <p><b>Simulador:</b> el cooldown {@value #COOLDOWN_WINDOW} (minutos) reemplaza la ventana del foco.
 */
@Component
public class OutbreakRule implements ZoneRule {

    /** Prioridad: única entre las reglas por sector y por macro-zona; antes que el plan de nutrición (sanidad antes que rutina). */
    private static final int PRIORITY = 18;
    public static final String NAME = "OutbreakRule";
    /** Cooldown key: minutes of the outbreak window. */
    public static final String COOLDOWN_WINDOW = "window";

    private final SectorGrid grid;
    private final FungicideLog fungicides;

    public OutbreakRule(SectorGrid grid, FungicideLog fungicides) {
        this.grid = grid;
        this.fungicides = fungicides;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "🔥 Foco biótico (F-04)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    @Override
    public Set<String> cooldownKeys() {
        return Set.of(COOLDOWN_WINDOW);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosBiotico.OUTBREAK_SECTORS, ParametrosBiotico.OUTBREAK_HOURS,
                ParametrosDiagnostico.CONFIANZA_MINIMA);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        Duration window = window(ctx, ev);
        Map<BioticKind, List<SectorEntity>> affected = affectedByKind(ctx, ev, window);

        List<RuleAction> actions = new ArrayList<>();
        Set<String> requested = new HashSet<>();
        for (BioticKind kind : BioticKind.values()) {
            List<SectorEntity> sectors = affected.getOrDefault(kind, List.of());
            if (ev.comparar("Sectores con " + kind.label() + " vigente en la ventana", (double) sectors.size(),
                    Operador.GE, ParametrosBiotico.OUTBREAK_SECTORS)) {
                actions.addAll(treatOutbreak(ctx, kind, sectors, window, requested));
            }
        }
        if (actions.isEmpty()) {
            return List.of(RuleAction.noopInfo(NAME, "No hay un foco biótico en " + ctx.zone().getId() + "."));
        }
        return actions;
    }

    /** La ventana del foco: la del simulador (minutos) o la del catálogo (horas). */
    private Duration window(ZoneRuleContext ctx, Evaluacion ev) {
        return ctx.cooldownOverrideMinutes(NAME, COOLDOWN_WINDOW)
                .map(minutes -> Duration.ofSeconds(Math.round(minutes * 60)))
                .orElseGet(() -> Duration.ofMinutes(Math.round(ev.numero(ParametrosBiotico.OUTBREAK_HOURS) * 60)));
    }

    /** Los sectores de la zona con un diagnóstico biótico vigente dentro de la ventana, por tipo de daño. */
    private Map<BioticKind, List<SectorEntity>> affectedByKind(ZoneRuleContext ctx, Evaluacion ev, Duration window) {
        double minConfidence = ev.numero(ParametrosDiagnostico.CONFIANZA_MINIMA);
        double windowHours = window.toMillis() / 3_600_000.0;
        Map<BioticKind, List<SectorEntity>> affected = new EnumMap<>(BioticKind.class);
        for (SectorEntity sector : ctx.sectors()) {
            Optional<BioticKind> kind = BioticKind.of(sector.getDiagnosisEstado());
            if (kind.isEmpty()) {
                continue;
            }
            boolean current = ValidDiagnosis.of(sector.getDiagnosisEstado(), sector.getDiagnosisConf(),
                    sector.getDiagnosisTs(), ctx.now(), minConfidence, windowHours).valid();
            if (current) {
                affected.computeIfAbsent(kind.get(), k -> new ArrayList<>()).add(sector);
            }
        }
        return affected;
    }

    private List<RuleAction> treatOutbreak(ZoneRuleContext ctx, BioticKind kind, List<SectorEntity> affected,
                                           Duration window, Set<String> requested) {
        String zoneId = ctx.zone().getId();
        List<SectorEntity> untreated = neighborsOf(ctx, affected).stream()
                .filter(s -> !fungicides.treatedSince(s.getId(), ctx.now().minus(window)) && !fungicides.isPending(s.getId()))
                .toList();
        if (untreated.isEmpty()) {
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Foco de %s en %s en curso: los lindantes ya recibieron el fitosanitario o lo tienen en cola.",
                    kind.label(), zoneId)));
        }

        List<RuleAction> actions = new ArrayList<>();
        int excluded = 0;
        for (SectorEntity neighbor : untreated) {
            Optional<String> block = ctx.dosingBlock(neighbor);
            if (block.isPresent()) {
                excluded++;
            } else if (requested.add(neighbor.getId())) {
                actions.add(RuleAction.of(ActionType.ACTIVAR_BOMBA, NAME, String.format(
                        "%s lindante de un foco de %s en %s.", neighbor.getId(), kind.label(), zoneId),
                        new DetalleOutbreakTreatment(neighbor.getId(), Supply.FUNGICIDE)));
            }
        }
        String alert = String.format("Foco de %s en %s: %d sector(es) afectados; se pidió fitosanitario para %d lindante(s)%s.",
                kind.label(), zoneId, affected.size(), untreated.size() - excluded,
                excluded > 0 ? " (" + excluded + " quedan fuera: bloqueados, sin bomba o con falla hidráulica)" : "");
        actions.add(new RuleAction(ActionType.ALERTA, NAME, alert, new DetalleAlerta(NivelAlerta.CRITICAL, alert)));
        return actions;
    }

    /** Los lindantes de todos los afectados que existen en la zona, sin repetir y en orden de grilla. */
    private List<SectorEntity> neighborsOf(ZoneRuleContext ctx, List<SectorEntity> affected) {
        int total = ctx.sectors().size();
        int perRow = grid.sectorsPerRow(total);
        Map<Integer, SectorEntity> byNumber = ctx.sectors().stream()
                .collect(Collectors.toMap(SectorEntity::getN, Function.identity(), (a, b) -> a));
        Set<Integer> numbers = new TreeSet<>();
        affected.forEach(s -> numbers.addAll(GridNeighbors.of(s.getN(), perRow, total)));
        return numbers.stream().map(byNumber::get).filter(Objects::nonNull).toList();
    }
}
