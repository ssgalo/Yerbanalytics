package com.yerbanalytics.backend.supply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reintenta las aplicaciones diferidas. Corre con un intervalo corto y deja que las precondiciones decidan: así una
 * aplicación diferida sale apenas se abre la ventana (07:00 / 17:00 de fábrica) y, dentro de ella, apenas se cumplen la
 * temperatura, la lluvia o la humedad. Las ventanas son parámetros del catálogo: atar el tick a dos horas fijas las
 * desfasaría en cuanto el agrónomo las cambie.
 *
 * <p>Sin pendientes cuesta una consulta.
 */
@Component
public class PendingApplicationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PendingApplicationScheduler.class);

    private final ApplicationService applications;

    public PendingApplicationScheduler(ApplicationService applications) {
        this.applications = applications;
    }

    @Scheduled(fixedDelayString = "${yerbanalytics.insumo.pendientes-revision-ms:300000}")
    public void tick() {
        try {
            int applied = applications.processPending();
            if (applied > 0) {
                log.info("Aplicaciones diferidas: {} salieron en este ciclo.", applied);
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron reintentar las aplicaciones diferidas; se reintenta en el próximo ciclo.", e);
        }
    }
}
