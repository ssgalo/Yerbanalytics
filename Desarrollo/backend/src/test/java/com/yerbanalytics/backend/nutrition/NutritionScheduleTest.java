package com.yerbanalytics.backend.nutrition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NutritionSchedule · cuándo vence el plan")
class NutritionScheduleTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Duration SEVEN_DAYS = Duration.ofDays(7);

    @Test
    void venceCuandoPasoElIntervaloDesdeLaUltimaAplicacion() {
        assertThat(NutritionSchedule.isDue(NOW, NOW.minus(Duration.ofDays(7)), null, SEVEN_DAYS)).isTrue();
        assertThat(NutritionSchedule.isDue(NOW, NOW.minus(Duration.ofDays(8)), null, SEVEN_DAYS)).isTrue();
    }

    @Test
    void conCincoDiasDeSieteTodaviaNo() {
        assertThat(NutritionSchedule.isDue(NOW, NOW.minus(Duration.ofDays(5)), null, SEVEN_DAYS)).isFalse();
    }

    @Test
    void unaZonaSinHistorialSeConsideraVencida() {
        assertThat(NutritionSchedule.isDue(NOW, null, null, SEVEN_DAYS)).isTrue();
        assertThat(NutritionSchedule.nextDue(null, null, SEVEN_DAYS)).isEmpty();
    }

    @Test
    void elAdelantamientoLaVuelveVencidaAntesDelIntervalo() {
        Instant last = NOW.minus(Duration.ofDays(6));

        assertThat(NutritionSchedule.isDue(NOW, last, NOW, Duration.ofDays(10))).isTrue();
        assertThat(NutritionSchedule.nextDue(last, NOW, Duration.ofDays(10))).contains(NOW);
    }

    @Test
    void unAdelantamientoPosteriorAlIntervaloNoLoPostergaYElProximoVencimientoEsElMasTemprano() {
        Instant last = NOW.minus(Duration.ofDays(6));
        Instant byInterval = last.plus(SEVEN_DAYS);

        Optional<Instant> next = NutritionSchedule.nextDue(last, NOW.plus(Duration.ofDays(30)), SEVEN_DAYS);

        assertThat(next).contains(byInterval);
    }

    @Test
    void sinAdelantamientoElProximoVencimientoEsLaUltimaMasElIntervalo() {
        Instant last = NOW.minus(Duration.ofDays(2));

        assertThat(NutritionSchedule.nextDue(last, null, SEVEN_DAYS)).contains(last.plus(SEVEN_DAYS));
    }

    @Test
    void conElIntervaloEnCeroVenceYa() {
        assertThat(NutritionSchedule.isDue(NOW, NOW, null, Duration.ZERO)).isTrue();
    }
}
