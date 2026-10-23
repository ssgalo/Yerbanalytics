package com.yerbanalytics.backend.effectiveness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E-01: efectividad del riego (delta de humedad con signo)")
class IrrigationEvaluatorTest {

    private static final double MIN_DELTA = 8.0;

    private final IrrigationEvaluator evaluator = new IrrigationEvaluator();

    @Test
    void unaSubidaDe19PuntosEsEfectiva() {
        Evaluation result = evaluator.evaluate(44, 63, MIN_DELTA);

        assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        assertThat(result.now()).isEqualTo("63");
        assertThat(result.delta()).isEqualTo("+19");
    }

    @Test
    void unaSubidaDe4PuntosNoAlcanza() {
        Evaluation result = evaluator.evaluate(44, 48, MIN_DELTA);

        assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        assertThat(result.delta()).isEqualTo("+4");
    }

    @Test
    void siLaHumedadBajaNoEsEfectivaAunqueElValorAbsolutoSuperaElUmbral() {
        Evaluation result = evaluator.evaluate(44, 30, MIN_DELTA);

        assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        assertThat(result.delta()).isEqualTo("-14");
    }

    @Test
    void elUmbralExactoCuentaComoEfectivo() {
        assertThat(evaluator.evaluate(40, 48, MIN_DELTA).outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
    }

    @Test
    void elMotivoExplicaLaComparacion() {
        assertThat(evaluator.evaluate(44, 48, MIN_DELTA).reason())
                .contains("44").contains("48").contains("8");
    }
}
