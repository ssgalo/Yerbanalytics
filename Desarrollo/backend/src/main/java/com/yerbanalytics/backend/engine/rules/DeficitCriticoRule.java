package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.riego.CalculoRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * R-02 · Déficit hídrico crítico ({@code reglas_v2} §5).
 *
 * <p>Con humedad {@code < riego.umbral-critico} (35 %) ordena regar el sector con el volumen máximo
 * ({@code riego.volumen-max-evento}), a cualquier hora, aunque el pronóstico anuncie lluvia y aunque el
 * sector esté en pausa por R-06 (principio 3: el clima sólo agrega protección, nunca la quita), y emite
 * una alerta {@code CRITICAL} por macro-zona.
 *
 * <p><b>Cómo gana:</b> corre ANTES que R-05, R-06 y R-03 y el orquestador conserva las acciones ya emitidas
 * aunque después se corte la rama; además esas tres compuertas sólo actúan cuando aplica R-01, o sea que
 * con déficit crítico registran "no aplica".
 *
 * <p><b>Tope (design D9.2):</b> v2 limita R-02 a un riego cada 12 h por sector sólo cuando S-06 bloqueó la
 * macro-zona. Sin S-06, un sensor roto que marca "seco" dispararía R-02 en cada ciclo (6 L × 6 al día en
 * 10 L de sustrato): el tope se aplica SIEMPRE, con {@code riego.exceptuado-bloqueo}. Con el tope vencido
 * emite {@code ABORT_RIEGO} citándolo.
 */
@Component
public class DeficitCriticoRule implements Rule {

    private static final int PRIORITY = 4;
    /** Nombre de la regla: también es la {@code regla} que queda en los eventos "Riego" que ordenó R-02. */
    public static final String NAME = SolicitudRiego.REGLA_DEFICIT_CRITICO;
    static final String TEXTO_ALERTA = "Déficit hídrico crítico";

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
        return "🚨 Déficit hídrico crítico (R-02)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.UMBRAL_CRITICO, ParametrosRiego.VOLUMEN_MAX_EVENTO,
                ParametrosRiego.CAUDAL_EMISOR, ParametrosRiego.EXCEPTUADO_BLOQUEO);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humedad = RiegoRuleSupport.humedad(ctx);
        boolean critico = ev.comparar("Humedad de sustrato", humedad, Operador.LT, ParametrosRiego.UMBRAL_CRITICO);

        if (!critico) {
            return List.of(RuleAction.noopInfo(NAME, humedad == null || humedad.isNaN()
                    ? "Sin lectura de humedad de sustrato: no se evalúa el déficit crítico."
                    : String.format("Humedad de sustrato %s%% sin déficit crítico (umbral %s%%).",
                            RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(ev.numero(ParametrosRiego.UMBRAL_CRITICO)))));
        }

        // Tope: un riego crítico por sector cada N horas (sin riego crítico previo no hay nada que comparar).
        Long ultimoCritico = ctx.riego().ultimoRiegoCriticoMs();
        if (ultimoCritico != null) {
            double horas = RiegoRuleSupport.horasDesde(ultimoCritico, ctx.now());
            boolean dentroDelTope = ev.comparar("Horas desde el último riego por déficit crítico", horas,
                    Operador.LT, ParametrosRiego.EXCEPTUADO_BLOQUEO);
            if (dentroDelTope) {
                return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                        "Déficit crítico (%s%%), pero el sector ya recibió un riego crítico a las %s: se respeta el tope de "
                                + "un riego cada %s h para no regar sin fin con un sensor que pueda estar fallando.",
                        RiegoRuleSupport.num(humedad), RiegoRuleSupport.hora(ultimoCritico),
                        RiegoRuleSupport.num(ev.numero(ParametrosRiego.EXCEPTUADO_BLOQUEO)))));
            }
        }

        CalculoRiego.PlanRiego plan = CalculoRiego.volumenMaximo(
                ev.numero(ParametrosRiego.VOLUMEN_MAX_EVENTO), ev.numero(ParametrosRiego.CAUDAL_EMISOR));
        if (plan.duracionSeg() < 1) {
            // Defensa: con el rango del catálogo no ocurre, pero nunca se ordena un riego de 0 s.
            return List.of(RuleAction.noopInfo(NAME, String.format(
                    "Déficit crítico (%s%%), pero el volumen calculado es 0 L: no se ordena el riego.",
                    RiegoRuleSupport.num(humedad))));
        }
        String motivo = String.format(
                "Déficit hídrico crítico: humedad de sustrato %s%% bajo %s%%. Regar %s con el volumen máximo, a cualquier hora%s.",
                RiegoRuleSupport.num(humedad), RiegoRuleSupport.num(ev.numero(ParametrosRiego.UMBRAL_CRITICO)),
                RiegoRuleSupport.volumenYTiempo(plan.volumenL(), plan.duracionSeg()),
                plan.recortado() ? " (duración recortada al máximo de la válvula)" : "");
        RuleAction riego = RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, motivo, plan.aDetalle(humedad));
        RuleAction alerta = RuleAction.of(ActionType.ALERTA, NAME,
                String.format("Humedad de sustrato %s%% bajo el umbral crítico.", RiegoRuleSupport.num(humedad)),
                new DetalleAlerta(NivelAlerta.CRITICAL, TEXTO_ALERTA));
        return List.of(riego, alerta);
    }
}
