package com.yerbanalytics.backend.biotic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Capturas diarias seguidas con el mismo diagnóstico (D-3)")
class ConsecutiveCapturesTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Predicate<ConsecutiveCaptures.Capture> FUNGAL = c -> BioticKind.FUNGAL.matches(c.estado());

    /** Una captura {@code daysAgo} días antes del 22/10 a las {@code hour} h (hora del vivero). */
    private static ConsecutiveCaptures.Capture capture(String estado, int daysAgo, int hour) {
        Instant ts = Instant.parse("2026-10-22T00:00:00-03:00").minusSeconds(daysAgo * 86_400L).plusSeconds(hour * 3600L);
        return new ConsecutiveCaptures.Capture(estado, 85.0, ts.toEpochMilli());
    }

    private static int count(ConsecutiveCaptures.Capture... newestFirst) {
        return ConsecutiveCaptures.count(List.of(newestFirst), FUNGAL, ZONE);
    }

    @Test
    void tresDiasSeguidosCuentanTres() {
        assertThat(count(capture("Daño fúngico", 0, 10), capture("Daño fúngico", 1, 10), capture("Daño fúngico", 2, 10)))
                .isEqualTo(3);
    }

    @Test
    void unaPasadaFaltanteCortaLaRacha() {
        // Hoy y anteayer: ayer no hubo captura.
        assertThat(count(capture("Daño fúngico", 0, 10), capture("Daño fúngico", 2, 10), capture("Daño fúngico", 3, 10)))
                .isEqualTo(1);
    }

    @Test
    void variasCapturasDelMismoDiaCuentanComoUnaSola() {
        assertThat(count(capture("Daño fúngico", 0, 16), capture("Daño fúngico", 0, 9), capture("Daño fúngico", 1, 10)))
                .isEqualTo(2);
    }

    @Test
    void otroDiagnosticoEnUnDiaCortaLaRacha() {
        assertThat(count(capture("Daño fúngico", 0, 10), capture("Sano", 1, 10), capture("Daño fúngico", 2, 10)))
                .isEqualTo(1);
    }

    @Test
    void siLaUltimaCapturaDelDiaNoEsDeEseDiagnosticoElDiaNoCuenta() {
        // Ese día hubo dos capturas: la última (la que vale) ya es Sano.
        assertThat(count(capture("Daño fúngico", 0, 10), capture("Sano", 1, 16), capture("Daño fúngico", 1, 9)))
                .isEqualTo(1);
    }

    @Test
    void sinCapturasONingunaDelDiagnosticoCuentaCero() {
        assertThat(ConsecutiveCaptures.count(List.of(), FUNGAL, ZONE)).isZero();
        assertThat(count(capture("Sano", 0, 10), capture("Sano", 1, 10))).isZero();
    }

    @Test
    void elFiltroPuedeExigirConfianza() {
        ConsecutiveCaptures.Capture lowConfidence = new ConsecutiveCaptures.Capture("Daño fúngico", 40.0,
                capture("Daño fúngico", 1, 10).ts());
        Predicate<ConsecutiveCaptures.Capture> confident = FUNGAL.and(c -> c.conf() != null && c.conf() >= 70);

        assertThat(ConsecutiveCaptures.count(List.of(capture("Daño fúngico", 0, 10), lowConfidence), confident, ZONE))
                .isEqualTo(1);
    }

    @Test
    void elDiaSeCortaEnLaMedianocheDelVivero() {
        // 23:30 de ayer y 00:30 de hoy (hora del vivero) son días distintos aunque estén a una hora de distancia.
        assertThat(count(capture("Daño fúngico", 0, 0), capture("Daño fúngico", 1, 23))).isEqualTo(2);
    }
}
