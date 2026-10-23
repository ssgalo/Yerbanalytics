package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.dto.FungicideReview;
import com.yerbanalytics.backend.exception.InvalidFungicideReviewException;
import com.yerbanalytics.backend.exception.ReviewSectorNotFoundException;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("F-06 · revisión de un sector que alcanzó el tope de fitosanitarios")
class FungicideReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T13:00:00Z");

    private SectorRepository sectors;
    private HistorialService history;
    private FungicideCap cap;
    private SectorEntity sector;
    private FungicideReviewService service;

    @BeforeEach
    void setUp() {
        sectors = mock(SectorRepository.class);
        history = mock(HistorialService.class);
        cap = mock(FungicideCap.class);
        ZonaEntity zone = new ZonaEntity();
        zone.setId("MZ-1");
        sector = new SectorEntity();
        sector.setId("MZ-1-045");
        sector.setZona(zone);
        when(sectors.findById("MZ-1-045")).thenReturn(Optional.of(sector));
        service = new FungicideReviewService(sectors, history, cap, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void laRevisionDejaLaHoraEnElSectorYEnElHistorial() {
        FungicideReview review = service.register("MZ-1-045", "  Revisé las hojas: es otro hongo  ", "Ana");

        assertThat(review).isEqualTo(new FungicideReview("MZ-1-045", NOW.toEpochMilli()));
        assertThat(sector.getFungicideReviewTs()).isEqualTo(NOW.toEpochMilli());
        verify(sectors).save(sector);
        verify(history).registrarRevisionFitosanitaria("Ana", sector, "Revisé las hojas: es otro hongo");
    }

    @Test
    void levantaElBloqueoYPermiteAvisarDeNuevoSiVuelveAAlcanzarElTope() {
        service.register("MZ-1-045", "ok", "Ana");

        verify(cap).clearReport("MZ-1-045");
    }

    @Test
    void sinDetalleNoHayRevision() {
        assertThatThrownBy(() -> service.register("MZ-1-045", "   ", "Ana"))
                .isInstanceOf(InvalidFungicideReviewException.class)
                .hasMessageContaining("detalle");
        assertThatThrownBy(() -> service.register("MZ-1-045", null, "Ana"))
                .isInstanceOf(InvalidFungicideReviewException.class);

        verify(sectors, never()).save(any());
        verifyNoInteractions(history, cap);
    }

    @Test
    void unSectorQueNoExisteNoSeRevisa() {
        when(sectors.findById("MZ-9-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register("MZ-9-001", "ok", "Ana"))
                .isInstanceOf(ReviewSectorNotFoundException.class)
                .hasMessageContaining("MZ-9-001");
        verifyNoInteractions(history, cap);
    }
}
