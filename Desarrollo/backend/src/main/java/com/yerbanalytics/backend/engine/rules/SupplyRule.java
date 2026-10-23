package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleInsumo;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.supply.Supply;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de dosificación de insumo por diagnóstico IA (HU-07). <b>Retirada por defecto</b>: arranca apagada
 * ({@code yerbanalytics.reglas.retiradas}, ver {@code RuleOverridesStore}) hasta que las reglas v2 de aplicación
 * (N-*, F-*) la reemplacen; sin ellas, borrarla dejaría al vivero sin dosificación.
 *
 * <p><b>Prioridad:</b> 12 (ejecutora, corre justo después de {@link RiegoRule}).
 *
 * <p><b>Condición:</b> el sector está en estado {@code "critical"} Y la confianza del
 * diagnóstico alcanza {@code diagnostico.confianza-minima} (HU-07 CA-01/02).
 *
 * <p><b>Acción si se cumple:</b> {@code ACTIVAR_BOMBA} para fertilizante. La regla sólo decide <i>qué</i>; el
 * {@link com.yerbanalytics.backend.engine.ActionExecutor} se lo pide al {@code ApplicationService}, que verifica las
 * precondiciones (ventana, temperatura, lluvia, humedad, intervalos), aplica la dosis del catálogo y descuenta el tanque.
 *
 * <p><b>Acción si no se cumple:</b> {@code NOOP_INFO} — Registro de Inacción
 * que explica qué condición falló (status, confianza insuficiente, o sin diagnóstico).
 */
@Component
public class SupplyRule implements Rule {

    private static final int PRIORITY = 12;
    private static final String NAME = "SupplyRule";

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
        return "🧪 Dosificación de insumo";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.INSUMO;
    }

    /** Decide con el diagnóstico de IA: actúa una vez por diagnóstico nuevo. */
    @Override
    public boolean usaDiagnostico() {
        return true;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosDiagnostico.CONFIANZA_MINIMA);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        String status = ctx.finalStatus();
        Double conf   = ctx.sector().getDiagnosisConf();
        String estado = ctx.sector().getDiagnosisEstado();

        // Condición fija (no configurable): sólo se dosifica en estado crítico.
        if (!ev.compararFijo("Estado del sector", status, Operador.EQ, "critical")) {
            return List.of(RuleAction.noopInfo(NAME,
                    String.format("Sector en estado '%s' — dosificación solo se activa en estado crítico.", status)));
        }

        boolean confiable = ev.comparar("Confianza del diagnóstico", conf, Operador.GE,
                ParametrosDiagnostico.CONFIANZA_MINIMA);
        if (conf == null) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin confianza de diagnóstico disponible — no se puede evaluar dosificación."));
        }

        double confUmbral = ev.numero(ParametrosDiagnostico.CONFIANZA_MINIMA);
        if (confiable) {
            String motivo = String.format(
                    "Diagnóstico IA: %s (confianza %.0f%% ≥ %.0f%%). Dosificación autorizada.",
                    estado, conf, confUmbral);
            return List.of(RuleAction.of(ActionType.ACTIVAR_BOMBA, NAME, motivo, new DetalleInsumo(Supply.FERTILIZER, true)));
        } else {
            String motivo = String.format(
                    "Diagnóstico IA: %s (confianza %.0f%% < %.0f%% requerida). No se dosifica.",
                    estado, conf, confUmbral);
            return List.of(RuleAction.noopInfo(NAME, motivo));
        }
    }
}
