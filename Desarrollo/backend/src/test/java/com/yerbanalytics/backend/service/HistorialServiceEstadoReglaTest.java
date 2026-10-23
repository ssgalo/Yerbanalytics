package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
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

/** Prender o apagar una regla queda en el historial como un evento de configuración, con quién, por qué y qué implica. */
@DisplayName("HistorialService.registrarEstadoRegla")
@ExtendWith(MockitoExtension.class)
class HistorialServiceEstadoReglaTest {

    private static final Instant AHORA = Instant.parse("2026-10-23T17:05:00Z");

    @Mock
    private HistorialRepository historialRepository;

    private HistorialEventoEntity registrar(boolean activa, String motivo, String consecuencia) {
        HistorialService service = new HistorialService(historialRepository, kind -> 1_800_000L,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        service.registrarEstadoRegla("Ana López", "SustratoSaturadoRule", "Sustrato saturado", "R-04",
                activa, motivo, consecuencia);

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("apagar una protección: evento Configuración con la regla, el usuario, el motivo y la consecuencia")
    void apagarUnaProteccion() {
        HistorialEventoEntity e = registrar(false, "sonda en recalibración", "Se riega con el sustrato saturado.");

        assertThat(e.getTipo()).isEqualTo("Configuración");
        assertThat(e.getRegla()).isEqualTo("SustratoSaturadoRule");
        assertThat(e.getTs()).isEqualTo(AHORA.toEpochMilli());
        assertThat(e.getLectura()).contains("Sustrato saturado").contains("R-04");
        assertThat(e.getDecision()).containsIgnoringCase("apagada").contains("Ana López")
                .contains("sonda en recalibración");
        assertThat(e.getAccion()).contains("Se riega con el sustrato saturado.");
        assertThat(e.getRes()).isEqualTo("Efectiva");
        assertThat(e.isEvoShow()).isFalse();
        assertThat(e.isBloqueoRepeticion()).isFalse();
    }

    @Test
    @DisplayName("prender: dice 'prendida' y, si no hay motivo, no inventa uno")
    void prender() {
        HistorialEventoEntity e = registrar(true, null, null);

        assertThat(e.getDecision()).containsIgnoringCase("prendida").contains("Ana López").doesNotContain("null");
        assertThat(e.getAccion()).doesNotContain("null");
        assertThat(e.getRegla()).isEqualTo("SustratoSaturadoRule");
    }

    @Test
    @DisplayName("apagar una regla que no es protección: sin consecuencia, pero con el motivo si vino")
    void apagarUnaReglaDeAccion() {
        HistorialEventoEntity e = registrar(false, "prueba de campo", null);

        assertThat(e.getDecision()).contains("prueba de campo");
        assertThat(e.getAccion()).doesNotContain("null").isNotBlank();
    }

    @Test
    @DisplayName("una regla sin código no deja paréntesis vacíos")
    void sinCodigo() {
        HistorialService service = new HistorialService(historialRepository, kind -> 1_800_000L,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        service.registrarEstadoRegla("Ana", "SupplyRule", "Dosificación de insumo", "", false, null, null);

        ArgumentCaptor<HistorialEventoEntity> captor = ArgumentCaptor.forClass(HistorialEventoEntity.class);
        verify(historialRepository).save(captor.capture());
        assertThat(captor.getValue().getLectura()).contains("Dosificación de insumo").doesNotContain("()");
    }
}
