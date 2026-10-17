package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.overrides.RuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.SensingParameters;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Escalamiento por pulsos consecutivos sin respuesta del nodo testigo (design D4). La cuenta se persiste en la
 * zona ({@link ZonaEntity#getPulsosSinRespuesta()}) para que un reinicio en medio de una racha no la pierda.
 *
 * <p>1.º y 2.º pulso sin respuesta → alerta {@code WARNING}; 3.º → {@code CRITICAL}, el nodo queda fuera de
 * servicio y la zona pasa a estado seguro ({@link ZoneSafeState}, una sola vez); del 4.º en adelante, sin alertas. Cualquier telemetría de la zona vuelve la cuenta a 0 y, si el nodo
 * estaba fuera de servicio, deja una alerta {@code INFO}.
 */
@Service
public class EscalamientoNodoService implements ConsumidorParametros {

    private static final Logger log = LoggerFactory.getLogger(EscalamientoNodoService.class);

    /** Nombre técnico del escalamiento: el de su consumo de parámetros y el de sus overrides. */
    public static final String NAME = "EscalamientoNodo";

    /** Clave de cooldown: minutos entre dos pedidos de lectura a un nodo fuera de servicio (0 pide en cada tick). */
    public static final String COOLDOWN_RETRY_OFFLINE = "retry-offline";

    /** Nombre de "regla" con que las alertas del pulso quedan en el historial. */
    public static final String REGLA = "PulsoSensado";

    /** Pulsos consecutivos sin respuesta a partir de los cuales el nodo está fuera de servicio. */
    public static final int FUERA_DE_SERVICIO = 3;

    private final ZonaRepository zonaRepository;
    private final HistorialService historialService;
    private final ZoneSafeState safeState;
    private final ComandoZonaPublisher zonePublisher;
    private final CatalogoParametrosService parametros;
    private final RuleOverrides overrides;
    private final Clock reloj;

    /** Último pedido de lectura a cada zona fuera de servicio (epoch ms). En memoria: tras un reinicio se pide de inmediato. */
    private final Map<String, Long> lastOfflineRequest = new ConcurrentHashMap<>();

    public EscalamientoNodoService(ZonaRepository zonaRepository, HistorialService historialService,
                                   ZoneSafeState safeState, ComandoZonaPublisher zonePublisher,
                                   // Perezoso: el catálogo se arma con la lista de consumidores, y esta clase es uno.
                                   @Lazy CatalogoParametrosService parametros,
                                   RuleOverrides overrides, Clock relojVivero) {
        this.zonaRepository = zonaRepository;
        this.historialService = historialService;
        this.safeState = safeState;
        this.zonePublisher = zonePublisher;
        this.parametros = parametros;
        this.overrides = overrides;
        this.reloj = relojVivero;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(SensingParameters.OFFLINE_RETRY);
    }

    /** {@code true} si la cuenta de la zona la pone fuera de servicio. */
    public static boolean fueraDeServicio(ZonaEntity zona) {
        return cuenta(zona) >= FUERA_DE_SERVICIO;
    }

    /** Pulsos consecutivos sin respuesta; una fila sin valor se lee como 0. */
    public static int cuenta(ZonaEntity zona) {
        Integer n = zona.getPulsosSinRespuesta();
        return n != null ? n : 0;
    }

    /** Un pulso cerró sin respuesta de {@code zonaId}: suma uno a la cuenta y registra la alerta que corresponda. */
    @Transactional
    public void registrarSinRespuesta(String zonaId) {
        ZonaEntity zona = zonaRepository.findById(zonaId).orElse(null);
        if (zona == null) {
            return;   // la topología cambió en medio del pulso
        }
        int n = cuenta(zona) + 1;
        zona.setPulsosSinRespuesta(n);
        zonaRepository.save(zona);

        NivelAlerta nivel = n < FUERA_DE_SERVICIO ? NivelAlerta.WARNING
                : n == FUERA_DE_SERVICIO ? NivelAlerta.CRITICAL : null;
        if (nivel == null) {
            log.debug("Zona {}: {} pulsos seguidos sin respuesta, ya fuera de servicio.", zonaId, n);
            return;
        }
        String texto = nivel == NivelAlerta.CRITICAL
                ? "El nodo testigo de " + zonaId + " no respondió a " + n + " pulsos de sensado seguidos: "
                        + "queda fuera de servicio. " + ordenarEstadoSeguro(zonaId)
                : "El nodo testigo de " + zonaId + " no respondió al pulso de sensado ("
                        + n + (n == 1 ? " pulso" : " pulsos seguidos") + " sin respuesta).";
        historialService.registrarAlerta(zonaId, zona.getName(), REGLA, new DetalleAlerta(nivel, texto), reloj.millis());
        if (nivel == NivelAlerta.CRITICAL) {
            // El pulso que lo declaró caído acaba de pedir lectura: el siguiente pedido es recién en el plazo.
            lastOfflineRequest.put(zonaId, reloj.millis());
        }
    }

    /**
     * S-02: mientras una zona esté fuera de servicio se le vuelve a pedir lectura cada
     * {@code sensado.reintento-fuera-servicio} minutos, fuera de los pulsos. Si responde, la telemetría vuelve su
     * cuenta a 0 ({@link #reiniciar}). Lo llama el tick del planificador; ante una zona que vuelve a caer el plazo
     * arranca de cero.
     */
    public void retryOfflineNodes() {
        if (!overrides.isEnabled(NAME)) {
            return;
        }
        List<ZonaEntity> offline = zonaRepository.findByPulsosSinRespuestaGreaterThanEqual(FUERA_DE_SERVICIO);
        Set<String> ids = offline.stream().map(ZonaEntity::getId).collect(Collectors.toSet());
        lastOfflineRequest.keySet().retainAll(ids);   // las que volvieron se olvidan
        if (offline.isEmpty()) {
            return;
        }
        long now = reloj.millis();
        long intervalMs = (long) (retryIntervalMinutes() * 60_000.0);
        for (ZonaEntity zona : offline) {
            Long last = lastOfflineRequest.get(zona.getId());
            if (last != null && now - last < intervalMs) {
                continue;
            }
            lastOfflineRequest.put(zona.getId(), now);   // también si falla: no se insiste en cada tick
            requestReading(zona.getId());
        }
    }

    /** El override del simulador reemplaza al parámetro del catálogo; 0 elimina la espera. */
    private double retryIntervalMinutes() {
        return overrides.cooldownMinutes(NAME, COOLDOWN_RETRY_OFFLINE)
                .orElseGet(() -> parametros.vigentes().numero(SensingParameters.OFFLINE_RETRY));
    }

    private void requestReading(String zonaId) {
        try {
            ComandoZonaPublisher.Resultado r = zonePublisher.leerAhora(zonaId);
            if (!r.publicado()) {
                log.warn("Zona {}: no se pudo pedir lectura al nodo fuera de servicio ({}).", zonaId, r.error());
            }
        } catch (RuntimeException e) {
            log.warn("Zona {}: falló el pedido de lectura al nodo fuera de servicio. {}", zonaId, e.getMessage());
        }
    }

    /**
     * S-02: al quedar fuera de servicio el nodo, la zona pasa a estado seguro. Se llama una sola vez, al cruzar el
     * umbral. Una falla acá no puede deshacer la cuenta ni la alerta: se informa en el texto de la alerta.
     *
     * @return la frase que la alerta CRITICAL agrega sobre lo que se ordenó
     */
    private String ordenarEstadoSeguro(String zonaId) {
        try {
            ZoneSafeState.Result r = safeState.order(zonaId);
            if (r.commands() == 0 && r.failed() == 0) {
                return "No hay actuadores registrados en la zona a los que ordenarles el estado seguro.";
            }
            String ordenes = r.commands() + (r.commands() == 1 ? " orden" : " órdenes");
            return r.failed() == 0
                    ? "Se ordenó el estado seguro (riego cerrado, bombas detenidas y mediasombra cerrada): " + ordenes + "."
                    : "Estado seguro: " + ordenes + " enviadas y " + r.failed() + " que no se pudieron publicar.";
        } catch (RuntimeException e) {
            log.error("Zona {}: no se pudo ordenar el estado seguro.", zonaId, e);
            return "Atención: no se pudo ordenar el estado seguro de la zona (" + e.getMessage() + ").";
        }
    }

    /**
     * Llegó telemetría de la zona: la cuenta vuelve a 0. Se invoca dentro de la transacción de la ingesta, con la
     * zona ya cargada; no la guarda (lo hace la ingesta). Devuelve {@code true} si la cuenta cambió.
     */
    public boolean reiniciar(ZonaEntity zona) {
        int previa = cuenta(zona);
        if (previa == 0) {
            return false;
        }
        zona.setPulsosSinRespuesta(0);
        if (previa >= FUERA_DE_SERVICIO) {
            historialService.registrarAlerta(zona.getId(), zona.getName(), REGLA,
                    new DetalleAlerta(NivelAlerta.INFO,
                            "El nodo testigo de " + zona.getId() + " volvió a responder."),
                    reloj.millis());
        }
        return true;
    }
}
