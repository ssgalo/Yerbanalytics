package com.yerbanalytics.backend.effectiveness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lectura de verificación de E-01: banderín por macro-zona")
class VerificationReadsTest {

    private static final long T0 = 1_000_000L;

    private final VerificationReads reads = new VerificationReads();

    @Test
    void sinPedidoNingunaLecturaEsDeVerificacion() {
        assertThat(reads.consume("MZ-1", T0)).isFalse();
    }

    @Test
    void laPrimeraLecturaTrasElPedidoEsLaDeVerificacionYLaSiguienteNo() {
        reads.expect("MZ-1", T0);

        assertThat(reads.consume("MZ-1", T0 + 5_000)).isTrue();
        assertThat(reads.consume("MZ-1", T0 + 6_000)).isFalse();
    }

    @Test
    void elBanderinEsDeCadaZona() {
        reads.expect("MZ-1", T0);

        assertThat(reads.consume("MZ-2", T0 + 1_000)).isFalse();
        assertThat(reads.consume("MZ-1", T0 + 1_000)).isTrue();
    }

    @Test
    void unPedidoSinRespuestaVenceYUnaLecturaTardiaNoCuentaComoVerificacion() {
        reads.expect("MZ-1", T0);

        assertThat(reads.consume("MZ-1", T0 + VerificationReads.EXPIRY_MS + 1)).isFalse();
    }

    @Test
    void lecturaContestadaSeVeDesdeElServicioHastaQueSeLimpia() {
        reads.expect("MZ-1", T0);
        reads.consume("MZ-1", T0 + 2_000);

        assertThat(reads.answeredAfter("MZ-1")).hasValue(T0);

        reads.clear("MZ-1");

        assertThat(reads.answeredAfter("MZ-1")).isEmpty();
    }

    @Test
    void sinRespuestaTodaviaNoHayLecturaContestada() {
        reads.expect("MZ-1", T0);

        assertThat(reads.answeredAfter("MZ-1")).isEmpty();
    }

    @Test
    void hayQuePedirDeNuevoSiNuncaSePidioOSiElPedidoVencioSinRespuesta() {
        assertThat(reads.needsRequest("MZ-1", T0)).isTrue();

        reads.expect("MZ-1", T0);
        assertThat(reads.needsRequest("MZ-1", T0 + 1_000)).isFalse();
        assertThat(reads.needsRequest("MZ-1", T0 + VerificationReads.RETRY_MS + 1)).isTrue();
    }

    @Test
    void unaRespuestaPendienteDeEvaluarNoSePideDeNuevoAntesDelReintento() {
        reads.expect("MZ-1", T0);
        reads.consume("MZ-1", T0 + 2_000);

        assertThat(reads.needsRequest("MZ-1", T0 + 10_000)).isFalse();
    }
}
