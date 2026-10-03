package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.HistorialEvento;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Tarea 3.6: el DTO del historial expone regla, alerta, volumen y duración (aditivo). */
@DisplayName("HistorialEvento (DTO)")
@ExtendWith(MockitoExtension.class)
class HistorialServiceDtoTest {

    @Mock
    private HistorialRepository historialRepository;
    @Mock
    private SectorRepository sectorRepository;
    @Mock
    private ConfiguracionService configuracionService;

    private HistorialEventoEntity entidad() {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId("e1");
        e.setSectorId("MZ-2-001");
        e.setZonaId("MZ-2");
        e.setZonaName("Macro-zona 2");
        e.setTipo("Riego");
        e.setTs(System.currentTimeMillis());
        e.setLectura("l");
        e.setDecision("d");
        e.setAccion("a");
        e.setRes("Efectiva");
        e.setSev("—");
        return e;
    }

    private HistorialService service() {
        return new HistorialService(historialRepository, sectorRepository, configuracionService, 120000, "2 min", 5);
    }

    @Test
    void exponeLosCuatroCamposNuevos() {
        HistorialEventoEntity e = entidad();
        e.setRegla("RiegoPorDeficitRule");
        e.setAlerta("INFO");
        e.setVolumenL(4.2);
        e.setDuracionSeg(504);
        when(historialRepository.findFiltered(any(), any(), any(), any(), any())).thenReturn(List.of(e));

        HistorialEvento dto = service().getHistorial(null, null, null, null, null).get(0);

        assertThat(dto.regla()).isEqualTo("RiegoPorDeficitRule");
        assertThat(dto.alerta()).isEqualTo("INFO");
        assertThat(dto.volumenL()).isEqualTo(4.2);
        assertThat(dto.duracionSeg()).isEqualTo(504);
    }

    @Test
    void sinDatosLosCamposNuevosSonNulos() {
        when(historialRepository.findFiltered(any(), any(), any(), any(), any())).thenReturn(List.of(entidad()));

        HistorialEvento dto = service().getHistorial(null, null, null, null, null).get(0);

        assertThat(dto.regla()).isNull();
        assertThat(dto.alerta()).isNull();
        assertThat(dto.volumenL()).isNull();
        assertThat(dto.duracionSeg()).isNull();
    }
}
