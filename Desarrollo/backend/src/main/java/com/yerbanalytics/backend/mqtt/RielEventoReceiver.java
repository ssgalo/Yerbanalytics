package com.yerbanalytics.backend.mqtt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yerbanalytics.backend.service.PasadaRielService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * Entrada de los eventos del riel ({@code nursery/rail/event}). Sólo deserializa y delega: un
 * JSON roto se loguea y se descarta, nunca tira abajo el adaptador.
 */
@Component
public class RielEventoReceiver {

    private static final Logger log = LoggerFactory.getLogger(RielEventoReceiver.class);

    private final PasadaRielService pasadaService;

    /** Tolera claves desconocidas: una clave nueva del firmware no debe romper la ingesta. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RielEventoReceiver(PasadaRielService pasadaService) {
        this.pasadaService = pasadaService;
    }

    public void processMessage(Message<?> message) {
        try {
            EventoRiel evento = objectMapper.readValue(String.valueOf(message.getPayload()), EventoRiel.class);
            pasadaService.registrarEvento(evento);
        } catch (Exception e) {
            log.warn("Riel: evento MQTT ilegible, se descarta — {}", e.getMessage());
        }
    }
}
