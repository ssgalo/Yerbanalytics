package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.engine.rules.ShadingRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.RustificacionEtapaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.RustificacionEtapaRepository;
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
 * Tests unitarios de {@link ShadingRule} (tarea 4.7).
 */
@DisplayName("ShadingRule")
@ExtendWith(MockitoExtension.class)
class ShadingRuleTest {

    @Mock
    private RustificacionEtapaRepository rustificacionRepository;

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
        assertThat(acciones.get(0).motivo()).contains("[apertura=0]").contains("Mediasombra cerrada")
                .doesNotContain("%");
    }

    @Test
    @DisplayName("ShadingRule: con pico UV reenvía el cierre aunque lo guardado ya sea 0 (el nodo pudo reiniciar)")
    void picoUV_reenviaAunqueLoGuardadoYaSeaCerrado() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        sector.setActuadorShade(0);
        WeatherForecast forecast = new WeatherForecast(10.0, 9.5, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of());

        List<RuleAction> acciones = evaluar(rule, RuleContextTestFactory.conForecast(sector, zona, forecast));

        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
    }

    @Test
    @DisplayName("ShadingRule: sin pico UV ni plan no mueve la mediasombra")
    void sinPico_noMueve() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");

        List<RuleAction> acciones = evaluar(rule, RuleContextTestFactory.basico(sector, zona));

        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ShadingRule: apertura ya correcta según plan → NOOP_INFO")
    void aperturaYaCorrecta_noopInfo() {
        String hoy = LocalDate.now(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA).format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, hoy);
        // sector abierto (100), plan dice 50 → abierta: mismo estado binario
        sector.setActuadorShade(100);
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
        sector.setActuadorShade(100);
        when(rustificacionRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                new RustificacionEtapaEntity(1, 1, 2, 40), new RustificacionEtapaEntity(2, 3, 5, 60)));
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.parse("2026-10-03T01:00:00Z"), null, false);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones.get(0).motivo()).contains("[apertura=0]");
    }

    @Test
    @DisplayName("O-04: sin pronóstico (ni red) el día del plan avanza con el reloj y los horarios rigen igual")
    void planDeRustificacion_avanzaConElRelojSinPronostico() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "2026-10-01");
        when(rustificacionRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                new RustificacionEtapaEntity(1, 1, 2, 40), new RustificacionEtapaEntity(2, 3, 5, 60)));

        // Día 2 del plan: etapa de 40 % → cerrada. Sector abierto: la cierra.
        sector.setActuadorShade(100);
        RuleContext dia2 = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.parse("2026-10-02T15:00:00Z"), null, false);
        assertThat(evaluar(rule, dia2).get(0).motivo()).contains("[apertura=0]");

        // Tres días sin red después (día 5): etapa de 60 % → abierta. Sector cerrado: la abre.
        sector.setActuadorShade(0);
        RuleContext dia5 = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", Instant.parse("2026-10-05T15:00:00Z"), null, false);
        List<RuleAction> acciones = evaluar(rule, dia5);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).doesNotContain("[apertura=0]");
    }

    @Test
    @DisplayName("ShadingRule: apertura diferente al plan → MOVER_MEDIASOMBRA")
    void aperturaDiferenteAlPlan_moverMediasombra() {
        String hoy = LocalDate.now(com.yerbanalytics.backend.config.ZonaHorariaVivero.ZONA).format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, hoy);
        sector.setActuadorShade(0); // cerrada, plan dice 60 → abierta
        when(rustificacionRepository.findAllByOrderByOrdenAsc())
                .thenReturn(List.of(new RustificacionEtapaEntity(1, 1, 365, 60)));

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).contains("[apertura=100]");
    }

    @Test
    @DisplayName("ShadingRule: declara su único parámetro y registra el UV contra el umbral")
    void declaraLosParametrosYRegistraElUv() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 9.5, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of());
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.conForecast(sector, zona, forecast), ev);

        assertThat(rule.parametros()).containsExactly(ParametrosMediasombra.UV_UMBRAL);
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
    @DisplayName("ShadingRule: el umbral UV sale del catálogo")
    void elUmbralSaleDelCatalogo() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 8.0, Instant.now(), 20.0, "Soleado", 40.0, java.util.List.of());
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        // UV 8 < umbral 9: no es pico.
        assertThat(rule.evaluate(ctx, ev(rule, java.util.Map.of(ParametrosMediasombra.UV_UMBRAL, "9"))).get(0).type())
                .isEqualTo(ActionType.NOOP_INFO);
    }
}
