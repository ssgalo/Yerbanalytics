package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.HardwareCoverage;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * S-04: ninguna regla autónoma actúa sobre un alcance al que le falta mapear un actuador que su rama requiere
 * (HU-18 CA-04, {@code reglas_v2} §4).
 *
 * <p><b>Prioridad:</b> 1 — justo después del bloqueo manual y antes de la frescura de la lectura: es una condición
 * de la instalación, no del momento, y no tiene sentido evaluar ni siquiera el sensor de algo que no se puede accionar.
 *
 * <p><b>Qué requiere cada rama</b> (la cobertura la arma el motor una vez por zona, ver {@link HardwareCoverage}):
 * <ul>
 *   <li><b>Riego:</b> la electroválvula del sector y el nodo testigo de su macro-zona (sin él no hay humedad).</li>
 *   <li><b>Insumos:</b> la bomba peristáltica del sector. El hardware admite una sola por sector; cuando
 *       {@code add-dosificacion-base} registre una por insumo, exigirá todas.</li>
 *   <li><b>Mediasombra:</b> al menos una registrada en algún sector de la macro-zona (la v2 la exige por MZ; el
 *       hardware las registra por sector).</li>
 * </ul>
 *
 * <p><b>Acciones:</b> aborta sólo la rama a la que le falta algo ({@code ABORT_RIEGO}, {@code ABORT_INSUMO},
 * {@code ABORT_MEDIASOMBRA}), nunca {@code ABORT_ALL}: un sector sin bomba sí puede regarse. Sin falta, {@code NOOP_INFO}.
 * No emite alertas: ya se avisó al configurar el hardware. No lleva marca de cancelación del riego encolado: la
 * solicitud ya decidida vence sola a los dos ciclos y el despacho la revalida.
 *
 * <p><b>Simulador:</b> el bypass {@code hardware} ignora los actuadores faltantes (el sector se evalúa completo).
 */
@Component
public class IncompleteHardwareRule implements Rule {

    private static final int PRIORITY = 1;
    public static final String NAME = "IncompleteHardwareRule";
    /** Bypass key: evaluates the sector as if every actuator were mapped. */
    public static final String BYPASS_HARDWARE = "hardware";

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
        return "🧰 Hardware incompleto (S-04)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.GLOBAL;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_HARDWARE);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        HardwareCoverage hardware = ctx.isBypassed(NAME, BYPASS_HARDWARE) ? HardwareCoverage.COMPLETE : ctx.hardware();
        String sectorId = ctx.sector().getId();
        List<RuleAction> aborts = new ArrayList<>();

        if (!ev.compararFijo("Electroválvula del sector y nodo testigo de la zona mapeados",
                hardware.canIrrigate(), Operador.EQ, true)) {
            aborts.add(RuleAction.of(ActionType.ABORT_RIEGO, NAME, "Hardware incompleto: "
                    + faltaParaRegar(sectorId, hardware) + " El riego autónomo del sector queda anulado."));
        }
        if (!ev.compararFijo("Bomba peristáltica del sector mapeada", hardware.canDose(), Operador.EQ, true)) {
            aborts.add(RuleAction.of(ActionType.ABORT_INSUMO, NAME, "Hardware incompleto: el sector " + sectorId
                    + " no tiene bomba peristáltica registrada. La dosificación autónoma del sector queda anulada."));
        }
        if (!ev.compararFijo("Mediasombra de la macro-zona mapeada", hardware.canShade(), Operador.EQ, true)) {
            aborts.add(RuleAction.of(ActionType.ABORT_MEDIASOMBRA, NAME, "Hardware incompleto: ningún sector de la "
                    + "macro-zona del sector " + sectorId + " tiene mediasombra registrada. El control de la malla queda anulado."));
        }

        return aborts.isEmpty()
                ? List.of(RuleAction.noopInfo(NAME, "Hardware completo: el sector tiene mapeados los actuadores que requiere."))
                : List.copyOf(aborts);
    }

    private static String faltaParaRegar(String sectorId, HardwareCoverage hardware) {
        List<String> faltan = new ArrayList<>();
        if (!hardware.valve()) {
            faltan.add("el sector " + sectorId + " no tiene electroválvula registrada");
        }
        if (!hardware.zoneNode()) {
            faltan.add("la macro-zona no tiene nodo testigo registrado");
        }
        return String.join(" y ", faltan) + ".";
    }
}
