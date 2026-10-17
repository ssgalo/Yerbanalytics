package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.config.PulsoProperties;
import com.yerbanalytics.backend.engine.riego.CicloLectura;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.OrigenPulso;
import com.yerbanalytics.backend.sensing.PulsoSensadoService.PulsoEnCursoException;
import com.yerbanalytics.backend.service.ConfiguracionService;
import com.yerbanalytics.backend.service.EscalamientoNodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Planificador del pulso de sensado: emite un pulso con motor al comienzo de cada franja de
 * {@code intervaloSensadoMinutos}, anclada a las 02:00 ({@link CicloLectura}, la misma función que usa el riego:
 * el pulso coincide con el ciclo de lectura por construcción), y en cada tick avanza los reintentos del pulso en
 * curso.
 *
 * <p>En lugar de calcular el próximo disparo una vez, cada tick compara la franja actual con la del último pulso:
 * así un cambio de intervalo guardado rige en el tick siguiente, sin reiniciar. Al arrancar se toma la franja en
 * curso como ya atendida: un pulso perdido mientras el backend estaba caído no se recupera. Si al empezar una
 * franja hay un pulso en curso (uno manual largo), el programado se omite y queda en el log. Un pulso manual no
 * corre las franjas.
 */
// Explicit name: the default ("sensingScheduler") is taken by the TaskScheduler lane bean in SchedulersConfig.
@Component("sensingPullScheduler")
public class SensingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SensingScheduler.class);

    private final PulsoSensadoService pulsos;
    private final EscalamientoNodoService escalamiento;
    private final ConfiguracionService configuracionService;
    private final TaskScheduler scheduler;
    private final PulsoProperties properties;
    private final Clock clock;

    /** Inicio de la franja del último pulso programado (o de la vigente al arrancar). Sólo lo toca el tick. */
    private Instant ultimaFranja;

    public SensingScheduler(PulsoSensadoService pulsos,
                            EscalamientoNodoService escalamiento,
                            ConfiguracionService configuracionService,
                            @Qualifier("sensingScheduler") TaskScheduler scheduler,
                            PulsoProperties properties,
                            Clock clock) {
        this.pulsos = pulsos;
        this.escalamiento = escalamiento;
        this.configuracionService = configuracionService;
        this.scheduler = scheduler;
        this.properties = properties;
        this.clock = clock;
    }

    /** Arranca el tick en su carril propio, para que el pulso nunca espere detrás de otra tarea. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ultimaFranja = franjaActual();
        scheduler.scheduleWithFixedDelay(this::tick, Duration.ofMillis(properties.getTickMs()));
        log.info("Pulso de sensado: planificador iniciado (franja vigente desde {}).", ultimaFranja);
    }

    /** Un tick: dispara el pulso de una franja nueva, avanza los reintentos y reintenta los nodos caídos. Nada escapa. */
    void tick() {
        try {
            revisarFranja();
        } catch (RuntimeException e) {
            log.error("Pulso de sensado: falló la revisión de la franja.", e);
        }
        try {
            pulsos.revisarReintentos();
        } catch (RuntimeException e) {
            log.error("Pulso de sensado: fallaron los reintentos del pulso en curso.", e);
        }
        try {
            escalamiento.retryOfflineNodes();
        } catch (RuntimeException e) {
            log.error("Pulso de sensado: falló el reintento de los nodos fuera de servicio.", e);
        }
    }

    /** Si empezó una franja posterior a la del último pulso, emite el pulso programado. */
    void revisarFranja() {
        Instant franja = franjaActual();
        if (ultimaFranja == null) {
            ultimaFranja = franja;
            return;
        }
        if (!franja.isAfter(ultimaFranja)) {
            return;
        }
        ultimaFranja = franja;
        try {
            pulsos.iniciar(OrigenPulso.PROGRAMADO, true);
        } catch (PulsoEnCursoException e) {
            log.warn("Pulso de sensado: el pulso programado de la franja {} se omite, hay otro en curso.", franja);
        }
    }

    private Instant franjaActual() {
        return CicloLectura.inicio(clock.instant(), currentIntervalMinutes());
    }

    private int currentIntervalMinutes() {
        try {
            return SensingCadence.intervalMinutes(
                    configuracionService.getConfiguracionOperativa().getIntervaloSensadoMinutos());
        } catch (RuntimeException e) {
            log.warn("Pulso de sensado: no se pudo leer el intervalo, se usan {} min. {}",
                    SensingCadence.DEFAULT_MINUTES, e.getMessage());
            return SensingCadence.DEFAULT_MINUTES;
        }
    }
}
