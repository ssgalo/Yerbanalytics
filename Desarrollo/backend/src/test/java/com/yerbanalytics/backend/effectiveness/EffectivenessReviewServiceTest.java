package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.dto.EffectivenessReview;
import com.yerbanalytics.backend.exception.EffectivenessEventNotFoundException;
import com.yerbanalytics.backend.exception.EffectivenessNotBlockingException;
import com.yerbanalytics.backend.exception.InvalidEffectivenessReviewException;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Revisión humana de una acción sin efectividad (levanta el bloqueo S-06)")
class EffectivenessReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T13:00:00Z");

    private HistorialRepository repository;
    private HistorialService history;
    private EffectivenessReviewService service;

    @BeforeEach
    void setUp() {
        repository = mock(HistorialRepository.class);
        history = mock(HistorialService.class);
        service = new EffectivenessReviewService(repository, history, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static HistorialEventoEntity blocking(String id, EffectivenessKind kind, String sectorId) {
        HistorialEventoEntity e = new HistorialEventoEntity();
        e.setId(id);
        e.setSectorId(sectorId);
        e.setZonaId("MZ-1");
        e.setEfectividadTipo(kind.name());
        e.setBloqueoRepeticion(true);
        e.setEvoVerdict("Sin efectividad");
        return e;
    }

    @Test
    void laRevisionDelRiegoLevantaElBloqueoDeTodaLaZona() {
        HistorialEventoEntity first = blocking("a", EffectivenessKind.IRRIGATION, "MZ-1-010");
        HistorialEventoEntity second = blocking("b", EffectivenessKind.IRRIGATION, "MZ-1-020");
        HistorialEventoEntity fungicide = blocking("c", EffectivenessKind.FUNGICIDE, "MZ-1-010");
        when(repository.findById("a")).thenReturn(Optional.of(first));
        when(repository.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1"))
                .thenReturn(List.of(first, second, fungicide));

        EffectivenessReview review = service.register("a", "  Destapé el emisor  ", "Ana");

        assertThat(first.isBloqueoRepeticion()).isFalse();
        assertThat(second.isBloqueoRepeticion()).isFalse();
        assertThat(fungicide.isBloqueoRepeticion()).as("otro tipo de acción: sigue bloqueando").isTrue();
        assertThat(review).isEqualTo(new EffectivenessReview("a", "IRRIGATION", 2, NOW.toEpochMilli()));
        verify(history).registrarRevisionEfectividad("Ana", first, "Destapé el emisor");
    }

    @Test
    void laRevisionDeUnTratamientoLevantaSoloElBloqueoDeEseSectorYEseTipo() {
        HistorialEventoEntity mine = blocking("a", EffectivenessKind.FUNGICIDE, "MZ-1-045");
        HistorialEventoEntity otherType = blocking("b", EffectivenessKind.CORRECTIVE_FERTILIZER, "MZ-1-045");
        when(repository.findById("a")).thenReturn(Optional.of(mine));
        when(repository.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull("MZ-1-045"))
                .thenReturn(List.of(mine, otherType));

        EffectivenessReview review = service.register("a", "Era otro hongo", "Ana");

        assertThat(mine.isBloqueoRepeticion()).isFalse();
        assertThat(otherType.isBloqueoRepeticion()).isTrue();
        assertThat(review.clearedEvents()).isEqualTo(1);
    }

    @Test
    void sinDetalleSeRechazaYNoSeToca() {
        assertThatThrownBy(() -> service.register("a", "   ", "Ana")).isInstanceOf(InvalidEffectivenessReviewException.class);
        assertThatThrownBy(() -> service.register("a", null, "Ana")).isInstanceOf(InvalidEffectivenessReviewException.class);

        verifyNoInteractions(history);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void unEventoQueNoExisteEsNotFound() {
        when(repository.findById("zz")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register("zz", "x", "Ana")).isInstanceOf(EffectivenessEventNotFoundException.class);
    }

    @Test
    void unEventoQueNoBloqueaNoSeRevisa() {
        HistorialEventoEntity effective = blocking("a", EffectivenessKind.IRRIGATION, "MZ-1-010");
        effective.setBloqueoRepeticion(false);
        when(repository.findById("a")).thenReturn(Optional.of(effective));

        assertThatThrownBy(() -> service.register("a", "x", "Ana")).isInstanceOf(EffectivenessNotBlockingException.class);
        verifyNoInteractions(history);
    }

    @Test
    void unEventoSinTipoDeEfectividadNoSeRevisa() {
        HistorialEventoEntity legacy = blocking("a", EffectivenessKind.IRRIGATION, "MZ-1-010");
        legacy.setEfectividadTipo(null);
        when(repository.findById("a")).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.register("a", "x", "Ana")).isInstanceOf(EffectivenessNotBlockingException.class);
    }
}
