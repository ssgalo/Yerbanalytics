package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.rules.IrrigationRule;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link IrrigationRule}: misma decisión que antes de migrarla al catálogo, ahora con su traza. */
@DisplayName("IrrigationRule")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IrrigationRuleTest {

    @Mock
    private HistorialRepository historialRepository;

    private IrrigationRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new IrrigationRule(historialRepository);
        sector = RuleContextTestFactory.sectorBasico("MZ-1-007");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    private RuleContext conHumedad(Double humSus, boolean conConfig) {
        List<Metric> metricas = humSus == null ? List.of()
                : List.of(new Metric("humSus", "Humedad de sustrato", "%", humSus, humSus + "", "ok", "#000", null));
        return new RuleContext(sector, zona, List.of(), metricas,
                conConfig ? RuleContextTestFactory.defaultConfig() : null,
                "ok", Instant.now(), null, false);
    }

    @Test
    void sinLecturaDeHumedad_noopInfoYNoConsultaElHistorial() {
        List<RuleAction> acciones = evaluar(rule, conHumedad(null, true));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("Sin lectura de humedad");
        verify(historialRepository, never()).countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong());
    }

    @Test
    void humedadSobreElUmbral_noopInfo() {
        List<RuleAction> acciones = evaluar(rule, conHumedad(50.0, true));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("No se riega");
    }

    @Test
    void humedadExactamenteEnElUmbral_noRiega() {
        assertThat(evaluar(rule, conHumedad(42.0, true))).extracting(RuleAction::type)
                .containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void humedadBajoElUmbralSinRiegosPrevios_activaLaValvulaConElTiempoMaximo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong())).thenReturn(0L);

        List<RuleAction> acciones = evaluar(rule, conHumedad(41.0, true));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ACTIVAR_VALVULA);
        assertThat(acciones.get(0).motivo()).contains("[tiempo-max-seg=120]").contains("41").contains("42");
    }

    @Test
    void humedadBajoElUmbralConUnRiegoEnLas24h_abortaPorLimiteOperativo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong())).thenReturn(1L);

        List<RuleAction> acciones = evaluar(rule, conHumedad(38.0, true));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).contains("MZ-1-007").contains("500");
    }

    @Test
    void sinConfiguracionOperativa_riegaConMotivoSimpleSinConsultarElHistorial() {
        List<RuleAction> acciones = evaluar(rule, conHumedad(38.0, false));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ACTIVAR_VALVULA);
        assertThat(acciones.get(0).motivo()).doesNotContain("tiempo-max-seg");
        verify(historialRepository, never()).countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong());
    }

    @Test
    void declaraSusTresParametros() {
        assertThat(rule.parametros()).containsExactly(ParametrosRiego.UMBRAL_HUMEDAD,
                ParametrosRiego.MAX_RIEGOS_24H_SECTOR, ParametrosRiego.TIEMPO_MAX_APERTURA);
    }

    @Test
    void laTrazaRegistraLaHumedadContraElUmbralYLosRiegosContraElMaximo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong())).thenReturn(0L);
        Evaluacion ev = ev(rule);

        rule.evaluate(conHumedad(38.0, true), ev);

        assertThat(ev.comparaciones()).extracting(Comparacion::etiqueta, Comparacion::operador, Comparacion::umbral,
                        Comparacion::resultado)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Humedad de sustrato", Operador.LT, 42.0, ResultadoComparacion.CUMPLE),
                        org.assertj.core.api.Assertions.tuple("Riegos en las últimas 24 h", Operador.GE, 1.0, ResultadoComparacion.NO_CUMPLE));
        assertThat(ev.comparaciones().get(0).clave()).isEqualTo("riego.umbral-humedad");
    }

    @Test
    void sinLecturaLaTrazaQuedaSinDato() {
        Evaluacion ev = ev(rule);

        rule.evaluate(conHumedad(null, true), ev);

        assertThat(ev.comparaciones()).extracting(Comparacion::resultado).containsExactly(ResultadoComparacion.SIN_DATO);
    }

    @Test
    void elUmbralSaleDelCatalogo_unOverrideCambiaLaDecision() {
        // Con el umbral en 35 (override), 38 % ya no necesita riego.
        Evaluacion ev = ev(rule, java.util.Map.of(ParametrosRiego.UMBRAL_HUMEDAD, "35"));

        List<RuleAction> acciones = rule.evaluate(conHumedad(38.0, true), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(ev.comparaciones().get(0).umbral()).isEqualTo(35.0);
    }

    @Test
    void elTiempoMaximoSaleDelCatalogoYNoDeLaConfiguracionOperativa() {
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-007"), eq("Riego"), anyLong())).thenReturn(0L);
        // La config operativa del test dice 120 s; el override del catálogo manda.
        List<RuleAction> acciones = rule.evaluate(conHumedad(38.0, true),
                ev(rule, java.util.Map.of(ParametrosRiego.TIEMPO_MAX_APERTURA, "90")));

        assertThat(acciones.get(0).motivo()).contains("[tiempo-max-seg=90]");
    }

    @Test
    void cambiarLaBandaIdealDeHumSusNoCambiaLaDecision() {
        // Antes el umbral de riego era idealMin de la métrica; ahora son cosas separadas.
        MetricSpec spec = new MetricSpec("humSus", "Humedad de sustrato", "%", new Double[]{60.0, 80.0},
                new Double[]{0.0, 0.0}, new Double[]{0.0, 0.0}, 0, 70.0, "ambiente", true);
        Metric m = new Metric("humSus", "Humedad de sustrato", "%", 50.0, "50", "ok", "#000", spec);
        RuleContext ctx = new RuleContext(sector, zona, List.of(spec), List.of(m),
                RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false);

        // 50 % está bajo la banda ideal (60) pero sobre el umbral de riego (42): no se riega.
        assertThat(evaluar(rule, ctx)).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }
}
