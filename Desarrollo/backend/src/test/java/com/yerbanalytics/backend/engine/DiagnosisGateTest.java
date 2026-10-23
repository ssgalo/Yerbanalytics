package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Compuerta del diagnóstico de IA (P-D1) de las reglas por diagnóstico")
class DiagnosisGateTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    /** Una regla mínima que sólo declara los parámetros de la compuerta. */
    private static final Rule RULE = new Rule() {
        @Override public int priority() { return 99; }
        @Override public String name() { return "GateTestRule"; }
        @Override public List<DefinicionParametro> parametros() { return DiagnosisGate.PARAMETERS; }
        @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) { return List.of(); }
    };

    private static RuleContext ctx(String estado, Double conf, long hoursAgo) {
        SectorEntity s = RuleContextTestFactory.sectorBasico("MZ-1-045");
        ZonaEntity z = RuleContextTestFactory.zonaBasica("MZ-1");
        s.setZona(z);
        s.setDiagnosisEstado(estado);
        s.setDiagnosisConf(conf);
        s.setDiagnosisTs(NOW.minusSeconds(hoursAgo * 3600).toEpochMilli());
        return new RuleContext(s, z, List.of(), List.of(), RuleContextTestFactory.defaultConfig(), "ok", NOW, null, false);
    }

    private static Optional<RuleAction> check(RuleContext ctx, Evaluacion ev) {
        return DiagnosisGate.check(ctx, ev, RULE.name(), "clorosis", "Clorosis"::equals);
    }

    @Test
    void unDiagnosticoDelEstadoVigenteYConfiableDejaPasar() {
        Evaluacion ev = ReglaTestSupport.ev(RULE);

        assertThat(check(ctx("Clorosis", 88.0, 2), ev)).isEmpty();
        assertThat(ev.comparaciones()).singleElement().satisfies(c -> {
            assertThat(c.etiqueta()).contains("P-D1");
            assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        });
    }

    @Test
    void otroEstadoNoEsDeLaReglaYNoSeMiraLaVigencia() {
        Evaluacion ev = ReglaTestSupport.ev(RULE);

        Optional<RuleAction> noop = check(ctx("Daño fúngico", 90.0, 2), ev);

        assertThat(noop).hasValueSatisfying(a -> {
            assertThat(a.type()).isEqualTo(ActionType.NOOP_INFO);
            assertThat(a.ruleName()).isEqualTo("GateTestRule");
            assertThat(a.motivo()).contains("MZ-1-045").contains("Daño fúngico").contains("clorosis");
        });
        assertThat(ev.comparaciones()).isEmpty();
    }

    @Test
    void unaConfianzaInsuficienteNoDejaPasarYDiceCual() {
        Optional<RuleAction> noop = check(ctx("Clorosis", 60.0, 2), ReglaTestSupport.ev(RULE));

        assertThat(noop).hasValueSatisfying(a -> assertThat(a.motivo()).contains("confianza").contains("70"));
    }

    @Test
    void unDiagnosticoVencidoNoDejaPasar() {
        Optional<RuleAction> noop = check(ctx("Clorosis", 90.0, 60), ReglaTestSupport.ev(RULE));

        assertThat(noop).hasValueSatisfying(a -> assertThat(a.motivo()).contains("60 h"));
    }

    @Test
    void unSectorSinDiagnosticoNoDejaPasar() {
        RuleContext ctx = ctx("Sano", null, 0);
        ctx.sector().setDiagnosisTs(null);

        assertThat(check(ctx, ReglaTestSupport.ev(RULE))).isPresent();
    }

    @Test
    void declaraLaConfianzaYLaVigenciaDeP_D1() {
        assertThat(DiagnosisGate.PARAMETERS).extracting(DefinicionParametro::clave)
                .containsExactly("diagnostico.confianza-minima", "diagnostico.vigencia-horas");
    }
}
