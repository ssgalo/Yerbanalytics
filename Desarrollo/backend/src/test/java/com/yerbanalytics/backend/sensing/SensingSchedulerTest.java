package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher.Resultado;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SensingScheduler")
class SensingSchedulerTest {

    private static final Instant T = Instant.parse("2026-01-01T12:00:00Z");

    private ZonaRepository zonas;
    private ComandoZonaPublisher publisher;
    private ConfiguracionService configuracion;
    private TaskScheduler taskScheduler;
    private SensingScheduler scheduler;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        publisher = mock(ComandoZonaPublisher.class);
        configuracion = mock(ConfiguracionService.class);
        taskScheduler = mock(TaskScheduler.class);
        when(publisher.leerAhora(anyString())).thenReturn(new Resultado(true, "c", null));
        pullInterval(60);
        scheduler = new SensingScheduler(zonas, publisher, configuracion, taskScheduler,
                Clock.fixed(T, ZoneOffset.UTC));
    }

    private void pullInterval(Integer seconds) {
        ConfiguracionOperativaEntity op = new ConfiguracionOperativaEntity();
        op.setSensingPullIntervalSeconds(seconds);
        when(configuracion.getConfiguracionOperativa()).thenReturn(op);
    }

    private Instant next(Instant lastCompletion) {
        return scheduler.nextExecution(new SimpleTriggerContext(null, null, lastCompletion));
    }

    // --- pullAll ---

    @Test
    void publishesOneLeerAhoraPerZone() {
        when(zonas.findAllIds()).thenReturn(List.of("MZ-1", "MZ-2"));

        scheduler.pullAll();

        verify(publisher).leerAhora("MZ-1");
        verify(publisher).leerAhora("MZ-2");
    }

    @Test
    void emptyTopologyPublishesNothing() {
        when(zonas.findAllIds()).thenReturn(List.of());

        assertThatCode(scheduler::pullAll).doesNotThrowAnyException();

        verifyNoInteractions(publisher);
    }

    @Test
    void aZoneThatThrowsDoesNotStopTheOthers() {
        when(zonas.findAllIds()).thenReturn(List.of("MZ-1", "MZ-2", "MZ-3"));
        when(publisher.leerAhora("MZ-2")).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(scheduler::pullAll).doesNotThrowAnyException();

        verify(publisher).leerAhora("MZ-1");
        verify(publisher).leerAhora("MZ-3");
    }

    @Test
    void aZoneNotPublishedDoesNotStopTheOthers() {
        when(zonas.findAllIds()).thenReturn(List.of("MZ-1", "MZ-2", "MZ-3"));
        when(publisher.leerAhora("MZ-1")).thenReturn(new Resultado(false, "c", "boom"));

        assertThatCode(scheduler::pullAll).doesNotThrowAnyException();

        verify(publisher).leerAhora("MZ-2");
        verify(publisher).leerAhora("MZ-3");
    }

    @Test
    void zoneEnumerationFailureNeverEscapes() {
        when(zonas.findAllIds()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(scheduler::pullAll).doesNotThrowAnyException();

        verify(publisher, never()).leerAhora(any());
    }

    @Test
    void logsOneInfoSummaryWithRequestedAndFailedCounts() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SensingScheduler.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(zonas.findAllIds()).thenReturn(List.of("MZ-1", "MZ-2", "MZ-3"));
            when(publisher.leerAhora("MZ-2")).thenReturn(new Resultado(false, "c", "boom"));

            scheduler.pullAll();

            List<String> infos = appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.INFO)
                    .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertThat(infos).hasSize(1);
            assertThat(infos.get(0)).contains("requested=3").contains("failed=1");
        } finally {
            logger.detachAppender(appender);
        }
    }

    // --- trigger ---

    @Test
    void nextTickIsLastCompletionPlusInterval() {
        assertThat(next(T)).isEqualTo(T.plusSeconds(60));
    }

    @Test
    void changedIntervalAppliesOnTheNextComputation() {
        assertThat(next(T)).isEqualTo(T.plusSeconds(60));

        pullInterval(15);

        assertThat(next(T.plusSeconds(60))).isEqualTo(T.plusSeconds(75));
    }

    @Test
    void storedIntervalIsClamped() {
        pullInterval(2);
        assertThat(next(T)).isEqualTo(T.plusSeconds(10));

        pullInterval(99999);
        assertThat(next(T)).isEqualTo(T.plusSeconds(3600));
    }

    @Test
    void nullLastCompletionUsesTheClock() {
        assertThat(next(null)).isEqualTo(T.plusSeconds(60));
    }

    @Test
    void nullIntervalReadsAsSixty() {
        pullInterval(null);

        assertThat(next(T)).isEqualTo(T.plusSeconds(60));
    }

    @Test
    void configurationReadErrorFallsBackToSixty() {
        when(configuracion.getConfiguracionOperativa()).thenThrow(new IllegalStateException("db down"));

        assertThat(next(T)).isEqualTo(T.plusSeconds(60));
    }

    // --- wiring ---

    @Test
    void startSchedulesPullAllOnTheDedicatedLane() {
        scheduler.start();

        verify(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }
}
