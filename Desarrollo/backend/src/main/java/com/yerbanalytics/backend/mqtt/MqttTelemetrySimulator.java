package com.yerbanalytics.backend.mqtt;

import com.yerbanalytics.backend.service.SimulacionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Simulador automático de telemetría: publica ruido periódico a las 6 macro-zonas para dar
 * "vida" de fondo al vivero. Sólo corre cuando el {@link SimulacionService} está en modo
 * simulación y el simulador automático fue activado explícitamente (apagado por defecto para
 * no pisar los datos estáticos ni los envíos manuales). La mecánica MQTT se delega en
 * {@link MqttTelemetryPublisher}.
 */
@Component
public class MqttTelemetrySimulator {

    private final Random random = new Random();
    private final MqttTelemetryPublisher publisher;
    private final SimulacionService simulacionService;

    public MqttTelemetrySimulator(MqttTelemetryPublisher publisher, SimulacionService simulacionService) {
        this.publisher = publisher;
        this.simulacionService = simulacionService;
    }

    @Scheduled(fixedDelayString = "${yerbanalytics.mqtt.simulator.interval-ms}")
    public void publishTelemetry() {
        if (!simulacionService.debeCorrerSimuladorAutomatico()) {
            return;
        }
        // Simulate zones MZ-1 to MZ-6
        for (int i = 1; i <= 6; i++) {
            String zoneId = "MZ-" + i;
            // MAC distinta por macro-zona, para que cada nodo testigo actualice su fila.
            String mac = String.format("A4:CF:12:9A:00:%02d", i);
            // La macro-zona 2 reporta batería baja para ejercitar "Batería Baja" (HU-21 CA-02).
            int battery = i == 2 ? 12 + random.nextInt(8) : 70 + random.nextInt(30);
            int signal = -55 - random.nextInt(35); // dBm: -55 a -89
            MqttTelemetryPayload payload = new MqttTelemetryPayload(
                    mac,
                    battery,
                    signal,
                    System.currentTimeMillis(),
                    new MqttTelemetryPayload.MetricsPayload(
                            38.0 + random.nextDouble() * 25, // humSus (ideal is 42-68)
                            58.0 + random.nextDouble() * 22, // humAmb (ideal is 62-84)
                            17.0 + random.nextDouble() * 11, // temp (ideal is 18-27)
                            0.9 + random.nextDouble() * 0.9,  // ce (ideal is 1.0-1.9)
                            0.5 + random.nextDouble() * 6.5  // uv (ideal is 1.0-6.0)
                    )
            );
            try {
                publisher.publish(zoneId, payload);
            } catch (Exception e) {
                System.err.println("[MqttTelemetrySimulator] Error publishing telemetry: " + e.getMessage());
            }
        }
    }
}
