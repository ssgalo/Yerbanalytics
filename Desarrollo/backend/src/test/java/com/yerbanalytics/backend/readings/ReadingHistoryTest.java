package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import com.yerbanalytics.backend.model.ZoneReadingEntity;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Consultas del historial contra una "base" en memoria que respeta el contrato del repositorio (por zona, por rango,
 * paginado y ordenado), así se prueba la lógica y no el motor SQL.
 */
@DisplayName("ReadingHistory")
class ReadingHistoryTest {

    private static final String ZONE = "MZ-1";
    /** Medianoche local del 2026-10-14 como referencia. */
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 14, 0, 0);

    private final List<ZoneReadingEntity> db = new ArrayList<>();
    private ReadingHistory history;

    @BeforeEach
    void setUp() {
        ZoneReadingRepository repo = mock(ZoneReadingRepository.class);
        when(repo.findByZoneIdAndTsBetweenOrderByTsAsc(anyString(), anyLong(), anyLong())).thenAnswer(inv -> {
            String zone = inv.getArgument(0);
            long from = inv.getArgument(1);
            long to = inv.getArgument(2);
            return db.stream()
                    .filter(r -> r.getZoneId().equals(zone) && r.getTs() >= from && r.getTs() <= to)
                    .sorted(Comparator.comparing(ZoneReadingEntity::getTs)).toList();
        });
        when(repo.findByZoneIdOrderByTsDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> {
            Pageable p = inv.getArgument(1);
            return db.stream().filter(r -> r.getZoneId().equals(inv.getArgument(0)))
                    .sorted(Comparator.comparing(ZoneReadingEntity::getTs).reversed())
                    .limit(p.getPageSize()).toList();
        });
        when(repo.findByZoneIdAndOriginOrderByTsDesc(anyString(), any(ReadingOrigin.class), any(Pageable.class)))
                .thenAnswer(inv -> {
                    Pageable p = inv.getArgument(2);
                    return db.stream()
                            .filter(r -> r.getZoneId().equals(inv.getArgument(0)) && r.getOrigin() == inv.getArgument(1))
                            .sorted(Comparator.comparing(ZoneReadingEntity::getTs).reversed())
                            .limit(p.getPageSize()).toList();
                });
        history = new ReadingHistory(repo);
    }

    private static Instant local(int day, int hour, int min) {
        return DAY.plusDays(day).plusHours(hour).plusMinutes(min).atZone(ZonaHorariaVivero.ZONA).toInstant();
    }

    private ZoneReadingEntity add(String zone, Instant ts, ReadingOrigin origin, Double ce, Double uv) {
        ZoneReadingEntity r = new ZoneReadingEntity();
        r.setZoneId(zone);
        r.setTs(ts.toEpochMilli());
        r.setReceivedTs(ts.toEpochMilli());
        r.setOrigin(origin);
        r.setCe(ce);
        r.setUv(uv);
        db.add(r);
        return r;
    }

    // ----------------------------------------------------------------- latest

    @Test
    @DisplayName("latest(n) devuelve las N más nuevas en orden cronológico")
    void latest() {
        for (int h = 1; h <= 5; h++) {
            add(ZONE, local(0, h, 0), ReadingOrigin.PULSE, (double) h, null);
        }
        add("MZ-2", local(0, 9, 0), ReadingOrigin.PULSE, 99.0, null);

        assertThat(history.latest(ZONE, 3, false)).extracting(ZoneReadingEntity::getCe)
                .containsExactly(3.0, 4.0, 5.0);
    }

    @Test
    @DisplayName("latest con onlyPulse ignora las lecturas de otro origen")
    void latestOnlyPulse() {
        add(ZONE, local(0, 1, 0), ReadingOrigin.PULSE, 1.0, null);
        add(ZONE, local(0, 2, 0), ReadingOrigin.OTHER, 2.0, null);
        add(ZONE, local(0, 3, 0), ReadingOrigin.PULSE, 3.0, null);

        assertThat(history.latest(ZONE, 5, true)).extracting(ZoneReadingEntity::getCe)
                .containsExactly(1.0, 3.0);
    }

    @Test
    @DisplayName("latest sin lecturas devuelve una lista vacía")
    void latestEmpty() {
        assertThat(history.latest(ZONE, 3, false)).isEmpty();
    }

    // ----------------------------------------------------------------- agregados

    @Test
    @DisplayName("media de 6 lecturas de pulso: una lectura de verificación en el medio no cuenta")
    void averageOnlyPulse() {
        double[] ce = {0.6, 0.8, 1.0, 1.2, 1.4, 1.6};
        for (int i = 0; i < ce.length; i++) {
            add(ZONE, local(0, 2 + 4 * i, 0), ReadingOrigin.PULSE, ce[i], null);
        }
        add(ZONE, local(0, 11, 0), ReadingOrigin.OTHER, 9.0, null);

        var window = ReadingWindow.since(local(0, 0, 0)).onlyPulse();

        assertThat(history.average(ZONE, ReadingMetric.CE, window)).hasValueSatisfying(
                m -> assertThat(m).isCloseTo(1.1, org.assertj.core.data.Offset.offset(1e-9)));
        assertThat(history.count(ZONE, ReadingMetric.CE, window)).isEqualTo(6);
        assertThat(history.average(ZONE, ReadingMetric.CE, ReadingWindow.since(local(0, 0, 0))))
                .as("sin onlyPulse la de verificación sí cuenta")
                .hasValueSatisfying(m -> assertThat(m).isGreaterThan(2.0));
    }

    @Test
    @DisplayName("máximo y mínimo de una ventana temporal")
    void maxAndMin() {
        add(ZONE, local(0, 10, 0), ReadingOrigin.PULSE, null, 70.0);
        add(ZONE, local(0, 14, 0), ReadingOrigin.PULSE, null, 88.0);
        add(ZONE, local(1, 10, 0), ReadingOrigin.PULSE, null, 62.0);

        var window = ReadingWindow.since(local(0, 0, 0));

        assertThat(history.max(ZONE, ReadingMetric.UV, window)).contains(88.0);
        assertThat(history.min(ZONE, ReadingMetric.UV, window)).contains(62.0);
    }

    private void addDpv(Instant ts, ReadingOrigin origin, Double dpv) {
        add(ZONE, ts, origin, null, null).setDpv(dpv);
    }

    @Test
    @DisplayName("máximo del DPV en 72 h: el mayor de las lecturas de la ventana")
    void maxDpvInThreeDays() {
        addDpv(local(0, 6, 0), ReadingOrigin.PULSE, 0.4);
        addDpv(local(1, 14, 0), ReadingOrigin.PULSE, 2.3);
        addDpv(local(2, 14, 0), ReadingOrigin.OTHER, 1.1);
        addDpv(local(-5, 14, 0), ReadingOrigin.PULSE, 4.0);

        assertThat(history.max(ZONE, ReadingMetric.DPV, ReadingWindow.since(local(0, 0, 0)))).contains(2.3);
    }

    @Test
    @DisplayName("las dos últimas lecturas de pulso con DPV bajo 0,3 vienen en orden cronológico")
    void lastTwoDpvReadings() {
        addDpv(local(0, 2, 0), ReadingOrigin.PULSE, 0.9);
        addDpv(local(0, 6, 0), ReadingOrigin.PULSE, 0.21);
        addDpv(local(0, 10, 0), ReadingOrigin.PULSE, 0.25);

        assertThat(history.latest(ZONE, 2, true)).extracting(r -> ReadingMetric.DPV.valueIn(r))
                .containsExactly(0.21, 0.25);
    }

    @Test
    @DisplayName("una lectura sin DPV (la zona nunca tuvo T o HR) no cuenta, ni es cero")
    void readingsWithoutDpvDoNotCount() {
        addDpv(local(0, 6, 0), ReadingOrigin.PULSE, null);

        var window = ReadingWindow.since(local(0, 0, 0));
        assertThat(history.count(ZONE, ReadingMetric.DPV, window)).isZero();
        assertThat(history.max(ZONE, ReadingMetric.DPV, window)).isEmpty();
    }

    @Test
    @DisplayName("la ventana respeta from y to; sin to llega hasta lo último")
    void windowWithEnd() {
        add(ZONE, local(0, 10, 0), ReadingOrigin.PULSE, null, 70.0);
        add(ZONE, local(1, 10, 0), ReadingOrigin.PULSE, null, 95.0);

        assertThat(history.max(ZONE, ReadingMetric.UV,
                ReadingWindow.between(local(0, 0, 0), local(0, 23, 0)))).contains(70.0);
        assertThat(history.max(ZONE, ReadingMetric.UV, ReadingWindow.since(local(0, 0, 0)))).contains(95.0);
    }

    @Test
    @DisplayName("la franja horaria se evalúa en hora local del vivero: luz máxima entre las 10 y las 16 h")
    void localTimeBand() {
        add(ZONE, local(0, 6, 0), ReadingOrigin.PULSE, null, 99.0);    // de madrugada: fuera
        add(ZONE, local(0, 10, 0), ReadingOrigin.PULSE, null, 80.0);
        add(ZONE, local(0, 14, 0), ReadingOrigin.PULSE, null, 88.0);
        add(ZONE, local(0, 18, 0), ReadingOrigin.PULSE, null, 97.0);   // de tarde: fuera

        var window = ReadingWindow.since(local(0, 0, 0)).withBand(VentanaHoraria.parse("10:00-16:00"));

        assertThat(history.max(ZONE, ReadingMetric.UV, window)).contains(88.0);
        assertThat(history.count(ZONE, ReadingMetric.UV, window)).isEqualTo(2);
    }

    @Test
    @DisplayName("la lectura de la hora de fin de la franja todavía entra, la del minuto siguiente no")
    void bandEdge() {
        add(ZONE, local(0, 16, 0), ReadingOrigin.PULSE, null, 90.0);
        add(ZONE, local(0, 16, 1), ReadingOrigin.PULSE, null, 95.0);

        var window = ReadingWindow.since(local(0, 0, 0)).withBand(VentanaHoraria.parse("10:00-16:00"));

        assertThat(history.max(ZONE, ReadingMetric.UV, window)).contains(90.0);
    }

    // ----------------------------------------------------------------- sin dato

    @Test
    @DisplayName("sin lecturas en la ventana: sin dato, nunca cero")
    void noReadings() {
        var window = ReadingWindow.since(local(0, 0, 0));

        assertThat(history.max(ZONE, ReadingMetric.CE, window)).isEmpty();
        assertThat(history.min(ZONE, ReadingMetric.CE, window)).isEmpty();
        assertThat(history.average(ZONE, ReadingMetric.CE, window)).isEmpty();
        assertThat(history.count(ZONE, ReadingMetric.CE, window)).isZero();
    }

    @Test
    @DisplayName("una lectura sin esa métrica (nula) no cuenta ni baja la media")
    void nullMetricDoesNotCount() {
        add(ZONE, local(0, 2, 0), ReadingOrigin.PULSE, 1.0, null);
        add(ZONE, local(0, 6, 0), ReadingOrigin.PULSE, null, 50.0);
        add(ZONE, local(0, 10, 0), ReadingOrigin.PULSE, 3.0, null);

        var window = ReadingWindow.since(local(0, 0, 0));

        assertThat(history.average(ZONE, ReadingMetric.CE, window)).contains(2.0);
        assertThat(history.count(ZONE, ReadingMetric.CE, window)).isEqualTo(2);
    }

    @Test
    @DisplayName("pocas lecturas: el conteo permite decidir sin dato (4 de las 6 exigidas)")
    void fewReadings() {
        for (int i = 0; i < 4; i++) {
            add(ZONE, local(0, 2 + 4 * i, 0), ReadingOrigin.PULSE, 1.0, null);
        }

        assertThat(history.count(ZONE, ReadingMetric.CE, ReadingWindow.since(local(0, 0, 0)).onlyPulse()))
                .isLessThan(6);
    }

    @Test
    @DisplayName("no mezcla zonas")
    void otherZone() {
        add("MZ-2", local(0, 10, 0), ReadingOrigin.PULSE, 5.0, null);

        assertThat(history.average(ZONE, ReadingMetric.CE, ReadingWindow.since(local(0, 0, 0)))).isEmpty();
    }
}
