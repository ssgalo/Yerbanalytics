package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.repository.ApplicationRepository;
import com.yerbanalytics.backend.repository.PendingApplicationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Qué fitosanitarios recibió un sector: las que se aplicaron y las que esperan su ventana. Es la única puerta de las reglas
 * bióticas (el foco, el tope de F-06) al registro de aplicaciones; escribir es del {@link ApplicationService}.
 */
@Service
public class FungicideLog {

    private final ApplicationRepository applications;
    private final PendingApplicationRepository pending;

    public FungicideLog(ApplicationRepository applications, PendingApplicationRepository pending) {
        this.applications = applications;
        this.pending = pending;
    }

    /** Cuántos fitosanitarios se le aplicaron al sector desde {@code since}, inclusive. */
    public int appliedSince(String sectorId, Instant since) {
        return (int) applications.countBySectorIdAndSupplyAndTsGreaterThanEqual(sectorId, Supply.FUNGICIDE, since.toEpochMilli());
    }

    /** {@code true} si al sector se le aplicó un fitosanitario desde {@code since}. */
    public boolean treatedSince(String sectorId, Instant since) {
        return appliedSince(sectorId, since) > 0;
    }

    /** {@code true} si un fitosanitario del sector espera la próxima ventana apta. */
    public boolean isPending(String sectorId) {
        return pending.findBySectorIdAndSupply(sectorId, Supply.FUNGICIDE).isPresent();
    }
}
