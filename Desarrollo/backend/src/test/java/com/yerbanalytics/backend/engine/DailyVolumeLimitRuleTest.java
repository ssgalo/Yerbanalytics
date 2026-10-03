package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.rules.DailyVolumeLimitRule;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** {@link DailyVolumeLimitRule}: misma decisión que antes de migrarla al catálogo, ahora con su traza. */
@DisplayName("DailyVolumeLimitRule")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DailyVolumeLimitRuleTest {

    @Mock
    private HistorialRepository historialRepository;

    private DailyVolumeLimitRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new DailyVolumeLimitRule(historialRepository);
        sector = RuleContextTestFactory.sectorBasico("MZ-1-009");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    private void riegosEn24h(long n) {
        when(historialRepository.countByTipoAndSectorAndPeriod(eq("MZ-1-009"), eq("Riego"), anyLong())).thenReturn(n);
    }

    @Test
    void sinConfiguracionOperativa_noVerificaElLimite() {
        RuleContext ctx = new RuleContext(sector, zona, List.of(), List.of(), null, "ok", Instant.now(), null, false);
        riegosEn24h(5);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("no verificado");
    }

    @Test
    void conMenosDeDosRiegosEn24h_noCorta() {
        for (long n : new long[]{0, 1}) {
            riegosEn24h(n);
            assertThat(evaluar(rule, RuleContextTestFactory.basico(sector, zona))).extracting(RuleAction::type)
                    .containsExactly(ActionType.NOOP_INFO);
        }
    }

    @Test
    void conDosOMasRiegosEn24h_abortaElRiego() {
        for (long n : new long[]{2, 3}) {
            riegosEn24h(n);
            List<RuleAction> acciones = evaluar(rule, RuleContextTestFactory.basico(sector, zona));
            assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
            assertThat(acciones.get(0).motivo()).contains("MZ-1-009").contains(String.valueOf(n));
        }
    }

    @Test
    void declaraElMaximoDeRiegosEn24h() {
        assertThat(rule.parametros()).containsExactly(ParametrosRiego.MAX_RIEGOS_24H);
    }

    @Test
    void laTrazaRegistraLosRiegosContraElMaximo() {
        riegosEn24h(2);
        Evaluacion ev = ev(rule);

        rule.evaluate(RuleContextTestFactory.basico(sector, zona), ev);

        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.recibido()).isEqualTo(2.0);
        assertThat(c.operador()).isEqualTo(Operador.GE);
        assertThat(c.umbral()).isEqualTo(2.0);
        assertThat(c.clave()).isEqualTo("riego.max-riegos-24h");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    void elMaximoSaleDelCatalogo() {
        riegosEn24h(2);

        List<RuleAction> acciones = rule.evaluate(RuleContextTestFactory.basico(sector, zona),
                ev(rule, java.util.Map.of(ParametrosRiego.MAX_RIEGOS_24H, "3")));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }
}
