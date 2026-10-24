package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ReadingParameters;
import com.yerbanalytics.backend.repository.ZoneReadingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Retención del historial: una vez por día borra las lecturas más viejas que {@code readings.retention-days}
 * (fábrica 14 días: cubre las 72 h que miran las reglas con margen para auditar). Lee el parámetro del catálogo, así
 * que declararlo en {@link #parametros()} es lo que lo hace figurar "usado" en la vista de reglas.
 *
 * <p>El catálogo se pide {@code @Lazy}: él mismo se arma con la lista de quienes leen parámetros, y esta clase es una
 * (igual que el despacho de riego); sin eso habría un ciclo al arrancar.
 *
 * <p>No abre una transacción propia: el borrado es una sola sentencia que el repositorio ejecuta en la suya. Una falla
 * de la base no escapa de la tarea; se reintenta al día siguiente y el historial sólo crece un día de más.
 */
@Component
public class ReadingPurge implements ConsumidorParametros {

    private static final Logger log = LoggerFactory.getLogger(ReadingPurge.class);

    private final ZoneReadingRepository repository;
    private final CatalogoParametrosService parameters;
    private final Clock clock;

    public ReadingPurge(ZoneReadingRepository repository, @Lazy CatalogoParametrosService parameters,
                        Clock relojVivero) {
        this.repository = repository;
        this.parameters = parameters;
        this.clock = relojVivero;
    }

    @Override
    public String name() {
        return "ReadingPurge";
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ReadingParameters.RETENTION_DAYS);
    }

    /** Todos los días a las 03:30, hora del vivero. */
    @Scheduled(cron = "0 30 3 * * *", zone = ZonaHorariaVivero.ID)
    public void scheduledPurge() {
        try {
            int deleted = purge();
            log.info("Historial de lecturas: se borraron {} lectura(s) fuera de la retención.", deleted);
        } catch (RuntimeException e) {
            log.error("Historial de lecturas: falló la purga, se reintenta mañana.", e);
        }
    }

    /** Borra lo anterior a la retención vigente y devuelve cuántas lecturas eran. */
    public int purge() {
        long days = (long) parameters.vigentes().de(this).numero(ReadingParameters.RETENTION_DAYS);
        long cutoff = clock.instant().minus(Duration.ofDays(days)).toEpochMilli();
        return repository.deleteByTsLessThan(cutoff);
    }
}
