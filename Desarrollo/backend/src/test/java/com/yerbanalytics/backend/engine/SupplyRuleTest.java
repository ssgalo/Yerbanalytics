package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosDiagnostico;
import com.yerbanalytics.backend.engine.rules.SupplyRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;

/** {@link SupplyRule}: misma decisión que antes de migrarla al catálogo, ahora con su traza. */
@DisplayName("SupplyRule")
class SupplyRuleTest {

    private SupplyRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new SupplyRule();
        sector = RuleContextTestFactory.sectorBasico("MZ-1-003");
        sector.setDiagnosisEstado("Clorosis");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    private RuleContext con(String estado, Double conf) {
        sector.setDiagnosisConf(conf);
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                estado, Instant.now(), null, false);
    }

    @Test
    void sectorQueNoEstaCritico_noDosifica() {
        List<RuleAction> acciones = evaluar(rule, con("warning", 99.0));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("warning");
    }

    @Test
    void criticoSinConfianza_noDosifica() {
        assertThat(evaluar(rule, con("critical", null))).extracting(RuleAction::type)
                .containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    void criticoConConfianzaIgualAlUmbral_dosifica() {
        List<RuleAction> acciones = evaluar(rule, con("critical", 85.0));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ACTIVAR_BOMBA);
        assertThat(acciones.get(0).motivo()).contains("Clorosis").contains("85");
    }

    @Test
    void criticoConConfianzaBajoElUmbral_noDosifica() {
        List<RuleAction> acciones = evaluar(rule, con("critical", 84.9));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
        assertThat(acciones.get(0).motivo()).contains("No se dosifica");
    }

    @Test
    void declaraLaConfianzaMinimaDelDiagnostico() {
        assertThat(rule.parametros()).containsExactly(ParametrosDiagnostico.CONFIANZA_MINIMA);
    }

    @Test
    void laTrazaMarcaElEstadoComoCondicionFijaYLaConfianzaComoConfigurable() {
        Evaluacion ev = ev(rule);

        rule.evaluate(con("critical", 90.0), ev);

        assertThat(ev.comparaciones()).hasSize(2);
        Comparacion estado = ev.comparaciones().get(0);
        assertThat(estado.configurable()).isFalse();
        assertThat(estado.clave()).isNull();
        assertThat(estado.recibido()).isEqualTo("critical");
        assertThat(estado.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        Comparacion conf = ev.comparaciones().get(1);
        assertThat(conf.configurable()).isTrue();
        assertThat(conf.operador()).isEqualTo(Operador.GE);
        assertThat(conf.umbral()).isEqualTo(85.0);
        assertThat(conf.clave()).isEqualTo("diagnostico.confianza-minima");
    }

    @Test
    void sectorNoCriticoNoLlegaAComparar​LaConfianza() {
        Evaluacion ev = ev(rule);

        rule.evaluate(con("ok", 99.0), ev);

        assertThat(ev.comparaciones()).hasSize(1);
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    void sinConfianzaLaTrazaQuedaSinDato() {
        Evaluacion ev = ev(rule);

        rule.evaluate(con("critical", null), ev);

        assertThat(ev.comparaciones().get(1).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    void laConfianzaMinimaSaleDelCatalogo() {
        // Con el mínimo en 95, una confianza de 90 ya no alcanza.
        List<RuleAction> acciones = rule.evaluate(con("critical", 90.0),
                ev(rule, java.util.Map.of(ParametrosDiagnostico.CONFIANZA_MINIMA, "95")));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }
}
