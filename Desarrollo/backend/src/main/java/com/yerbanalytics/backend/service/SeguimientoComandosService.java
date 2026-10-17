package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.AckActuador;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import com.yerbanalytics.backend.mqtt.NodeError;
import com.yerbanalytics.backend.mqtt.NodeErrorType;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seguimiento de los comandos a los actuadores (riego, insumo, mediasombra): cada comando publicado
 * espera el ack del nodo, como la pasada del riel espera sus eventos.
 *
 * <ul>
 *   <li><b>Envío:</b> {@code ComandoActuadorPublisher} registra cada comando publicado con su plazo:
 *       la duración pedida ({@code durationSec}, si la hay) más {@code yerbanalytics.actuadores.ack-margen-ms}.</li>
 *   <li><b>Ack intermedio {@code EN_CURSO}</b> (add-cola-actuadores-nodo): el comando sigue pendiente y su plazo se
 *       recalcula desde el arranque ({@code en_cola}: espera máxima + duración + margen; {@code iniciado}: duración +
 *       margen, desde la recepción) y se publica un {@link ActuatorProgress}. Sólo un ack final resuelve el comando;
 *       un status desconocido se loguea y no resuelve. Sin intermedios (firmware viejo) rige el plazo de siempre.</li>
 *   <li><b>Ack SUCCESS:</b> se cierra el seguimiento.</li>
 *   <li><b>Ack ERROR:</b> si es una falla (p. ej. {@code falla_mecanica}, comando rechazado) queda una
 *       alerta en el historial de la macro-zona; {@code reemplazado} y {@code cancelado} son esperables
 *       y sólo se loguean. La {@code falla_mecanica} de la mediasombra es además una falla de la malla (S-05): se
 *       entrega a {@link HardwareFaultService}, que la marca y deja <b>la</b> alerta (el mismo hecho puede llegar
 *       también por el tópico de errores). Sólo si el receptor de errores está apagado alerta este servicio.</li>
 *   <li><b>Cierre de estado:</b> un {@code cancelado} de la válvula, y el ack final de la bomba (ok, cancelado
 *       o error), publican un {@link ActuatorStopped}: el "regando" y el "dosificando" se derivan por tiempo y
 *       esto los cierra al instante. Un {@code reemplazado} no cierra nada.</li>
 *   <li><b>Sin ack en el plazo:</b> alerta {@value #REGLA_SIN_RESPUESTA} (el nodo está apagado, sin
 *       WiFi o no recibió el comando). Es el equivalente de {@code RIEL_SIN_RESPUESTA}.</li>
 * </ul>
 *
 * <p>No cambia ninguna decisión del motor por sí mismo: el despacho de riego no depende del ack (ver
 * {@code DespachoRiego}). Esto hace visible en la web lo que pasó en el nodo; lo único que además tiene consecuencias
 * es la falla de la malla, y la decide {@link HardwareFaultService}.
 *
 * <p><b>En memoria:</b> tras un reinicio se pierde lo pendiente (sus acks llegan como desconocidos:
 * un ERROR que sea falla igual alerta). Un commandId ya resuelto no se procesa dos veces: el nodo
 * reenvía el ack de un comando repetido, y un ack que llega después del plazo ya alertó.
 *
 * <p><b>Concurrencia:</b> los acks llegan por el hilo de Paho y el vencimiento corre en el scheduler
 * del motor; el {@code remove} del mapa concurrente decide quién se queda con cada comando.
 */
@Service
public class SeguimientoComandosService {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoComandosService.class);

    /** Regla con la que se registra en el historial un ERROR del nodo que es una falla. */
    static final String REGLA_FALLA = "FallaActuador";
    /** Regla con la que se registra en el historial un comando que el nodo no confirmó a tiempo. */
    static final String REGLA_SIN_RESPUESTA = "ActuadorSinRespuesta";

    /** Cuántos commandId resueltos se recuerdan para descartar acks repetidos o tardíos. */
    private static final int MAX_RESUELTOS = 500;

    /** Un comando publicado que todavía no tiene ack. */
    record Pendiente(String commandId, String zonaId, String sectorId, String actuador, long duracionMs, long venceMs) {
    }

    private final Map<String, Pendiente> pendientes = new ConcurrentHashMap<>();
    private final Set<String> resueltos = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> masViejo) {
                    return size() > MAX_RESUELTOS;
                }
            }));

    private final HistorialService historialService;
    private final ZonaRepository zonaRepository;
    private final HardwareFaultService hardwareFaultService;
    private final Clock reloj;
    private final long margenMs;
    private final ApplicationEventPublisher eventos;

    public SeguimientoComandosService(HistorialService historialService,
                                      ZonaRepository zonaRepository,
                                      HardwareFaultService hardwareFaultService,
                                      Clock reloj,
                                      @Value("${yerbanalytics.actuadores.ack-margen-ms:90000}") long margenMs,
                                      ApplicationEventPublisher eventos) {
        this.historialService = historialService;
        this.zonaRepository = zonaRepository;
        this.hardwareFaultService = hardwareFaultService;
        this.reloj = reloj;
        this.margenMs = margenMs;
        this.eventos = eventos;
    }

    /**
     * Registra un comando recién publicado. El plazo es {@code durationSec} (si viene) más el margen:
     * el nodo manda el ack cuando la acción termina, no cuando la recibe.
     */
    public void registrarEnvio(String commandId, String zonaId, String sectorId, String actuador,
                               Map<String, Object> parametros) {
        if (commandId == null) {
            return;
        }
        long duracionMs = 0;
        Object duracion = parametros != null ? parametros.get("durationSec") : null;
        if (duracion instanceof Number n && n.longValue() > 0) {
            duracionMs = n.longValue() * 1000L;
        }
        // La bomba de insumo dura lo que pida el comando en ml (el firmware la calibra a 1 s por ml).
        Object ml = parametros != null ? parametros.get("ml") : null;
        if (ml instanceof Number n && n.doubleValue() > 0) {
            duracionMs = Math.max(duracionMs, (long) (n.doubleValue() * ContratoNodo.BOMBA_INSUMO_MS_POR_ML));
        }
        pendientes.put(commandId, new Pendiente(commandId, zonaId, sectorId, actuador, duracionMs,
                reloj.millis() + duracionMs + margenMs));
    }

    /** Procesa un ack del nodo. La zona y el sector del ack salen del tópico. */
    public void registrarAck(AckActuador ack) {
        String zonaId = ack.zonaId();
        String sectorId = ack.sectorId();
        String commandId = ack.commandId();
        if (ack.esIntermedio()) {
            registrarIntermedio(ack);
            return;
        }
        // Sólo un final (SUCCESS/ERROR) resuelve el comando: un status que no se conoce se loguea y lo deja pendiente.
        if (!ContratoNodo.ACK_SUCCESS.equals(ack.status()) && !ContratoNodo.ACK_ERROR.equals(ack.status())) {
            log.warn("Sector {}: ack con status desconocido '{}' (commandId {}), se ignora.",
                    sectorId, ack.status(), commandId);
            return;
        }
        if (commandId != null && !resueltos.add(commandId)) {
            log.debug("Sector {}: ack repetido o tardío de {}, se ignora.", sectorId, commandId);
            return;
        }
        Pendiente p = commandId != null ? pendientes.remove(commandId) : null;
        String actuador = p != null ? p.actuador() : null;
        String tipo = ack.tipoDetalle();

        if (ContratoNodo.ACK_SUCCESS.equals(ack.status())) {
            log.info("Sector {}: el nodo confirmó {} ({}).", sectorId, nombre(actuador), tipo);
            avisarSiTerminoLaBomba(zonaId, sectorId, actuador, commandId, tipo);
            return;
        }
        if (ContratoNodo.ACK_TIPO_REEMPLAZADO.equals(tipo) || ContratoNodo.ACK_TIPO_CANCELADO.equals(tipo)) {
            log.info("Sector {}: {} terminó antes de tiempo ({}).", sectorId, nombre(actuador), tipo);
            // Un cancelado es que alguien cortó el actuador (OFF manual, protección del nodo): el estado "regando" /
            // "dosificando" que el backend deriva por tiempo ya no es cierto. Un reemplazado, en cambio, es que otro
            // comando más nuevo tomó su lugar: ese sigue vigente y no se cierra nada.
            if (ContratoNodo.ACK_TIPO_CANCELADO.equals(tipo)) {
                avisarCorte(zonaId, sectorId, actuador, commandId, tipo);
            }
            return;
        }

        log.warn("Sector {}: el nodo informó un error en {} ({}).", sectorId, nombre(actuador), tipo);
        avisarSiTerminoLaBomba(zonaId, sectorId, actuador, commandId, tipo);
        if (ContratoNodo.ACK_TIPO_FALLA_MECANICA.equals(tipo)) {
            // Es la falla de la malla de la macro-zona (S-05): la marca y la alerta son del servicio de fallas.
            NodeError falla = new NodeError(zonaId, null, "", NodeErrorType.SHADE_LIMIT_SWITCH, null);
            if (hardwareFaultService.report(falla) == HardwareFaultService.Outcome.IGNORED) {
                alertar(zonaId, NivelAlerta.CRITICAL, REGLA_FALLA, "Sector " + sectorId + ": "
                        + "la mediasombra no llegó al final de carrera a tiempo (" + tipo + "). "
                        + "Revisar el motor y los finales de carrera.");
            }
        } else {
            alertar(zonaId, NivelAlerta.WARNING, REGLA_FALLA, "Sector " + sectorId + ": "
                    + "el nodo no pudo ejecutar " + nombre(actuador) + " (" + (tipo != null ? tipo : "sin detalle") + ").");
        }
    }

    /**
     * Un ack intermedio ({@code EN_CURSO}): el comando sigue pendiente y su plazo se recalcula desde lo que informó el
     * nodo. {@code en_cola}: espera máxima + duración + margen. {@code iniciado}: duración + margen. Siempre desde la hora
     * de recepción (el ack no lleva hora y el reloj del nodo puede no estar sincronizado). De un comando ya resuelto o
     * desconocido se ignora: sin pendiente no se sabe de qué actuador es.
     */
    private void registrarIntermedio(AckActuador ack) {
        String commandId = ack.commandId();
        Pendiente p = commandId != null ? pendientes.get(commandId) : null;
        if (p == null) {
            log.debug("Sector {}: ack intermedio de {} que no está pendiente, se ignora.", ack.sectorId(), commandId);
            return;
        }
        long ahora = reloj.millis();
        ActuatorProgress.Phase fase;
        long esperaMs = 0;
        long vence;
        if (ContratoNodo.ACK_TIPO_EN_COLA.equals(ack.tipoDetalle())) {
            fase = ActuatorProgress.Phase.QUEUED;
            esperaMs = ack.esperaMaxSec() * 1000L;
            vence = esperaMs > 0 ? ahora + esperaMs + p.duracionMs() + margenMs : p.venceMs();
        } else if (ContratoNodo.ACK_TIPO_INICIADO.equals(ack.tipoDetalle())) {
            fase = ActuatorProgress.Phase.STARTED;
            vence = ahora + p.duracionMs() + margenMs;
        } else {
            log.warn("Sector {}: ack EN_CURSO con detalle '{}' desconocido (commandId {}), se ignora.",
                    ack.sectorId(), ack.tipoDetalle(), commandId);
            return;
        }
        // replace: si el vencimiento o un final se llevó el comando en el medio, no se lo vuelve a poner.
        if (!pendientes.replace(commandId, p, new Pendiente(p.commandId(), p.zonaId(), p.sectorId(), p.actuador(),
                p.duracionMs(), vence))) {
            return;
        }
        log.info("Sector {}: {} {} en el nodo.", ack.sectorId(), nombre(p.actuador()),
                fase == ActuatorProgress.Phase.QUEUED ? "espera turno" : "arrancó");
        try {
            eventos.publishEvent(new ActuatorProgress(ack.zonaId(), ack.sectorId(), p.actuador(), commandId, fase,
                    ahora, esperaMs));
        } catch (RuntimeException e) {
            log.error("Sector {}: falló al avisar el progreso de {} ({}).", ack.sectorId(), nombre(p.actuador()), fase, e);
        }
    }

    /** Alerta por cada comando que venció sin ack. */
    @Scheduled(fixedDelayString = "${yerbanalytics.actuadores.ack-revision-ms:10000}")
    public void revisarVencidos() {
        long ahora = reloj.millis();
        for (Pendiente p : List.copyOf(pendientes.values())) {
            if (p.venceMs() <= ahora && pendientes.remove(p.commandId(), p)) {
                resueltos.add(p.commandId());   // un ack que llegue después ya no vuelve a alertar
                log.warn("Sector {}: el nodo no confirmó {} (commandId {}).", p.sectorId(), nombre(p.actuador()), p.commandId());
                alertar(p.zonaId(), NivelAlerta.WARNING, REGLA_SIN_RESPUESTA, "Sector " + p.sectorId() + ": "
                        + "el nodo no confirmó " + nombre(p.actuador()) + " a tiempo. "
                        + "Puede estar apagado, sin WiFi o sin conexión al broker.");
            }
        }
    }

    /** Cantidad de comandos esperando ack (para tests y diagnóstico). */
    int pendientes() {
        return pendientes.size();
    }

    /** El ack final de la bomba (ok o error) cierra la dosis; el de la válvula no: ella se cierra sola por tiempo. */
    private void avisarSiTerminoLaBomba(String zonaId, String sectorId, String actuador, String commandId, String tipo) {
        if ("pump".equals(actuador)) {
            avisarCorte(zonaId, sectorId, actuador, commandId, tipo);
        }
    }

    /** Sólo para válvula y bomba (los que derivan un estado por tiempo) y si se sabe de qué comando es el ack. */
    private void avisarCorte(String zonaId, String sectorId, String actuador, String commandId, String tipo) {
        if (!"valve".equals(actuador) && !"pump".equals(actuador)) {
            return;
        }
        try {
            eventos.publishEvent(new ActuatorStopped(zonaId, sectorId, actuador, commandId, tipo));
        } catch (RuntimeException e) {
            log.error("Sector {}: falló al cerrar el estado de {} tras el ack {}.", sectorId, nombre(actuador), tipo, e);
        }
    }

    private void alertar(String zonaId, NivelAlerta nivel, String regla, String texto) {
        String zonaName = zonaRepository.findById(zonaId).map(ZonaEntity::getName).orElse(zonaId);
        try {
            historialService.registrarAlerta(zonaId, zonaName, regla, new DetalleAlerta(nivel, texto), reloj.millis());
        } catch (RuntimeException e) {
            log.error("Zona {}: no se pudo registrar la alerta {} — {}", zonaId, regla, texto, e);
        }
    }

    /** "el riego", "el insumo", "la mediasombra": para los textos del historial. */
    private static String nombre(String actuador) {
        if (actuador == null) {
            return "el comando";
        }
        return switch (actuador) {
            case "valve" -> "el riego";
            case "pump" -> "el insumo";
            case "shade" -> "la mediasombra";
            default -> "el comando de " + actuador;
        };
    }
}
