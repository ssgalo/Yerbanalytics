package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.ShadingRule;
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
        ShadingRule rule = new ShadingRule(rustificacionRepository, 7.0, "");
        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ShadingRule: pico UV sobre umbral → MOVER_MEDIASOMBRA protector")
    void picoUV_moverMediasombraProtector() {
        ShadingRule rule = new ShadingRule(rustificacionRepository, 7.0, "");
        WeatherForecast forecast = new WeatherForecast(10.0, 9.5, Instant.now()); // UV > 7
        RuleContext ctx = RuleContextTestFactory.conForecast(sector, zona, forecast);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).contains("[apertura=30]");
    }

    @Test
    @DisplayName("ShadingRule: apertura ya correcta según plan → NOOP_INFO")
    void aperturaYaCorrecta_noopInfo() {
        String hoy = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, 7.0, hoy);
        // sector tiene apertura 50%, plan dice 50%
        sector.setActuadorShade(50);
        when(rustificacionRepository.findAllByOrderByOrdenAsc())
                .thenReturn(List.of(new RustificacionEtapaEntity(1, 1, 365, 50)));

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ShadingRule: apertura diferente al plan → MOVER_MEDIASOMBRA")
    void aperturaDiferenteAlPlan_moverMediasombra() {
        String hoy = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        ShadingRule rule = new ShadingRule(rustificacionRepository, 7.0, hoy);
        sector.setActuadorShade(30); // actual 30%, plan dice 60%
        when(rustificacionRepository.findAllByOrderByOrdenAsc())
                .thenReturn(List.of(new RustificacionEtapaEntity(1, 1, 365, 60)));

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.MOVER_MEDIASOMBRA);
        assertThat(acciones.get(0).motivo()).contains("[apertura=60]");
    }

    // ===== FollowUpRule =====

    @Test
    @DisplayName("FollowUpRule: siempre emite NOOP_INFO (tarea interna)")
    void seguimientoRule_emiteNoopInfo() {
        FollowUpRule rule = new FollowUpRule(historialService);
        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);

        List<RuleAction> acciones = rule.evaluate(ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("FollowUpRule: prioridad es 20 (la última)")
    void seguimientoRule_prioridad20() {
        FollowUpRule rule = new FollowUpRule(historialService);
        assertThat(rule.priority()).isEqualTo(20);
    }
}
