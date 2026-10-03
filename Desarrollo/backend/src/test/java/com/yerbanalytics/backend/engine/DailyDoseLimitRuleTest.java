package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosInsumo;
import com.yerbanalytics.backend.engine.rules.DailyDoseLimitRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de {@link DailyDoseLimitRule} (tarea 4.7).
 */
@DisplayName("DailyDoseLimitRule")
@ExtendWith(MockitoExtension.class)
class DailyDoseLimitRuleTest {

    @Mock
    private HistorialRepository historialRepository;

    private DailyDoseLimitRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new DailyDoseLimitRule(historialRepository);
        sector = RuleContextTestFactory.sectorBasico("MZ-1-010");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    @Test
    @DisplayName("sin dosis en 24h → NOOP_INFO")
    void sinDosisEn24h_emiteNoopInfo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(0L);

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("con 1 dosis en 24h → ABORT_INSUMO")
    void conDosisEn24h_emiteAbortInsumo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(1L);

        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);
        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_INSUMO);
        assertThat(acciones.get(0).motivo()).contains("1").contains("MZ-1-010");
    }

    @Test
    @DisplayName("sin configuración operativa → NOOP_INFO (fail-open)")
    void sinConfig_emiteNoopInfoFailOpen() {
        RuleContext ctx = new RuleContext(
                sector, zona, List.of(), List.of(),
                null,   // config = null
                "ok", java.time.Instant.now(), null, false);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("declara su parámetro y lo registra en la traza")
    void declaraElMaximoDeDosisYLoRegistraEnLaTraza() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(1L);
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.basico(sector, zona), ev);

        assertThat(rule.parametros()).containsExactly(ParametrosInsumo.MAX_DOSIS_24H);
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.recibido()).isEqualTo(1.0);
        assertThat(c.operador()).isEqualTo(Operador.GE);
        assertThat(c.umbral()).isEqualTo(1.0);
        assertThat(c.clave()).isEqualTo("insumo.max-dosis-24h");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("el máximo sale del catálogo")
    void elMaximoSaleDelCatalogo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(1L);

        List<RuleAction> acciones = rule.evaluate(RuleContextTestFactory.basico(sector, zona),
                ev(rule, java.util.Map.of(ParametrosInsumo.MAX_DOSIS_24H, "2")));

        assertThat(acciones.get(0).type()).isEqualTo(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ABORT_INSUMO es una acción bloqueante")
    void abortInsumoEsBloqueante() {
        assertThat(ActionType.ABORT_INSUMO.isBlocking()).isTrue();
    }

    @Test
    @DisplayName("el motivo cita el máximo REAL del catálogo en dosis, no el campo viejo en ml de la configuración operativa")
    void elMotivoCitaElUmbralDelCatalogo() {
        when(historialRepository.countByTipoAndSectorAndPeriod(
                eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(3L);
        // defaultConfig() trae insumoDosisMax24hMl = 50: no debe aparecer en el texto.
        RuleContext ctx = RuleContextTestFactory.basico(sector, zona);

        List<RuleAction> acciones = rule.evaluate(ctx, ev(rule, java.util.Map.of(ParametrosInsumo.MAX_DOSIS_24H, "3")));
        List<RuleAction> deFabrica = rule.evaluate(ctx, ev(rule));

        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_INSUMO);
        assertThat(acciones.get(0).motivo()).contains("máx. configurado: 3 dosis").doesNotContain("ml").doesNotContain("50");
        assertThat(deFabrica.get(0).motivo()).contains("máx. configurado: 1 dosis").doesNotContain(" ml");
    }

    @Test
    @DisplayName("la ventana de 24 h se cuenta desde la hora del contexto (el reloj del vivero), no desde System.currentTimeMillis()")
    void laVentanaSeCuentaDesdeLaHoraDelContexto() {
        java.time.Instant ahora = java.time.Instant.parse("2020-01-01T12:00:00Z");   // lejos del reloj real
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-010"), eq("Insumo"), anyLong())).thenReturn(0L);
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", ahora, null, false);

        evaluar(rule, ctx);

        org.mockito.Mockito.verify(historialRepository).countByTipoAndSectorAndPeriod(
                "MZ-1-010", "Insumo", ahora.toEpochMilli() - 24L * 3_600_000L);
    }
}
