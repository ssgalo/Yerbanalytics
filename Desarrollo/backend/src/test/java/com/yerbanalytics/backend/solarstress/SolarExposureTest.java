package com.yerbanalytics.backend.solarstress;

import com.yerbanalytics.backend.dto.Metric;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.readings.ReadingHistory;
import com.yerbanalytics.backend.readings.ReadingMetric;
import com.yerbanalytics.backend.readings.ReadingWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Exposición solar de las últimas 48 h (F-S1 y F-S3)")
class SolarExposureTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    private ReadingHistory readings;
    private SolarExposure exposure;
    private RuleContext ctx;

    @BeforeEach
    void setUp() {
        readings = mock(ReadingHistory.class);
        exposure = new SolarExposure(readings);
        SectorEntity sector = RuleContextTestFactory.sectorBasico("MZ-1-045");
        ZonaEntity zone = RuleContextTestFactory.zonaBasica("MZ-1");
        sector.setZona(zone);
        List<Metric> metrics = List.of(
                new Metric("uv", "Luz", "%", 66.0, "66", "ok", "#000", null),
                new Metric("temp", "Temperatura", "°C", 29.0, "29", "ok", "#000", null));
        ctx = new RuleContext(sector, zone, List.of(), metrics, RuleContextTestFactory.defaultConfig(), "ok", NOW, null, false);
    }

    @Test
    void laLuzSeMiraEntreLas10YLas16DeLas48HorasYLaTemperaturaEnTodo() {
        when(readings.max(eq("MZ-1"), eq(ReadingMetric.UV), any(ReadingWindow.class))).thenReturn(Optional.of(88.0));
        when(readings.max(eq("MZ-1"), eq(ReadingMetric.TEMP), any(ReadingWindow.class))).thenReturn(Optional.of(31.0));

        SolarExposure.Peaks peaks = exposure.peaks(ctx, false);

        assertThat(peaks.light()).isEqualTo(88.0);
        assertThat(peaks.temp()).isEqualTo(31.0);
        ArgumentCaptor<ReadingWindow> light = ArgumentCaptor.forClass(ReadingWindow.class);
        verify(readings).max(eq("MZ-1"), eq(ReadingMetric.UV), light.capture());
        assertThat(light.getValue().from()).isEqualTo(NOW.minus(Duration.ofHours(48)));
        assertThat(light.getValue().band().desde()).isEqualTo(LocalTime.of(10, 0));
        assertThat(light.getValue().band().hasta()).isEqualTo(LocalTime.of(16, 0));
        ArgumentCaptor<ReadingWindow> temp = ArgumentCaptor.forClass(ReadingWindow.class);
        verify(readings).max(eq("MZ-1"), eq(ReadingMetric.TEMP), temp.capture());
        assertThat(temp.getValue().from()).isEqualTo(NOW.minus(Duration.ofHours(48)));
        assertThat(temp.getValue().band()).as("la temperatura cuenta a cualquier hora").isNull();
    }

    @Test
    void sinLecturasEnLaVentanaNoHayPico() {
        when(readings.max(any(), any(), any(ReadingWindow.class))).thenReturn(Optional.empty());

        SolarExposure.Peaks peaks = exposure.peaks(ctx, false);

        assertThat(peaks.light()).isNull();
        assertThat(peaks.temp()).isNull();
    }

    @Test
    void conElBypassDeMuestrasUsaLaUltimaLecturaYNoConsultaElHistorial() {
        SolarExposure.Peaks peaks = exposure.peaks(ctx, true);

        assertThat(peaks.light()).isEqualTo(66.0);
        assertThat(peaks.temp()).isEqualTo(29.0);
        verifyNoInteractions(readings);
    }

    @Test
    void conElBypassUnaMetricaDescartadaPorS03NoCuenta() {
        RuleContext discarded = new RuleContext(ctx.sector(), ctx.zona(), List.of(), ctx.metrics(), ctx.config(), "ok", NOW,
                null, false, com.yerbanalytics.backend.engine.ContextoRiego.vacio(),
                com.yerbanalytics.backend.engine.overrides.RuleOverrides.NONE, java.util.Set.of("uv"));

        SolarExposure.Peaks peaks = exposure.peaks(discarded, true);

        assertThat(peaks.light()).isNull();
        assertThat(peaks.temp()).isEqualTo(29.0);
    }
}
