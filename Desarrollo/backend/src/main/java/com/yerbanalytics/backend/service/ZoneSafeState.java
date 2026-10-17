package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Estado seguro de una macro-zona (reglas_v2 S-02): riego cerrado, bombas detenidas y mediasombra cerrada. Se ordena
 * cuando el nodo testigo queda fuera de servicio: sin su humedad no se puede regar con criterio, y cerrar la malla
 * baja la transpiración hasta que alguien revise el nodo.
 *
 * <p>Los comandos van a los equipos <b>registrados</b> en los sectores de la zona (ver {@link ActuatorShutdown}).
 *
 * <p>No toca el estado guardado de los sectores ni revierte nada cuando el nodo vuelve: decide el motor con la
 * lectura nueva.
 */
@Component
public class ZoneSafeState {

    private static final Logger log = LoggerFactory.getLogger(ZoneSafeState.class);

    private final SectorRepository sectors;
    private final ActuatorShutdown shutdown;

    public ZoneSafeState(SectorRepository sectors, ActuatorShutdown shutdown) {
        this.sectors = sectors;
        this.shutdown = shutdown;
    }

    /** Ordena el estado seguro de {@code zoneId}. Con la zona sin equipos registrados no publica nada. */
    public ActuatorShutdown.Result order(String zoneId) {
        List<String> sectorIds = sectors.findByZonaId(zoneId).stream().map(SectorEntity::getId).toList();
        ActuatorShutdown.Result result = shutdown.close(zoneId, sectorIds, ActuatorShutdown.ALL_TYPES);
        log.warn("Zona {}: estado seguro ordenado ({} órdenes, {} sin publicar).",
                zoneId, result.commands(), result.failed());
        return result;
    }
}
