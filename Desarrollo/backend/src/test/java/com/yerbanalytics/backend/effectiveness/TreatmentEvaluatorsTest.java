package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.engine.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("E-02 y E-03: efectividad de los tratamientos por diagnóstico")
class TreatmentEvaluatorsTest {

    private static TreatmentBaseline before(String state, Severity severity, String... neighbors) {
        return new TreatmentBaseline(state, severity, Set.of(neighbors));
    }

    private static DiagnosisView now(String state, Severity severity) {
        return new DiagnosisView(state, severity, true, null);
    }

    @Nested
    @DisplayName("E-02 fertilizante correctivo")
    class CorrectiveFertilizer {

        private final CorrectiveFertilizerEvaluator evaluator = new CorrectiveFertilizerEvaluator();

        @Test
        void siElSectorVuelveASanoEsEfectiva() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.HIGH), now("Sano", null));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        }

        @Test
        void siLaSeveridadBajaUnNivelEsEfectiva() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.HIGH), now("Clorosis", Severity.MODERATE));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        }

        @Test
        void siLaSeveridadSigueIgualNoEsEfectiva() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.HIGH), now("Clorosis", Severity.HIGH));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        }

        @Test
        void siLaSeveridadEmpeoraNoEsEfectiva() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.MILD), now("Clorosis", Severity.HIGH));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        }

        @Test
        void siLaClorosisSeReemplazaPorOtroDanoNoEsEfectiva() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.MODERATE), now("Estrés solar", Severity.MILD));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        }

        @Test
        void sinDiagnosticoVigenteQuedaPendiente() {
            Evaluation result = evaluator.evaluate(before("Clorosis", Severity.HIGH),
                    new DiagnosisView(null, null, false, "el sector no tiene diagnóstico"));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.PENDING);
            assertThat(result.reason()).contains("el sector no tiene diagnóstico");
        }
    }

    @Nested
    @DisplayName("E-03 fitosanitario")
    class Fungicide {

        private final FungicideEvaluator evaluator = new FungicideEvaluator();

        @Test
        void conLaMismaSeveridadYSinVecinosNuevosEsEfectivaAunqueElDanoSigaVisible() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.MODERATE),
                    now("Daño fúngico", Severity.MODERATE), Set.of());

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        }

        @Test
        void siLaSeveridadAumentaNoEsEfectivo() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.MODERATE),
                    now("Daño fúngico", Severity.HIGH), Set.of());

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
        }

        @Test
        void siApareceUnVecinoNuevoConElMismoDiagnosticoSePropagoYNoEsEfectivo() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.MODERATE, "MZ-1-044"),
                    now("Daño fúngico", Severity.MODERATE), Set.of("MZ-1-044", "MZ-1-046"));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.INEFFECTIVE);
            assertThat(result.reason()).contains("MZ-1-046");
        }

        @Test
        void unVecinoQueYaTeniaElDanoNoCuentaComoNuevo() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.MODERATE, "MZ-1-044"),
                    now("Daño fúngico", Severity.MODERATE), Set.of("MZ-1-044"));

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        }

        @Test
        void siElSectorSeCuraEsEfectivo() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.HIGH),
                    now("Sano", null), Set.of());

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.EFFECTIVE);
        }

        @Test
        void sinDiagnosticoVigenteQuedaPendiente() {
            Evaluation result = evaluator.evaluate(before("Daño fúngico", Severity.HIGH),
                    new DiagnosisView(null, null, false, "el diagnóstico tiene 80 h (vigencia 72 h)"), Set.of());

            assertThat(result.outcome()).isEqualTo(Evaluation.Outcome.PENDING);
        }
    }
}
