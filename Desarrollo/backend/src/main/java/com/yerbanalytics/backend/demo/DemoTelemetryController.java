package com.yerbanalytics.backend.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo-only: elige qué telemetría usa el backend (nodo real o simulador). Sólo se registra con
 * {@code yerbanalytics.demo.enabled=true}; en producción responde 404.
 */
@RestController
@RequestMapping("/api/demo/telemetry-source")
@ConditionalOnProperty(name = "yerbanalytics.demo.enabled", havingValue = "true")
public class DemoTelemetryController {

    /** Body y respuesta. */
    public record SourceDto(@NotNull TelemetrySource source) {
    }

    private final DemoTelemetrySourceStore store;

    public DemoTelemetryController(DemoTelemetrySourceStore store) {
        this.store = store;
    }

    @GetMapping
    public SourceDto get() {
        return new SourceDto(store.get());
    }

    @PutMapping
    public SourceDto set(@Valid @RequestBody SourceDto body) {
        store.set(body.source());
        return body;
    }
}
