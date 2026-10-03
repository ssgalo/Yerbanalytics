package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Tarea 2.3: el overload de {@code registrarConfiguracion} con detalle. */
@DisplayName("HistorialService.registrarConfiguracion(usuario, detalle)")
@ExtendWith(MockitoExtension.class)
class HistorialServiceConfiguracionTest {

    @Mock
    private HistorialRepository historialRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private ConfiguracionService configuracionService;

    @Test
    void asientaUnEventoConfiguracionConElUsuarioYElDetalle() {
        HistorialService service = new HistorialService(historialRepository, sectorRepository,
                configuracionService, 120000, "2 min", 5);

        service.registrarConfiguracion("Ana", "Parámetros de reglas modificados: riego.umbral-humedad.");

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        HistorialEventoEntity e = captor.getValue();
        assertThat(e.getTipo()).isEqualTo("Configuración");
        assertThat(e.getLectura()).contains("Ana");
        assertThat(e.getAccion()).contains("riego.umbral-humedad");
        assertThat(e.getSectorId()).isEqualTo("—");
        assertThat(e.isEvoShow()).isFalse();
    }
}
