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
                    // Valores en unidades del CONTRATO, como los publicaría el nodo real
                    // (ver ContratoNodo): ce en µS/cm, uv en % de luz del LDR.
                    new MqttTelemetryPayload.MetricsPayload(
                            38.0 + random.nextDouble() * 25,   // humSus  (ideal 42-68 %)
                            58.0 + random.nextDouble() * 22,   // humAmb  (ideal 62-84 %)
                            17.0 + random.nextDouble() * 11,   // temp    (ideal 18-27 °C)
                            15.0 + random.nextDouble() * 10,   // tempSuelo (ideal 16-24 °C)
                            30.0 + random.nextDouble() * 50,   // uv      (ideal 35-70 %)
                            900.0 + random.nextDouble() * 900, // ce      (ideal 1,0-1,9 dS/m)
                            4.8 + random.nextDouble() * 1.4,   // phSuelo (ideal 5,0-6,0)
                            90.0 + random.nextDouble() * 120,  // n       (ideal 100-200 mg/kg)
                            25.0 + random.nextDouble() * 40,   // p       (ideal 30-60 mg/kg)
                            110.0 + random.nextDouble() * 140  // k       (ideal 120-240 mg/kg)
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
