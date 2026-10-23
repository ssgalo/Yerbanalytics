package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.rules.FollowUpRule;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
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

/**
 * Tests unitarios de {@link FollowUpRule} (tarea 4.7 / 5.2). Los de la mediasombra, que es de la macro-zona, están en
 * {@code mediasombra/ShadingRuleTest}.
 */
@DisplayName("FollowUpRule")
@ExtendWith(MockitoExtension.class)
class FollowUpRuleTest {

    @Mock
    private HistorialService historialService;

    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        sector = RuleContextTestFactory.sectorBasico("MZ-4-001");
        zona = RuleContextTestFactory.zonaBasica("MZ-4");
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
    @DisplayName("FollowUpRule: prioridad es 21 (la última)")
    void seguimientoRule_prioridad21() {
        FollowUpRule rule = new FollowUpRule(historialService);
        assertThat(rule.priority()).isEqualTo(21);
    }
}
