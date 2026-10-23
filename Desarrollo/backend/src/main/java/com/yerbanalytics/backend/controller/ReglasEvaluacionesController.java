package com.yerbanalytics.backend.controller;

import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaRegla;
import com.yerbanalytics.backend.engine.zone.ZoneTraceStore;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Última evaluación del motor de reglas para un sector: qué recibió cada regla, contra qué
 * umbral y qué decidió. Diagnóstico en vivo, leído de memoria (ver {@link TrazaEvaluacionStore}):
 * no consulta la base de historial. Mismo prefijo que {@link RuleEngineSchemaController}.
 *
 * <p>A las reglas del sector se les suman las de su macro-zona (plan de nutrición): la decisión de la zona es la misma para
 * sus cien sectores, y el Inspector las dibuja en el mismo grafo.
 */
@RestController
@RequestMapping("/api/rules")
public class ReglasEvaluacionesController {

    private final TrazaEvaluacionStore store;
    private final SectorRepository sectorRepository;
    private final ZoneTraceStore zoneTraces;

    public ReglasEvaluacionesController(TrazaEvaluacionStore store, SectorRepository sectorRepository,
                                        ZoneTraceStore zoneTraces) {
        this.store = store;
        this.sectorRepository = sectorRepository;
        this.zoneTraces = zoneTraces;
    }

    /**
     * 200 con la traza; 204 si el sector existe pero todavía no se evaluó (desde el arranque, o
     * desde ese origen); 404 si el sector no existe. Sin {@code origen} devuelve la más reciente.
     */
    @GetMapping("/evaluaciones/{sectorId}")
    public ResponseEntity<TrazaEvaluacion> getEvaluacion(
            @PathVariable String sectorId,
            @RequestParam(required = false) OrigenEvaluacion origen) {
        if (!sectorRepository.existsById(sectorId)) {
            return ResponseEntity.notFound().build();
        }
        return (origen == null ? store.masReciente(sectorId) : store.ultima(sectorId, origen))
                .map(this::withZoneRules)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** La traza del sector más la última de las reglas de su zona, si la zona ya se evaluó. */
    private TrazaEvaluacion withZoneRules(TrazaEvaluacion trace) {
        if (trace.zonaId() == null) {
            return trace;
        }
        return zoneTraces.latest(trace.zonaId()).map(zoneRules -> {
            List<TrazaRegla> all = new ArrayList<>(trace.reglas());
            all.addAll(zoneRules);
            return new TrazaEvaluacion(trace.sectorId(), trace.zonaId(), trace.origen(), trace.ts(),
                    trace.parametrosHash(), all);
        }).orElse(trace);
    }
}
