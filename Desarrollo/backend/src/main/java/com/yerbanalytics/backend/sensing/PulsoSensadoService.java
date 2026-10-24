package com.yerbanalytics.backend.sensing;

import com.yerbanalytics.backend.config.ZonaHorariaVivero;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.SensingParameters;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.repository.ZonaRepository;
import com.yerbanalytics.backend.service.EscalamientoNodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pulso de sensado (design add-pulso-sensado-y-pasada-diaria D1–D3): pide {@code LEER_AHORA} a todas las
 * macro-zonas y espera la primera telemetría de cada una recibida desde el pedido. Es el único momento en que
 * decide el motor: si el pulso lo ejecuta, esa primera respuesta de la zona dispara la evaluación de sus sectores.
 *
 * <p>La telemetría del contrato no trae {@code commandId}, así que la correlación es por ventana de tiempo, igual
 * que la secuencia {@code LECTURA}. A una zona pendiente se le reenvía el pedido: el primer plazo es
 * {@code sensado.espera-respuesta} y los siguientes {@code sensado.espera-reintento}, hasta
 * {@code sensado.reintentos} reintentos (1 + reintentos pedidos en total); vencido el último plazo queda sin
 * respuesta en ese pulso y cuenta para el escalamiento ({@link EscalamientoNodoService}). Los plazos se leen del
 * catálogo en cada tick, así que un cambio guardado rige en el siguiente.
 *
 * <p>El estado vive en memoria y no se expone: hay a lo sumo un pulso en curso. Las publicaciones salen fuera del
 * lock, porque {@link #respondePulso} corre en el hilo del broker y no puede esperar detrás de un envío MQTT.
 *
 * <p>El catálogo se pide {@code @Lazy}: él mismo se arma con la lista de quienes leen parámetros, y esta clase es una.
 */
@Service
public class PulsoSensadoService implements ConsumidorParametros {

    /** Nombre técnico: dueño de las copias de los plazos {@code sensado.*} (rediseno-motor-reglas D-1). */
    public static final String NAME = "PulsoSensado";

    private static final Logger log = LoggerFactory.getLogger(PulsoSensadoService.class);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** Quién pidió el pulso. */
    public enum OrigenPulso { PROGRAMADO, MANUAL }

    /** Lo que se informa de un pulso al pedirlo. {@code pedidoEn} en epoch ms. */
    public record Pulso(String id, OrigenPulso origen, boolean ejecutaMotor, long pedidoEn) {
    }

    /** Hay un pulso en curso: no se pisan. */
    public static class PulsoEnCursoException extends RuntimeException {
        public PulsoEnCursoException(String mensaje) {
            super(mensaje);
        }
    }

    /** Una zona que todavía no respondió: cuántos pedidos lleva y cuándo salió el último. */
    private static final class ZonaPendiente {
        int pedidos;
        long ultimoPedidoMs;

        ZonaPendiente(long ahoraMs) {
            this.pedidos = 1;
            this.ultimoPedidoMs = ahoraMs;
        }
    }

    private static final class EnCurso {
        final Pulso pulso;
        final Map<String, ZonaPendiente> pendientes = new LinkedHashMap<>();

        EnCurso(Pulso pulso) {
            this.pulso = pulso;
        }
    }

    private final ZonaRepository zonaRepository;
    private final ComandoZonaPublisher publisher;
    private final EscalamientoNodoService escalamiento;
    private final CatalogoParametrosService parametros;
    private final Clock reloj;

    /** Guardado por {@code this}. {@code null} si no hay pulso en curso. */
    private EnCurso enCurso;

    public PulsoSensadoService(ZonaRepository zonaRepository,
                               ComandoZonaPublisher publisher,
                               EscalamientoNodoService escalamiento,
                               @Lazy CatalogoParametrosService parametros,
                               Clock relojVivero) {
        this.zonaRepository = zonaRepository;
        this.publisher = publisher;
        this.escalamiento = escalamiento;
        this.parametros = parametros;
        this.reloj = relojVivero;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(SensingParameters.RESPONSE_WAIT, SensingParameters.RETRIES, SensingParameters.RETRY_WAIT);
    }

    /**
     * Emite un pulso ahora: un {@code LEER_AHORA} a cada macro-zona de la topología. Con una topología vacía no
     * publica nada y el pulso termina en el acto.
     *
     * @throws PulsoEnCursoException si hay otro pulso en curso (no se publica nada)
     */
    public Pulso iniciar(OrigenPulso origen, boolean ejecutaMotor) {
        return iniciar(origen, ejecutaMotor, zonaRepository.findAllIds());
    }

    /**
     * Emite un pulso sólo para {@code zonas} (la secuencia {@code LECTURA} con motor lo pide para la zona del
     * stand). En todo lo demás es el mismo pulso: reintentos, escalamiento y una sola evaluación por zona.
     *
     * @throws PulsoEnCursoException si hay otro pulso en curso (no se publica nada)
     */
    public Pulso iniciar(OrigenPulso origen, boolean ejecutaMotor, List<String> zonas) {
        Pulso pulso;
        synchronized (this) {
            if (enCurso != null) {
                throw new PulsoEnCursoException("Ya hay una lectura de sensores en curso (pedida a las "
                        + hora(enCurso.pulso.pedidoEn()) + "). Esperá a que termine.");
            }
            long ahora = reloj.millis();
            pulso = new Pulso(UUID.randomUUID().toString(), origen, ejecutaMotor, ahora);
            if (!zonas.isEmpty()) {
                EnCurso nuevo = new EnCurso(pulso);
                for (String zona : zonas) {
                    nuevo.pendientes.put(zona, new ZonaPendiente(ahora));
                }
                enCurso = nuevo;
            }
        }
        log.info("Pulso {} ({}, motor={}): se pide lectura a {} zona(s).",
                pulso.id(), origen, ejecutaMotor, zonas.size());
        for (String zona : zonas) {
            pedir(zona);
        }
        return pulso;
    }

    /** {@code true} si hay un pulso en curso. */
    public synchronized boolean enCurso() {
        return enCurso != null;
    }

    /** El pulso en curso, si hay. */
    public synchronized Optional<Pulso> actual() {
        return Optional.ofNullable(enCurso).map(e -> e.pulso);
    }

    /** Lo que significa una telemetría para el pulso en curso: si lo responde y si corresponde ejecutar el motor. */
    public record PulseResponse(boolean responded, boolean runsEngine) {
        /** No responde a ningún pulso (no hay uno, es anterior al pedido o la zona ya respondió). */
        public static final PulseResponse NONE = new PulseResponse(false, false);
    }

    /**
     * La telemetría de {@code zonaId} recibida a {@code recepcionMs}, ¿responde al pulso en curso? Si es la primera
     * de esa zona desde el pedido, la marca como respondida (atómicamente: dos telemetrías simultáneas no responden
     * las dos) y dice si corresponde ejecutar el motor. Una telemetría anterior al pedido, una segunda respuesta
     * o una fuera de pulso devuelven {@link PulseResponse#NONE}.
     *
     * <p>Distingue "respondió a un pulso sin motor" de "no respondió a ninguno", que {@link #respondePulso} no puede:
     * el historial de lecturas necesita saberlo para marcar su origen. Es una sola operación porque marcar la
     * respuesta tiene efecto: consultar dos veces haría que la segunda dijera que no.
     */
    public synchronized PulseResponse recordResponse(String zonaId, long recepcionMs) {
        if (enCurso == null || recepcionMs < enCurso.pulso.pedidoEn()) {
            return PulseResponse.NONE;
        }
        if (enCurso.pendientes.remove(zonaId) == null) {
            return PulseResponse.NONE;
        }
        boolean motor = enCurso.pulso.ejecutaMotor();
        if (enCurso.pendientes.isEmpty()) {
            log.info("Pulso {}: respondieron todas las zonas.", enCurso.pulso.id());
            enCurso = null;
        }
        return new PulseResponse(true, motor);
    }

    /** Sólo si hay que ejecutar el motor; ver {@link #recordResponse} para la respuesta completa. */
    public boolean respondePulso(String zonaId, long recepcionMs) {
        return recordResponse(zonaId, recepcionMs).runsEngine();
    }

    /**
     * Reintentos y cierre: reenvía {@code LEER_AHORA} a las zonas pendientes cuyo plazo venció y cierra sin
     * respuesta las que ya agotaron sus pedidos. El pulso termina cuando no queda ninguna pendiente. Una falla al
     * publicar o al registrar el escalamiento de una zona no frena a las demás.
     */
    public void revisarReintentos() {
        List<String> reenviar = new ArrayList<>();
        List<String> sinRespuesta = new ArrayList<>();
        String pulsoId;
        int intentos;
        synchronized (this) {
            if (enCurso == null) {
                return;
            }
            pulsoId = enCurso.pulso.id();
            long ahora = reloj.millis();
            ParametrosVigentes vigentes = parametros.vigentes().de(this);   // una sola vez: todas las zonas ven los mismos plazos
            long primerPlazoMs = (long) (vigentes.numero(SensingParameters.RESPONSE_WAIT) * 1000L);
            long plazoReintentoMs = (long) (vigentes.numero(SensingParameters.RETRY_WAIT) * 60_000L);
            intentos = 1 + (int) vigentes.numero(SensingParameters.RETRIES);
            var it = enCurso.pendientes.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                ZonaPendiente zp = e.getValue();
                // El primer pedido espera la respuesta; cada reintento, el plazo entre reintentos.
                long plazoMs = zp.pedidos == 1 ? primerPlazoMs : plazoReintentoMs;
                if (ahora - zp.ultimoPedidoMs < plazoMs) {
                    continue;
                }
                if (zp.pedidos < intentos) {
                    zp.pedidos++;
                    zp.ultimoPedidoMs = ahora;
                    reenviar.add(e.getKey());
                } else {
                    it.remove();
                    sinRespuesta.add(e.getKey());
                }
            }
            if (enCurso.pendientes.isEmpty()) {
                enCurso = null;
            }
        }
        for (String zona : reenviar) {
            log.info("Pulso {}: la zona {} no respondió todavía, se reenvía el pedido.", pulsoId, zona);
            pedir(zona);
        }
        for (String zona : sinRespuesta) {
            log.warn("Pulso {}: la zona {} no respondió a ninguno de los {} pedidos.", pulsoId, zona, intentos);
            try {
                escalamiento.registrarSinRespuesta(zona);
            } catch (RuntimeException ex) {
                log.error("Pulso {}: no se pudo registrar el pulso sin respuesta de la zona {}.", pulsoId, zona, ex);
            }
        }
    }

    private void pedir(String zona) {
        try {
            ComandoZonaPublisher.Resultado r = publisher.leerAhora(zona);
            if (!r.publicado()) {
                log.warn("Pulso: no se pudo pedir lectura a la zona {} ({}); se reintenta en el próximo plazo.",
                        zona, r.error());
            }
        } catch (RuntimeException e) {
            log.warn("Pulso: falló el pedido de lectura a la zona {}; se reintenta en el próximo plazo. {}",
                    zona, e.getMessage());
        }
    }

    /** Hora local del vivero ({@code HH:mm}) para los mensajes. */
    private static String hora(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZonaHorariaVivero.ZONA).format(HH_MM);
    }
}
