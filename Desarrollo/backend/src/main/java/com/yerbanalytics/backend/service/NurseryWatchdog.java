package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ActionExecutor;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.RuleOrchestrator;
import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.weather.WeatherService;
import com.yerbanalytics.backend.model.BloqueoManualEntity;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.BloqueoManualRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Watchdog proactivo del motor de reglas (HU-02 CA-03/04, HU-08).
 *
 * <p>Complementa el trigger reactivo (MQTT) con una evaluación periódica de todos
 * los sectores. Sin este scheduler, si el hardware se apaga y deja de publicar
 * telemetría, el motor nunca se evaluaría y las condiciones críticas pasarían
 * desapercibidas.
 *
 * <h3>Funciones</h3>
 * <ul>
 *   <li><b>Detección de nodo caído:</b> itera todas las zonas y evalúa el motor
 *       aunque no haya telemetría reciente. El {@code StaleSensorRule} detectará
 *       la antigüedad y emitirá {@code ABORT_RIEGO} o la alerta correspondiente.</li>
 *   <li><b>Reglas independientes de telemetría:</b> evaluación periódica de
 *       {@code MediasombraRule} (plan de rustificación por día del ciclo) y
 *       {@code ClimaOverrideRule} (pronóstico climático actualizado).</li>
 * </ul>
 *
 * <h3>Configuración</h3>
 * <ul>
 *   <li>{@code yerbanalytics.engine.watchdog-interval-ms} — intervalo del scheduler
 *       (default: 5 minutos)</li>
 * </ul>
 */
@Service
public class NurseryWatchdog {

    private static final Logger log = LoggerFactory.getLogger(NurseryWatchdog.class);

    private final ZonaRepository zonaRepository;
    private final SectorRepository sectorRepository;
    private final RuleOrchestrator ruleOrchestrator;
    private final ActionExecutor actionExecutor;
    private final ConfiguracionService configuracionService;
    private final WeatherService weatherService;
    private final BloqueoManualRepository bloqueoManualRepository;
    private final long staleThresholdMs;

    public NurseryWatchdog(ZonaRepository zonaRepository,
                           SectorRepository sectorRepository,
                           RuleOrchestrator ruleOrchestrator,
                           ActionExecutor actionExecutor,
                           ConfiguracionService configuracionService,
                           WeatherService weatherService,
                           BloqueoManualRepository bloqueoManualRepository,
                           @Value("${yerbanalytics.nursery.stale-threshold-ms}") long staleThresholdMs) {
        this.zonaRepository = zonaRepository;
        this.sectorRepository = sectorRepository;
        this.ruleOrchestrator = ruleOrchestrator;
        this.actionExecutor = actionExecutor;
        this.configuracionService = configuracionService;
        this.weatherService = weatherService;
        this.bloqueoManualRepository = bloqueoManualRepository;
        this.staleThresholdMs = staleThresholdMs;
    }

    /**
     * Evaluación proactiva periódica del motor de reglas para todos los sectores.
     *
     * <p>Corre en el hilo del scheduler de Spring. Cada zona y sus sectores se evalúan
     * en orden, usando el último estado conocido de la base de datos.
     */
    @Scheduled(fixedDelayString = "${yerbanalytics.engine.watchdog-interval-ms:300000}")
    @Transactional
    public void evaluarTodos() {
        log.debug("NurseryWatchdog: iniciando evaluación proactiva de todos los sectores.");

        List<ZonaEntity> zonas = zonaRepository.findAllWithSectors();
        WeatherForecast forecast = weatherService.getForecast();

        // Todo lo que no depende del sector se resuelve UNA vez, fuera del barrido: con 600
        // sectores, lo que acá parece una llamada más adentro del bucle son 1200 consultas por
        // ciclo. Los bloqueos activos son un puñado y entran holgados como dos conjuntos.
        List<MetricSpec> specs = configuracionService.getEffectiveSpecs();
        ConfiguracionOperativaEntity operativa = configuracionService.getConfiguracionOperativa();
        List<BloqueoManualEntity> bloqueos = bloqueoManualRepository.findByActivoTrue();
        Set<String> sectoresBloqueados = bloqueos.stream()
                .map(BloqueoManualEntity::getSectorId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> zonasBloqueadas = bloqueos.stream()
                .map(BloqueoManualEntity::getZonaId).filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Un único instante para todo el barrido: los 600 sectores se evalúan contra la misma
        // foto del tiempo, en vez de contra un reloj que se corre mientras el ciclo avanza.
        long ahoraMs = System.currentTimeMillis();
        Instant ahora = Instant.ofEpochMilli(ahoraMs);
        int totalSectores = 0;

        for (ZonaEntity zona : zonas) {
            boolean sensorStale = zona.getLastReadingTime() == null
                    || (ahoraMs - zona.getLastReadingTime() > staleThresholdMs);
            boolean zonaBloqueada = zonasBloqueadas.contains(zona.getId());

            for (SectorEntity sector : zona.getSectors()) {
                boolean bloqueoActivo = zonaBloqueada || sectoresBloqueados.contains(sector.getId());

                // En el ciclo proactivo usamos las métricas derivadas del último estado conocido.
                // Si el sensor está stale, las métricas serán nulas y StaleSensorRule actuará.
                RuleContext ctx = new RuleContext(
                        sector,
                        zona,
                        specs,
                        List.of(),   // sin métricas frescas — el StaleSensorRule gestiona este caso
                        operativa,
                        sector.getStatus(),
                        ahora,
                        sensorStale,
                        forecast,
                        bloqueoActivo
                );

                actionExecutor.execute(ruleOrchestrator.evaluate(ctx), ctx);
                totalSectores++;
            }
        }

        if (totalSectores > 0) {
            sectorRepository.saveAll(
                    zonas.stream().flatMap(z -> z.getSectors().stream()).toList()
            );
        }

        log.debug("NurseryWatchdog: evaluación proactiva completada — {} sectores evaluados.", totalSectores);
    }
}
