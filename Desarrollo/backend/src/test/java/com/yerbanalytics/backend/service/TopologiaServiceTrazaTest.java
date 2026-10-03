package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.NuevaTopologia;
import com.yerbanalytics.backend.engine.traza.OrigenEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacion;
import com.yerbanalytics.backend.engine.traza.TrazaEvaluacionStore;
import com.yerbanalytics.backend.repository.DispositivoRepository;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.TopologiaLayoutRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Regenerar la topología reutiliza ids de sector: las trazas viejas no pueden sobrevivir. */
@DisplayName("TopologiaService - trazas")
class TopologiaServiceTrazaTest {

    private TrazaEvaluacionStore store;
    private TopologiaService service;

    @BeforeEach
    void setUp() {
        store = new TrazaEvaluacionStore();
        ZonaRepository zonas = mock(ZonaRepository.class);
        when(zonas.count()).thenReturn(1L);
        TopologiaLayoutRepository layout = mock(TopologiaLayoutRepository.class);
        when(layout.findById(1)).thenReturn(Optional.empty());
        service = new TopologiaService(zonas, mock(SectorRepository.class), mock(DispositivoRepository.class),
                mock(HistorialRepository.class), layout, store);
        store.guardar(new TrazaEvaluacion("MZ-1-001", "MZ-1", OrigenEvaluacion.TELEMETRIA, Instant.EPOCH, "h", List.of()));
    }

    @AfterEach
    void limpiarSincronizacion() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static NuevaTopologia regenerar() {
        return new NuevaTopologia(1, 2, true, 1, 1);
    }

    @Test
    void regenerar_sinTransaccionLimpiaLasTrazasAlTerminar() {
        service.generar(regenerar());

        assertThat(store.masReciente("MZ-1-001")).isEmpty();
    }

    @Test
    void regenerar_conTransaccionLasLimpiaDespuesDelCommitYNoAntes() {
        TransactionSynchronizationManager.initSynchronization();

        service.generar(regenerar());
        assertThat(store.masReciente("MZ-1-001")).isPresent();

        for (TransactionSynchronization s : TransactionSynchronizationManager.getSynchronizations()) {
            s.afterCommit();
        }
        assertThat(store.masReciente("MZ-1-001")).isEmpty();
    }
}
