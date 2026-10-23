package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.ResultadoEvaluacion;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.engine.traza.EstadoRegla;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El interruptor operativo de punta a punta en modo {@code REAL} (sin capa de demo): apagar una regla la saca del pulso
 * siguiente y el estado sobrevive a un reinicio, porque vive en {@code regla_estado} y no en memoria.
 */
@SpringBootTest
@TestPropertySource(properties = {"yerbanalytics.mode=REAL", "yerbanalytics.demo.enabled=false"})
@DisplayName("Estado operativo de las reglas · modo REAL")
class OperationalRuleStatesIntegrationTest {

    private static final String RULE = "RiegoPorDeficitRule";

    @Autowired
    private RuleOrchestrator orchestrator;
    @Autowired
    private OperationalRuleStates states;
    @Autowired
    private RuleOverrides overrides;
    @Autowired
    private RuleStateRepository repository;

    @AfterEach
    void limpiar() {
        repository.deleteAll();
        states.reload();
    }

    private EstadoRegla estadoDeLaRegla() {
        RuleContext ctx = RuleContextTestFactory.basico(
                RuleContextTestFactory.sectorBasico("MZ-2-006"), RuleContextTestFactory.zonaBasica("MZ-2"));
        ResultadoEvaluacion r = orchestrator.evaluate(ctx, OrigenEvaluacion.TELEMETRIA);
        return r.traza().reglas().stream().filter(t -> t.ruleId().equals(RULE)).map(TrazaRegla::estado)
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("apagar una regla la deja OMITIDA_DESHABILITADA en el pulso siguiente")
    void apagarLaOmiteDelPulso() {
        assertThat(estadoDeLaRegla()).isNotEqualTo(EstadoRegla.OMITIDA_DESHABILITADA);

        states.set(RULE, false, "sonda en recalibración", "Ana");

        assertThat(overrides.isEnabled(RULE)).isFalse();
        assertThat(estadoDeLaRegla()).isEqualTo(EstadoRegla.OMITIDA_DESHABILITADA);
    }

    @Test
    @DisplayName("un reinicio (otra instancia sobre la misma base) la sigue viendo apagada, con quién y por qué")
    void sobreviveAUnReinicio() {
        states.set(RULE, false, "sonda en recalibración", "Ana");

        OperationalRuleStates trasElReinicio = new OperationalRuleStates(repository, List.of("SupplyRule"));

        assertThat(trasElReinicio.isActive(RULE)).isFalse();
        assertThat(trasElReinicio.state(RULE)).hasValueSatisfying(e -> {
            assertThat(e.updatedBy()).isEqualTo("Ana");
            assertThat(e.reason()).isEqualTo("sonda en recalibración");
        });
    }

    @Test
    @DisplayName("sin modo demo no hay capa de demo: lo retirado sigue apagado por la siembra")
    void laSiembraRigeSinDemo() {
        assertThat(overrides.isEnabled("SupplyRule")).isFalse();
        assertThat(overrides.isEnabled(RULE)).isTrue();
    }
}
