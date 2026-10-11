package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.TriggerContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Backend-driven sensing: every X seconds asks every zone for a reading ({@code LEER_AHORA}).
 *
 * <p>X is {@code sensingPullIntervalSeconds} from the operating configuration, re-read each time the
 * next tick is computed, so a saved change applies without a restart. Cycles never overlap (the next
 * tick is computed from the previous completion) and a missed pull is neither queued nor replayed.
 * There is no autonomous fallback: if this scheduler does not pull, no periodic data is produced.
 */
// Explicit name: the default ("sensingScheduler") is taken by the TaskScheduler lane bean in SchedulersConfig.
@Component("sensingPullScheduler")
public class SensingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SensingScheduler.class);

    private final ZonaRepository zonaRepository;
    private final ComandoZonaPublisher publisher;
    private final ConfiguracionService configuracionService;
    private final TaskScheduler scheduler;
    private final Clock clock;

    public SensingScheduler(ZonaRepository zonaRepository,
                            ComandoZonaPublisher publisher,
                            ConfiguracionService configuracionService,
                            @Qualifier("sensingScheduler") TaskScheduler scheduler,
                            Clock clock) {
        this.zonaRepository = zonaRepository;
        this.publisher = publisher;
        this.configuracionService = configuracionService;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    /**
     * Starts the periodic pull once the application is ready. The task is scheduled on the dedicated
     * lane directly (not through the shared {@code ScheduledTaskRegistrar}) so {@code NurseryWatchdog}
     * stays on the default {@code taskScheduler}.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.schedule(this::pullAll, this::nextExecution);
        log.info("SensingScheduler: periodic pull started.");
    }

    /** Next tick: last completion (or now) plus the interval stored right now, clamped. */
    Instant nextExecution(TriggerContext context) {
        Instant base = context.lastCompletion();
        if (base == null) {
            base = clock.instant();
        }
        return base.plusSeconds(currentIntervalSeconds());
    }

    private int currentIntervalSeconds() {
        try {
            return SensingCadence.pullSeconds(configuracionService.getConfiguracionOperativa().getSensingPullIntervalSeconds());
        } catch (Exception e) {
            log.warn("SensingScheduler: could not read the pull interval, using {} s. {}",
                    SensingCadence.DEFAULT_SECONDS, e.getMessage());
            return SensingCadence.DEFAULT_SECONDS;
        }
    }

    /** One pull cycle: one {@code LEER_AHORA} per zone. Failures are isolated per zone and never escape. */
    public void pullAll() {
        List<String> zoneIds;
        try {
            zoneIds = zonaRepository.findAllIds();
        } catch (Exception e) {
            log.warn("SensingScheduler: could not list zones, skipping this cycle. {}", e.getMessage());
            return;
        }

        int failed = 0;
        for (String zoneId : zoneIds) {
            try {
                if (!publisher.leerAhora(zoneId).publicado()) {
                    failed++;
                }
            } catch (Exception e) {
                failed++;
                log.warn("SensingScheduler: pull to zone {} failed. {}", zoneId, e.getMessage());
            }
        }
        if (!zoneIds.isEmpty()) {
            log.info("SensingScheduler: pull cycle done — requested={}, failed={}", zoneIds.size(), failed);
        }
    }
}
