package com.yerbanalytics.backend.biotic;

import com.yerbanalytics.backend.model.TopologiaLayoutEntity;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import org.springframework.stereotype.Service;

/**
 * Cuántos sectores hay por fila en la grilla de una macro-zona: la disposición configurada en la topología
 * ({@code sectoresPorFila}), de la que {@link GridNeighbors} deduce los lindantes. Si nunca se configuró rige la de fábrica
 * del mapa (10), igual que {@code NurseryService}; siempre queda acotada a la cantidad de sectores de la zona.
 */
@Service
public class SectorGrid {

    private static final int LAYOUT_ID = 1;
    private static final int DEFAULT_PER_ROW = 10;

    private final TopologiaLayoutRepository layouts;

    public SectorGrid(TopologiaLayoutRepository layouts) {
        this.layouts = layouts;
    }

    /** Sectores por fila, entre 1 y {@code sectorsInZone}. */
    public int sectorsPerRow(int sectorsInZone) {
        int configured = layouts.findById(LAYOUT_ID).map(TopologiaLayoutEntity::getSectoresPorFila).orElse(DEFAULT_PER_ROW);
        return Math.max(1, Math.min(configured, Math.max(1, sectorsInZone)));
    }
}
