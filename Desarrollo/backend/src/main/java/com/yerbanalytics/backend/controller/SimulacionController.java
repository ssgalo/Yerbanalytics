package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.dto.EnvioTelemetria;
import com.yerbanalytics.backend.dto.SensorSimulado;
import com.yerbanalytics.backend.dto.SimulacionEstado;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPayload;
import com.yerbanalytics.backend.mqtt.MqttTelemetryPublisher;
import com.yerbanalytics.backend.service.SimulacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Dashboard de simulación: conmuta el modo de operación del vivero (estático/simulación) y
 * envía telemetría manual por MQTT real, atravesando el mismo pipeline de ingesta que el
 * hardware. El envío sólo se acepta en modo simulación (409 en estático); un dato inválido
 * responde 400 y una falla al publicar 502.
 */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

    private final SimulacionService simulacionService;
    private final MqttTelemetryPublisher publisher;

    public SimulacionController(SimulacionService simulacionService, MqttTelemetryPublisher publisher) {
        this.simulacionService = simulacionService;
        this.publisher = publisher;
    }

    /** Actualización parcial del estado: modo y/o simulador automático. */
    public record ActualizarEstado(String modo, Boolean autoSimulador) {}

    @GetMapping
    public ResponseEntity<SimulacionEstado> getEstado() {
        return ResponseEntity.ok(estado());
    }

    @PutMapping
    public ResponseEntity<SimulacionEstado> actualizar(@RequestBody ActualizarEstado body) {
        if (body.modo() != null) {
            simulacionService.setModo(SimulacionService.Modo.fromValue(body.modo()));
        }
        if (body.autoSimulador() != null) {
            simulacionService.setAutoSimuladorActivo(body.autoSimulador());
        }
        return ResponseEntity.ok(estado());
    }

    // ------------------------------------------------------------------
    // Sensores simulados (desacoplados del registro de hardware)
    // ------------------------------------------------------------------

    /** Alta de un sensor simulado: serial/MAC + macro-zona. */
    public record NuevoSensor(String serial, String zonaId) {}

    @GetMapping("/sensores")
    public ResponseEntity<List<SensorSimulado>> listarSensores() {
        return ResponseEntity.ok(simulacionService.listarSensores());
    }

    @PostMapping("/sensores")
    public ResponseEntity<SensorSimulado> crearSensor(@RequestBody NuevoSensor body) {
        SensorSimulado creado = simulacionService.crearSensor(
                body != null ? body.serial() : null,
                body != null ? body.zonaId() : null);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @DeleteMapping("/sensores")
    public ResponseEntity<Void> eliminarSensor(@RequestParam("serial") String serial) {
        simulacionService.eliminarSensor(serial);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/telemetria")
    public ResponseEntity<Map<String, Object>> enviarTelemetria(@RequestBody EnvioTelemetria envio) {
        if (!simulacionService.isSimulacion()) {
            throw new SimulacionInactivaException(
                    "La simulación está inactiva. Activá el modo simulación para enviar lecturas.");
        }
        String serial = trim(envio.serial());
        if (serial == null) {
            throw new IllegalArgumentException("El serial/MAC del sensor es obligatorio.");
        }
        String zonaId = trim(envio.zonaId());
        if (zonaId == null) {
            throw new IllegalArgumentException("La macro-zona del sensor es obligatoria.");
        }
        EnvioTelemetria.Metrics m = envio.metrics();
        // Métricas parciales: cada una es opcional, pero debe venir al menos una (permite
        // enviar una sola, p. ej. sólo radiación). La ingesta conserva las ausentes.
        if (m == null || (m.humSus() == null && m.humAmb() == null && m.temp() == null
                && m.ce() == null && m.uv() == null)) {
            throw new IllegalArgumentException("Enviá al menos una métrica (humSus, humAmb, temp, ce o uv).");
        }

        long ts = envio.timestamp() != null ? envio.timestamp() : System.currentTimeMillis();
        MqttTelemetryPayload payload = new MqttTelemetryPayload(
                serial,
                envio.battery(),
                envio.signal(),
                ts,
                new MqttTelemetryPayload.MetricsPayload(m.humSus(), m.humAmb(), m.temp(), m.ce(), m.uv())
        );

        try {
            publisher.publish(zonaId, payload);
        } catch (Exception e) {
            throw new PublicacionFallidaException(
                    "No se pudo publicar en el broker MQTT (" + e.getMessage()
                            + "). Verificá que el broker esté disponible.");
        }

        return ResponseEntity.accepted().body(Map.of(
                "zonaId", zonaId,
                "serial", serial,
                "timestamp", ts
        ));
    }

    private SimulacionEstado estado() {
        return new SimulacionEstado(
                simulacionService.getModo().value(),
                simulacionService.isAutoSimuladorActivo()
        );
    }

    private static String trim(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------
    // Errores → códigos HTTP
    // ------------------------------------------------------------------

    /** Envío pedido con la simulación apagada → 409. */
    static class SimulacionInactivaException extends RuntimeException {
        SimulacionInactivaException(String msg) { super(msg); }
    }

    /** Falla al publicar en el broker → 502. */
    static class PublicacionFallidaException extends RuntimeException {
        PublicacionFallidaException(String msg) { super(msg); }
    }

    /** Modo inválido o dato faltante → 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalido(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(SimulacionInactivaException.class)
    public ResponseEntity<Map<String, String>> handleInactiva(SimulacionInactivaException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PublicacionFallidaException.class)
    public ResponseEntity<Map<String, String>> handlePublicacion(PublicacionFallidaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }
}
