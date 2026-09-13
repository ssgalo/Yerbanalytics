package com.yerbanalytics.backend.mqtt;

import org.springframework.integration.annotation.MessagingGateway;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.handler.annotation.Header;

/**
 * Gateway de salida MQTT para publicar comandos hacia los nodos actuadores.
 *
 * <p>Spring Integration genera la implementación en tiempo de compilación.
 * El canal de destino es {@code mqttOutboundChannel}, declarado en {@link MqttConfig}.
 *
 * <p>El tópico se especifica por llamada mediante {@code @Header} para que cada
 * acción pueda apuntar al sector correcto sin instanciar múltiples gateways.
 *
 * <p>Uso:
 * <pre>
 *   mqttGateway.send("nursery/zone/MZ-1/sector/MZ-1-001/command", jsonPayload);
 * </pre>
 */
@MessagingGateway(defaultRequestChannel = "mqttOutboundChannel")
public interface MqttCommandGateway {

    /**
     * Publica {@code payload} en el tópico indicado.
     *
     * @param topic   Tópico MQTT destino (ej: {@code nursery/zone/{zona}/sector/{sector}/command}).
     * @param payload JSON serializado del comando (ver {@code contrato.h}).
     */
    void send(@Header(MqttHeaders.TOPIC) String topic, String payload);
}
