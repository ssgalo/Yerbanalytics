package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.rules.DeficitCriticoRule;
import com.yerbanalytics.backend.engine.rules.RiegoPorDeficitRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * S-03, qué ve el motor de una métrica descartada: el contexto la marca inválida y las reglas la leen como
 * "sin dato" (no actúan), mientras las demás métricas siguen disponibles.
 */
@DisplayName("RuleContext - validez de las métricas (S-03)")
class RuleContextMetricValidityTest {

    private static RuleContext contexto(Set<String> invalidas) {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        List<Metric> metricas = List.of(
                new Metric("humSus", "Humedad de sustrato", "%", 30.0, "30", "ok", "#000", null),
                new Metric("temp", "Temperatura", "°C", 22.0, "22", "ok", "#000", null));
        return new RuleContext(sector, zona, List.of(), metricas, RuleContextTestFactory.defaultConfig(), "ok",
                Instant.now(), null, false, ContextoRiego.vacio(), RuleOverrides.NONE, invalidas);
    }

    @Test
    @DisplayName("sin métricas descartadas todas son válidas (el contexto de siempre)")
    void todasValidasPorDefecto() {
        RuleContext ctx = RuleContextTestFactory.basico(RuleContextTestFactory.sectorBasico("S"),
                RuleContextTestFactory.zonaBasica("Z"));

        assertThat(ctx.isMetricValid("humSus")).isTrue();
        assertThat(ctx.invalidMetrics()).isEmpty();
    }

    @Test
    @DisplayName("una métrica descartada deja de ser válida y su valor se lee como ausente; las demás no cambian")
    void metricaDescartada() {
        RuleContext ctx = contexto(Set.of("humSus"));

        assertThat(ctx.isMetricValid("humSus")).isFalse();
        assertThat(ctx.validMetricRaw("humSus")).isNull();
        assertThat(ctx.isMetricValid("temp")).isTrue();
        assertThat(ctx.validMetricRaw("temp")).isEqualTo(22.0);
        // metricRaw sigue devolviendo lo que hay (lo usa el dashboard); el motor decide con validMetricRaw.
        assertThat(ctx.metricRaw("humSus")).isEqualTo(30.0);
    }

    @Test
    @DisplayName("una métrica que la zona nunca reportó (sonda sin conectar) se lee como ausente y no rompe la evaluación")
    void metricaSinLecturaSeLeeComoAusente() {
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        ZonaEntity zona = RuleContextTestFactory.zonaBasica("MZ-1");
        List<Metric> metricas = List.of(
                new Metric("humSus", "Humedad de sustrato", "%", 30.0, "30", "ok", "#000", null),
                new Metric("ce", "CE", "dS/m", null, "—", "offline", "#000", null));
        RuleContext ctx = new RuleContext(sector, zona, List.of(), metricas, RuleContextTestFactory.defaultConfig(), "ok",
                Instant.now(), null, false);

        assertThat(ctx.metricRaw("ce")).isNull();
        assertThat(ctx.validMetricRaw("ce")).isNull();
        assertThat(ctx.metricRaw("humSus")).isEqualTo(30.0);
        assertThat(ctx.metricRaw("noExiste")).isNull();
    }

    @Test
    @DisplayName("al cambiar los overrides se conservan las métricas descartadas")
    void conservaAlCambiarOverrides() {
        RuleContext ctx = contexto(Set.of("humSus")).withOverrides(RuleOverrides.NONE);

        assertThat(ctx.isMetricValid("humSus")).isFalse();
    }

    @Test
    @DisplayName("R-01 con la humedad descartada no riega: la traza muestra 'sin dato' y no hay válvula")
    void r01SinHumedad() {
        RiegoPorDeficitRule regla = new RiegoPorDeficitRule();
        Evaluacion ev = ev(regla);

        List<RuleAction> acciones = regla.evaluate(RiegoCtx.a("10:05").humedad(30.0).humedadDescartada().build(), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones()).isNotEmpty()
                .allSatisfy(c -> assertThat(c.resultado()).isEqualTo(ResultadoComparacion.SIN_DATO));
    }

    @Test
    @DisplayName("R-02 con la humedad descartada tampoco actúa, aunque el valor retenido sea crítico")
    void r02SinHumedad() {
        DeficitCriticoRule regla = new DeficitCriticoRule();

        List<RuleAction> acciones = regla.evaluate(RiegoCtx.a("10:05").humedad(20.0).humedadDescartada().build(), ev(regla));

        assertThat(acciones).extracting(RuleAction::type).doesNotContain(ActionType.ACTIVAR_VALVULA);
    }

    @Test
    @DisplayName("con la misma humedad válida, R-01 sí riega (el descarte es lo que lo frena)")
    void r01ConHumedadValida() {
        RiegoPorDeficitRule regla = new RiegoPorDeficitRule();

        List<RuleAction> acciones = regla.evaluate(RiegoCtx.a("10:05").humedad(40.0).build(), ev(regla));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ACTIVAR_VALVULA);
    }
}
