package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** S-05: la reparación de un dispositivo averiado queda en el historial con quién, cuándo y qué se hizo. */
@DisplayName("HistorialService.registrarReparacion")
@ExtendWith(MockitoExtension.class)
class HistorialServiceReparacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-17T13:05:00Z");

    @Mock
    private HistorialRepository historialRepository;
@Mock
    private ConfiguracionService configuracionService;

    private HistorialEventoEntity registrar(String sectorId) {
        HistorialService service = new HistorialService(historialRepository, kind -> 1_800_000L, Clock.fixed(AHORA, ZoneOffset.UTC));

        service.registrarReparacion("Ana", "Electroválvula DEV-101", sectorId, "MZ-1", "Macro-zona 1",
                "SIN_CAUDAL", "Se destapó el emisor");

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("asienta un evento 'Reparación' con el usuario, el dispositivo, la falla y el detalle")
    void asientaElEvento() {
        HistorialEventoEntity e = registrar("MZ-1-012");

        assertThat(e.getTipo()).isEqualTo("Reparación");
        assertThat(e.getTs()).isEqualTo(AHORA.toEpochMilli());
        assertThat(e.getSectorId()).isEqualTo("MZ-1-012");
        assertThat(e.getZonaId()).isEqualTo("MZ-1");
        assertThat(e.getZonaName()).isEqualTo("Macro-zona 1");
        assertThat(e.getLectura()).contains("Electroválvula DEV-101").containsIgnoringCase("sin caudal");
        assertThat(e.getDecision()).contains("Ana").contains("Se destapó el emisor");
        assertThat(e.getAccion()).containsIgnoringCase("autónomo");
        assertThat(e.getRes()).isEqualTo("Efectiva");
        assertThat(e.isEvoShow()).isFalse();
        assertThat(e.isBloqueoRepeticion()).isFalse();
    }

    @Test
    @DisplayName("la reparación de la malla no es de un sector: queda con '—'")
    void laMallaNoTieneSector() {
        assertThat(registrar("—").getSectorId()).isEqualTo("—");
    }
}
