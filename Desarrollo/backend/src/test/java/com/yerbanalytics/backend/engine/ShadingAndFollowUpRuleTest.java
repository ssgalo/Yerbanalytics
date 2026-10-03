package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.engine.rules.ShadingRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.rules.FollowUpRule;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link ShadingRule} y {@link FollowUpRule} (tarea 4.7 / 5.2).
 */
@DisplayName("ShadingRule y FollowUpRule")
@ExtendWith(MockitoExtension.class)
class ShadingAndFollowUpRuleTest {

    @Mock
    private RustificacionEtapaRepository rustificacionRepository;

    @Mock
    private HistorialService historialService;

    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        sector = RuleContextTestFactory.sectorBasico("MZ-4-001");
        sector.setActuadorShade(50); // apertura actual
        zona = RuleContextTestFactory.zonaBasica("MZ-4");
    }

    // ===== ShadingRule =====

    @Test
    @DisplayName("ShadingRule: sin fecha de siembra → NOOP_INFO")
    void sinFechaSiembra_noopInfo() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ShadingRule: pico UV sobre umbral → MOVER_MEDIASOMBRA protector")
    void picoUV_moverMediasombraProtector() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 9.5, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of()); // UV > 7
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).contains("[apertura=30]");
    }

    @Test
    @DisplayName("ShadingRule: apertura ya correcta según plan → NOOP_INFO")
    void aperturaYaCorrecta_noopInfo() {
        String hoy = LocalDate.now(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA).format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, hoy);
        // sector tiene apertura 50%, plan dice 50%
        sector.setActuadorShade(50);
        when(rustificacionRepository.findAllByOrderByOrdenAsc())
                .thenReturn(List.of(new RustificacionEtapaEntity(1, 1, 365, 50)));

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ShadingRule: el día del ciclo sale del reloj del contexto en la zona del vivero, no de la del JVM")
    void diaDelCiclo_usaLaZonaDelVivero() {
        // 01:00 UTC del 3/10 son las 22:00 del 2/10 en Buenos Aires: siembra el 1/10 → día 2 del ciclo (no 3).
        ShadingRule rule = new ShadingRule(rustificacionRepository, "2026-10-01");
        sector.setActuadorShade(50);
        when(rustificacionRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                new RustificacionEtapaEntity(1, 1, 2, 40), new RustificacionEtapaEntity(2, 3, 5, 60)));
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.parse("2026-10-03T01:00:00Z"), null, false);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones.get(0).motivo()).contains("[apertura=40]");
    }

    @Test
    @DisplayName("ShadingRule: apertura diferente al plan → MOVER_MEDIASOMBRA")
    void aperturaDiferenteAlPlan_moverMediasombra() {
        String hoy = LocalDate.now(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA).format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, hoy);
        sector.setActuadorShade(30); // actual 30%, plan dice 60%
        when(rustificacionRepository.findAllByOrderByOrdenAsc())
                .thenReturn(List.of(new RustificacionEtapaEntity(1, 1, 365, 60)));

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).contains("[apertura=60]");
    }

    @Test
    @DisplayName("ShadingRule: declara sus tres parámetros y registra el UV contra el umbral")
    void declaraLosParametrosYRegistraElUv() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 9.5, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of());
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.conForecast(sector, zona, forecast), ev);

        assertThat(rule.parametros()).containsExactly(ParametrosMediasombra.UV_UMBRAL,
                ParametrosMediasombra.APERTURA_PROTECCION_UV, ParametrosMediasombra.APERTURA_MAXIMA);
        assertThat(ev.comparaciones()).hasSize(1);
        assertThat(ev.comparaciones().get(0).recibido()).isEqualTo(9.5);
        assertThat(ev.comparaciones().get(0).operador()).isEqualTo(Operador.GE);
        assertThat(ev.comparaciones().get(0).umbral()).isEqualTo(7.0);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("ShadingRule: sin pronóstico el UV queda SIN_DATO")
    void sinPronosticoElUvQuedaSinDato() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.basico(sector, zona), ev);

        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("ShadingRule: umbral UV, apertura protectora y apertura máxima salen del catálogo")
    void losParametrosSalenDelCatalogo() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 8.0, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        // UV 8 < umbral 9: no es pico.
        assertThat(rule.evaluate(ctx, ev(rule, java.util.Map.of(ParametrosMediasombra.UV_UMBRAL, "9"))).get(0).type())
                .isEqualTo(ActionType.NOOP_INFO);
        // Apertura protectora 40 %.
        assertThat(rule.evaluate(ctx, ev(rule, java.util.Map.of(ParametrosMediasombra.APERTURA_PROTECCION_UV, "40"))).get(0).motivo())
                .contains("[apertura=40]");
        // El tope de apertura (20 %) manda sobre la protectora (30 %).
        assertThat(rule.evaluate(ctx, ev(rule, java.util.Map.of(ParametrosMediasombra.APERTURA_MAXIMA, "20"))).get(0).motivo())
                .contains("[apertura=20]");
    }

    // ===== FollowUpRule =====

    @Test
    @DisplayName("FollowUpRule: siempre emite NOOP_INFO (tarea interna)")
    void seguimientoRule_emiteNoopInfo() {
        FollowUpRule rule = new FollowUpRule(historialService);
        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("FollowUpRule: sin parámetros ni comparaciones")
    void seguimientoRule_sinParametrosNiComparaciones() {
        FollowUpRule rule = new FollowUpRule(historialService);
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.basico(sector, zona), ev);

        assertThat(rule.parametros()).isEmpty();
        assertThat(ev.comparaciones()).isEmpty();
    }

    @Test
    @DisplayName("FollowUpRule: prioridad es 20 (la última)")
    void seguimientoRule_prioridad20() {
        FollowUpRule rule = new FollowUpRule(historialService);
        assertThat(rule.priority()).isEqualTo(20);
    }
}
