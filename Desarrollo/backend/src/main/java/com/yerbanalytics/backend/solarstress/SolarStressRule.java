package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.DiagnosisGate;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.Severity;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.parametros.ParametrosEstres;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.riego.CalculoRiego;
import com.yerbanalytics.backend.engine.riego.PrecondicionesRiego;
import com.yerbanalytics.backend.engine.rules.PausaTrasAplicacionRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * F-S1 · Estrés solar con radiación alta (reglas_v2 §8.5): el estrés solar no usa químicos, se corrige con agua y sombra.
 * Ante un diagnóstico Estrés solar vigente, si en las últimas 48 h la luz fue {@code ≥} {@code estres.luz-alta} (80 %) en
 * alguna lectura entre las 10 y las 16 h <b>o</b> la temperatura llegó a {@code ≥} {@code estres.temp-alta} (32 °C), y el
 * sustrato está por debajo de {@code estres.humedad-reposicion} (55 %), ordena un <b>riego de reposición</b> del sector con
 * la fórmula de volumen de R-01 y avisa con un {@code WARNING} ({@code CRITICAL} con severidad alta).
 *
 * <p><b>No es R-01.</b> El disparo es el diagnóstico, no la humedad de la zona. Pero se despacha igual: la orden entra en la
 * cola de la zona y la abre el {@code DespachoRiego}, que la trata como riego común y la revalida al abrir cada válvula
 * (ventana de riego, pausa por aplicación R-06, lluvia y un riego por ciclo). Está en la rama de riego y corre después de
 * R-01…R-06, así que un corte de seguridad (sustrato saturado R-04, sensor sin datos, falla hidráulica, bloqueo manual) la
 * deja sin alcanzar; y comprueba por su cuenta la ventana y la pausa, porque las reglas de R-05 y R-06 sólo actúan cuando
 * aplica R-01 (humedad bajo el umbral de riego), y F-S1 también riega con humedad de 45 a 55 %.
 *
 * <p><b>Simulador:</b> el bypass {@value #BYPASS_WINDOW} ignora la ventana de riego (también en el despacho) y
 * {@value #BYPASS_SAMPLES} usa la última lectura de luz y temperatura en lugar de las 48 h.
 */
@Component
public class SolarStressRule implements Rule {

    /** Prioridad: después de las reglas de riego por déficit; única entre las reglas por sector y por macro-zona. */
    private static final int PRIORITY = 26;
    public static final String NAME = "SolarStressRule";
    /** Bypass key: ignores the irrigation window. */
    public static final String BYPASS_WINDOW = "window";
    /** Bypass key: uses the last light and temperature reading instead of the last 48 h. */
    public static final String BYPASS_SAMPLES = "samples";

    private final SolarExposure exposure;

    public SolarStressRule(SolarExposure exposure) {
        this.exposure = exposure;
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
        return "☀️ Estrés solar con radiación alta (F-S1)";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    /** Decide con el diagnóstico de IA: actúa una vez por diagnóstico nuevo. */
    @Override
    public boolean usaDiagnostico() {
        return true;
    }

    @Override
    public Set<String> bypassKeys() {
        return Set.of(BYPASS_WINDOW, BYPASS_SAMPLES);
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosDiagnostico.CONFIANZA_MINIMA, ParametrosDiagnostico.VALIDITY_HOURS,
                ParametrosEstres.HIGH_LIGHT, ParametrosEstres.HIGH_TEMP, ParametrosEstres.REPLENISH_MOISTURE,
                ParametrosRiego.VENTANA_NORMAL, ParametrosRiego.PAUSA_TRAS_APLICACION, ParametrosRiego.HUMEDAD_OBJETIVO,
                ParametrosRiego.LITROS_POR_PUNTO, ParametrosRiego.VOLUMEN_MAX_EVENTO, ParametrosRiego.CAUDAL_EMISOR);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Optional<RuleAction> notApplicable = DiagnosisGate.check(ctx, ev, NAME, "estrés solar", "Estrés solar"::equals);
        if (notApplicable.isPresent()) {
            return List.of(notApplicable.get());
        }
        String sectorId = ctx.sector().getId();

        SolarExposure.Peaks peaks = exposure.peaks(ctx, ctx.isBypassed(NAME, BYPASS_SAMPLES));
        boolean highLight = ev.comparar("Luz máxima entre las 10 y las 16 h de las últimas 48 h", peaks.light(), Operador.GE,
                ParametrosEstres.HIGH_LIGHT);
        boolean hot = ev.comparar("Temperatura máxima de las últimas 48 h", peaks.temp(), Operador.GE, ParametrosEstres.HIGH_TEMP);
        if (!(highLight || hot)) {
            return List.of(RuleAction.noopInfo(NAME, "Estrés solar en " + sectorId
                    + " sin radiación alta ni calor en las últimas 48 h: no hay un riego de reposición que justificar (F-S1)."));
        }

        Double moisture = ctx.validMetricRaw("humSus");
        if (!ev.comparar("Humedad de sustrato", moisture, Operador.LT, ParametrosEstres.REPLENISH_MOISTURE)) {
            return List.of(RuleAction.noopInfo(NAME, "Estrés solar en " + sectorId + " con el sustrato suficiente"
                    + (moisture == null ? " (sin lectura de humedad)" : " (" + trim(moisture) + " %)") + ": no se repone agua."));
        }

        LocalTime localTime = ctx.now().atZone(ZonaHorariaVivero.ZONA).toLocalTime();
        if (!ev.compararVentana("Hora local", localTime, ParametrosRiego.VENTANA_NORMAL) && !ctx.isBypassed(NAME, BYPASS_WINDOW)) {
            return List.of(RuleAction.noopInfo(NAME, "Estrés solar en " + sectorId + ", pero fuera de la ventana de riego ("
                    + ev.ventana(ParametrosRiego.VENTANA_NORMAL).canonico() + "): la reposición espera a la próxima lectura dentro de ella."));
        }

        Long lastApplication = ctx.riego().ultimaAplicacionMs();
        if (lastApplication != null) {
            double hoursSince = PrecondicionesRiego.horasDesde(lastApplication, ctx.now());
            double pauseHours = ctx.cooldownOverrideMinutes(PausaTrasAplicacionRule.NAME, PausaTrasAplicacionRule.COOLDOWN_PAUSE)
                    .map(minutes -> minutes / 60.0)
                    .orElseGet(() -> ev.numero(ParametrosRiego.PAUSA_TRAS_APLICACION));
            if (!ev.compararFijo("Horas desde la última aplicación de insumo", hoursSince, Operador.GE, pauseHours)) {
                return List.of(RuleAction.noopInfo(NAME, "Al sector se le aplicó un insumo hace menos de " + trim(pauseHours)
                        + " h (R-06): no se riega para no lavar el producto."));
            }
        }

        CalculoRiego.PlanRiego plan = CalculoRiego.porDeficit(moisture, ev.numero(ParametrosRiego.HUMEDAD_OBJETIVO),
                ev.numero(ParametrosRiego.LITROS_POR_PUNTO), ev.numero(ParametrosRiego.VOLUMEN_MAX_EVENTO),
                ev.numero(ParametrosRiego.CAUDAL_EMISOR));
        if (plan.duracionSeg() < 1) {
            return List.of(RuleAction.noopInfo(NAME, "Estrés solar en " + sectorId
                    + ": el volumen de reposición calculado es 0 L, no se riega."));
        }

        Severity severity = Severity.parse(ctx.sector().getDiagnosisSev()).orElse(Severity.MILD);
        String reason = String.format("Estrés solar en %s con %s y el sustrato en %s %%: riego de reposición de %s L (%d s).",
                sectorId, highLight ? "radiación alta" : "calor", trim(moisture), trim(plan.volumenL()), plan.duracionSeg());
        String alert = "Estrés solar en " + sectorId + ": se repone agua al sector.";
        NivelAlerta level = severity.isHigh() ? NivelAlerta.CRITICAL : NivelAlerta.WARNING;
        return List.of(
                RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, reason, plan.aDetalle(moisture)),
                new RuleAction(ActionType.ALERTA, NAME, alert, new DetalleAlerta(level, alert)));
    }

    private static String trim(double value) {
        return new java.math.BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
