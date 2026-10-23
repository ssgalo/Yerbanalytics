package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.ComandoActuadorPublisher;
import com.yerbanalytics.backend.mediasombra.MediasombraService;
import com.yerbanalytics.backend.model.DispositivoEntity;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ordena el cierre de los equipos <b>registrados</b> (Hardware) de una macro-zona: válvula {@code OFF} y bomba
 * {@code OFF} de los sectores pedidos, y la mediasombra de la zona cerrada. Quien llama elige qué tipos de equipo: el
 * estado seguro de una zona ({@link ZoneSafeState}) cierra los tres; el bloqueo manual ({@link ManualLockService}) sólo
 * agua y bombas.
 *
 * <p>Publica primero los {@code OFF} y al final la mediasombra, que lleva {@code prioridad: "INMEDIATA"}: el nodo
 * serializa las cargas ({@code add-cola-actuadores-nodo}) y esta es la única orden que no espera turno. La malla es de
 * la macro-zona: va por el comando de zona, y sólo si la zona tiene una registrada.
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
            // prioridad INMEDIATA: es la orden de seguridad, el nodo no la hace esperar en su cola de cargas
            // (add-cola-actuadores-nodo, D-3.1). Un SET 0 común (motor, secuencias) sí espera su turno.
            HardwareService.MEDIASOMBRA, new Order("shade", "SET",
                    Map.of("targetPct", 0, ContratoNodo.PARAM_PRIORIDAD, ContratoNodo.PRIORIDAD_INMEDIATA)));

    /** Todos los tipos de equipo que sabe cerrar. */
    public static final Set<String> ALL_TYPES = ORDERS.keySet();

    private final DispositivoRepository devices;
    private final ComandoActuadorPublisher publisher;
    private final MediasombraService mediasombra;

    public ActuatorShutdown(DispositivoRepository devices, ComandoActuadorPublisher publisher,
                            @Lazy MediasombraService mediasombra) {
        this.devices = devices;
        this.publisher = publisher;
        this.mediasombra = mediasombra;
    }

    /**
     * Cierra los equipos de los {@code types} registrados en {@code sectorIds}, uno por sector y tipo, y la mediasombra
     * de la zona si {@code types} la incluye. Sin equipos registrados no publica nada.
     */
    public Result close(String zoneId, Collection<String> sectorIds, Collection<String> types) {
        Set<String> seen = new LinkedHashSet<>();
        int commands = 0;
        int failed = 0;
        // Los OFF primero y la malla al final: el instante en que se suman cargas (la malla arranca ya, aunque haya
        // otras corriendo) queda en milisegundos, no mientras todavía se están cerrando las válvulas.
        List<String> sectorTypes = types.stream().filter(t -> !HardwareService.MEDIASOMBRA.equals(t)).toList();
        List<DispositivoEntity> found = new ArrayList<>(sectorIds.isEmpty() || sectorTypes.isEmpty() ? List.of()
                : devices.findBySectorIdInAndTipoIn(sectorIds, sectorTypes));
        for (DispositivoEntity device : found) {
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
        if (types.contains(HardwareService.MEDIASOMBRA)
                && !devices.findByZonaIdAndTipo(zoneId, HardwareService.MEDIASOMBRA).isEmpty()) {
            if (publish(zoneId, null, ORDERS.get(HardwareService.MEDIASOMBRA))) {
                commands++;
                mediasombra.registrarPosicion(zoneId, false);
            } else {
                failed++;
            }
        }
        return new Result(commands, failed);
    }

    private boolean publish(String zoneId, String sectorId, Order order) {
        try {
            ComandoActuadorPublisher.Resultado r = sectorId == null
                    ? publisher.publicarZona(zoneId, order.actuator(), order.action(), order.parameters())
                    : publisher.publicar(zoneId, sectorId, order.actuator(), order.action(), order.parameters());
            if (!r.publicado()) {
                log.warn("{}: la orden de cierre {} {} no se publicó — {}",
                        donde(zoneId, sectorId), order.actuator(), order.action(), r.error());
            }
            return r.publicado();
        } catch (RuntimeException e) {
            log.warn("{}: falló la orden de cierre {} {} — {}",
                    donde(zoneId, sectorId), order.actuator(), order.action(), e.getMessage());
            return false;
        }
    }

    private static String donde(String zoneId, String sectorId) {
        return sectorId != null ? "Sector " + sectorId : "Zona " + zoneId;
    }
}
