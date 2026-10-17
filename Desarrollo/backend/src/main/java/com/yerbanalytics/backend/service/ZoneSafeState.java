package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estado seguro de una macro-zona (reglas_v2 S-02): riego cerrado, bombas detenidas y mediasombra cerrada. Se ordena
 * cuando el nodo testigo queda fuera de servicio: sin su humedad no se puede regar con criterio, y cerrar la malla
 * baja la transpiración hasta que alguien revise el nodo.
 *
 * <p>Los comandos van a los equipos <b>registrados</b> en los sectores de la zona (Hardware), uno por sector y tipo,
 * por el mismo {@link ComandoActuadorPublisher} que usan las reglas y las secuencias de Demo Expo: así
 * {@link SeguimientoComandosService} vigila cada orden hasta su ack. Una orden que no sale no frena a las demás.
 *
 * <p>No toca el estado guardado de los sectores ni revierte nada cuando el nodo vuelve: decide el motor con la
 * lectura nueva.
 */
@Component
public class ZoneSafeState {

    private static final Logger log = LoggerFactory.getLogger(ZoneSafeState.class);

    /** Cuántas órdenes salieron y cuántas no se pudieron publicar. */
    public record Result(int commands, int failed) {
    }

    /** Qué comando cierra cada tipo de equipo: tipo registrado → (actuador, acción, parámetros). */
    private record Order(String actuator, String action, Map<String, Object> parameters) {
    }

    private static final Map<String, Order> ORDERS = Map.of(
            HardwareService.ELECTROVALVULA, new Order("valve", "OFF", Map.of()),
            HardwareService.BOMBA, new Order("pump", "OFF", Map.of()),
            HardwareService.MEDIASOMBRA, new Order("shade", "SET", Map.of("targetPct", 0)));

    private final SectorRepository sectors;
    private final DispositivoRepository devices;
    private final ComandoActuadorPublisher publisher;

    public ZoneSafeState(SectorRepository sectors, DispositivoRepository devices, ComandoActuadorPublisher publisher) {
        this.sectors = sectors;
        this.devices = devices;
        this.publisher = publisher;
    }

    /** Ordena el estado seguro de {@code zoneId}. Con la zona sin equipos registrados no publica nada. */
    public Result order(String zoneId) {
        List<String> sectorIds = sectors.findByZonaId(zoneId).stream().map(SectorEntity::getId).toList();
        if (sectorIds.isEmpty()) {
            return new Result(0, 0);
        }
        // Un equipo repetido en el mismo sector y tipo no recibe dos veces la orden.
        Set<String> seen = new LinkedHashSet<>();
        int commands = 0;
        int failed = 0;
        for (DispositivoEntity device : devices.findBySectorIdInAndTipoIn(sectorIds, ORDERS.keySet())) {
            Order order = ORDERS.get(device.getTipo());
            if (order == null || !seen.add(device.getSectorId() + "|" + device.getTipo())) {
                continue;
            }
            if (publish(zoneId, device.getSectorId(), order)) {
                commands++;
            } else {
                failed++;
            }
        }
        log.warn("Zona {}: estado seguro ordenado ({} órdenes, {} sin publicar).", zoneId, commands, failed);
        return new Result(commands, failed);
    }

    private boolean publish(String zoneId, String sectorId, Order order) {
        try {
            ComandoActuadorPublisher.Resultado r = publisher.publicar(zoneId, sectorId, order.actuator(),
                    order.action(), order.parameters());
            if (!r.publicado()) {
                log.warn("Sector {}: la orden de estado seguro {} {} no se publicó — {}",
                        sectorId, order.actuator(), order.action(), r.error());
            }
            return r.publicado();
        } catch (RuntimeException e) {
            log.warn("Sector {}: falló la orden de estado seguro {} {} — {}",
                    sectorId, order.actuator(), order.action(), e.getMessage());
            return false;
        }
    }
}
