package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.dto.FungicideReview;
import com.yerbanalytics.backend.exception.InvalidFungicideReviewException;
import com.yerbanalytics.backend.exception.ReviewSectorNotFoundException;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Registra la revisión de un sector que alcanzó el tope de fitosanitarios (F-06, reglas_v2 §8.5): alguien constató en
 * persona qué le pasa al sector. Es lo único que levanta el bloqueo: desde la hora de la revisión el conteo del tope
 * ({@link FungicideCap}) vuelve a cero y el sector puede recibir fitosanitarios otra vez.
 *
 * <p>Queda en el historial del sector quién revisó, cuándo y qué constató, igual que el registro de una reparación.
 */
@Service
public class FungicideReviewService {

    private final SectorRepository sectors;
    private final HistorialService history;
    private final FungicideCap cap;
    private final Clock clock;

    public FungicideReviewService(SectorRepository sectors, HistorialService history, FungicideCap cap, Clock clock) {
        this.sectors = sectors;
        this.history = history;
        this.cap = cap;
        this.clock = clock;
    }

    /**
     * @param detail lo que se constató; obligatorio
     * @param author quien revisa (sale de la sesión)
     * @throws InvalidFungicideReviewException si falta el detalle
     * @throws ReviewSectorNotFoundException   si el sector no existe
     */
    @Transactional
    public FungicideReview register(String sectorId, String detail, String author) {
        String cleanDetail = detail == null ? "" : detail.trim();
        if (cleanDetail.isEmpty()) {
            throw new InvalidFungicideReviewException("El detalle de la revisión es obligatorio.");
        }
        SectorEntity sector = sectors.findById(sectorId).orElseThrow(() -> new ReviewSectorNotFoundException(sectorId));

        long now = clock.millis();
        sector.setFungicideReviewTs(now);
        sectors.save(sector);
        cap.clearReport(sectorId);
        history.registrarRevisionFitosanitaria(author, sector, cleanDetail);
        return new FungicideReview(sectorId, now);
    }
}
