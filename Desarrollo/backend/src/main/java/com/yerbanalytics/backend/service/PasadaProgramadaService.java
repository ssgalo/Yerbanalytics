package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.constant.NurseryConstants;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Pasada diaria del riel (design add-pulso-sensado-y-pasada-diaria D8): si {@code pasadaDiariaActiva}, en el minuto
 * de {@code pasadaDiariaHora} (hora local del vivero) inicia la misma pasada que {@code POST /api/pasadas}.
 *
 * <p>No guarda "ya corrió hoy": recuerda sólo el minuto en que disparó, así no repite dentro del mismo minuto y un
 * cambio de hora rige desde el minuto siguiente (incluso si la pasada del día ya corrió). Un reinicio después de la
 * hora no la recupera. Si la pasada se rechaza a su hora, queda una alerta {@code WARNING} y no se reintenta.
 *
 * <p>El tick corre más seguido que una vez por minuto para no saltear un minuto si se atrasa; la marca del minuto
 * disparado lo deja en un disparo por minuto.
 */
@Service
public class PasadaProgramadaService {

    private static final Logger log = LoggerFactory.getLogger(PasadaProgramadaService.class);

    /** Nombre de "regla" con que la alerta de rechazo queda en el historial. */
    public static final String REGLA = "PasadaProgramada";

    private final PasadaRielService pasadas;
    private final ConfiguracionService configuracion;
    private final HistorialService historial;
    private final Clock reloj;

    /** Minuto local en que se disparó la última pasada programada. Sólo lo toca el tick (un hilo). */
    private LocalDateTime ultimoDisparo;

    public PasadaProgramadaService(PasadaRielService pasadas, ConfiguracionService configuracion,
                                   HistorialService historial, Clock relojVivero) {
        this.pasadas = pasadas;
        this.configuracion = configuracion;
        this.historial = historial;
        this.reloj = relojVivero;
    }

    @Scheduled(fixedDelayString = "${yerbanalytics.pasada.programada-tick-ms:20000}", scheduler = "pasadaScheduler")
    public void tick() {
        try {
            revisar();
        } catch (RuntimeException e) {
            log.error("Pasada programada: falló la revisión del horario.", e);
        }
    }

    void revisar() {
        ConfiguracionOperativaEntity op = configuracion.getConfiguracionOperativa();
        if (op == null || !ConfiguracionService.pasadaDiariaActiva(op)) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now(reloj.withZone(ZonaHorariaVivero.ZONA)).truncatedTo(ChronoUnit.MINUTES);
        LocalTime hora = LocalTime.parse(ConfiguracionService.pasadaDiariaHora(op));
        if (!ahora.toLocalTime().equals(hora) || ahora.equals(ultimoDisparo)) {
            return;
        }
        ultimoDisparo = ahora;
        try {
            pasadas.iniciar();
            log.info("Pasada programada de las {}: iniciada.", hora);
        } catch (PasadaRechazadaException e) {
            log.warn("Pasada programada de las {}: rechazada — {}", hora, e.getMessage());
            Optional<ZonaEntity> zona = Optional.empty();
            try {
                zona = pasadas.zonaDelRecorrido();
            } catch (RuntimeException ex) {
                log.warn("Pasada programada: no se pudo resolver la zona del recorrido. {}", ex.getMessage());
            }
            historial.registrarAlerta(
                    zona.map(ZonaEntity::getId).orElse(NurseryConstants.EMDASH),
                    zona.map(ZonaEntity::getName).orElse(null),
                    REGLA,
                    new DetalleAlerta(NivelAlerta.WARNING, "La pasada programada no pudo iniciarse: " + e.getMessage()),
                    reloj.millis());
        }
    }
}
