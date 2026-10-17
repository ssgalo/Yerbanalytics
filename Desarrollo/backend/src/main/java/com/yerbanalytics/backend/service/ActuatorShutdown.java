package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Ordena el cierre de los equipos <b>registrados</b> (Hardware) en un conjunto de sectores: válvula {@code OFF},
 * bomba {@code OFF} y mediasombra cerrada. Quien llama elige qué tipos de equipo: el estado seguro de una zona
 * ({@link ZoneSafeState}) cierra los tres; el bloqueo manual ({@link ManualLockService}) sólo agua y bombas.
 *
 * <p>Sale por el mismo {@link ComandoActuadorPublisher} que las reglas y las secuencias de Demo Expo, así
 * {@link SeguimientoComandosService} vigila cada orden hasta su ack. Una orden que no sale no frena a las demás.
 */
@Component
public class ActuatorShutdown {

    private static final Logger log = LoggerFactory.getLogger(ActuatorShutdown.class);

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

    /** Todos los tipos de equipo que sabe cerrar. */
    public static final Set<String> ALL_TYPES = ORDERS.keySet();

    private final DispositivoRepository devices;
    private final ComandoActuadorPublisher publisher;

    public ActuatorShutdown(DispositivoRepository devices, ComandoActuadorPublisher publisher) {
        this.devices = devices;
        this.publisher = publisher;
    }

    /**
     * Cierra los equipos de los {@code types} registrados en {@code sectorIds}, uno por sector y tipo.
     * Sin sectores o sin equipos registrados no publica nada.
     */
    public Result close(String zoneId, Collection<String> sectorIds, Collection<String> types) {
        if (sectorIds.isEmpty()) {
            return new Result(0, 0);
        }
        Set<String> seen = new LinkedHashSet<>();
        int commands = 0;
        int failed = 0;
        for (DispositivoEntity device : devices.findBySectorIdInAndTipoIn(sectorIds, types)) {
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
        return new Result(commands, failed);
    }

    private boolean publish(String zoneId, String sectorId, Order order) {
        try {
            ComandoActuadorPublisher.Resultado r = publisher.publicar(zoneId, sectorId, order.actuator(),
                    order.action(), order.parameters());
            if (!r.publicado()) {
                log.warn("Sector {}: la orden de cierre {} {} no se publicó — {}",
                        sectorId, order.actuator(), order.action(), r.error());
            }
            return r.publicado();
        } catch (RuntimeException e) {
            log.warn("Sector {}: falló la orden de cierre {} {} — {}",
                    sectorId, order.actuator(), order.action(), e.getMessage());
            return false;
        }
    }
}
