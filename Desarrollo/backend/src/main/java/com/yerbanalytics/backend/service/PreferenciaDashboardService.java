package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.PreferenciaDashboardEntity;
import com.yerbanalytics.backend.repository.PreferenciaDashboardRepository;
import org.springframework.stereotype.Service;

/** Interruptor "Demo Expo": decide si el dashboard muestra la pestaña. No condiciona al backend. */
@Service
public class PreferenciaDashboardService {

    private final PreferenciaDashboardRepository repo;

    public PreferenciaDashboardService(PreferenciaDashboardRepository repo) {
        this.repo = repo;
    }

    /** Sin fila guardada, oculta. */
    public boolean demoExpoVisible() {
        return repo.findById(PreferenciaDashboardEntity.ID_UNICO)
                .map(PreferenciaDashboardEntity::isDemoExpoVisible)
                .orElse(false);
    }

    public boolean cambiarDemoExpo(boolean visible) {
        PreferenciaDashboardEntity fila = repo.findById(PreferenciaDashboardEntity.ID_UNICO)
                .orElseGet(() -> new PreferenciaDashboardEntity(PreferenciaDashboardEntity.ID_UNICO, false));
        fila.setDemoExpoVisible(visible);
        return repo.save(fila).isDemoExpoVisible();
    }
}
