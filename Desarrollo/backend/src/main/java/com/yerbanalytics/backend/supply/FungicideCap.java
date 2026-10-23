package com.yerbanalytics.backend.supply;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * El tope de fitosanitarios por sector (F-06, reglas_v2 §8.5): con un solo producto, repetirlo arriesga resistencia. Cuenta
 * lo que el sector recibió en la ventana del tope, <b>desde la última revisión</b> si la hubo, y avisa al operador cuando
 * una aplicación se rechaza por eso. Decidir el rechazo es de {@link ApplicationPreconditions}; esto sólo aporta el conteo
 * y el aviso.
 *
 * <p><b>Un aviso por bloqueo.</b> El sector sigue bloqueado hasta la revisión, y cada pedido nuevo lo rechaza otra vez:
 * avisar en cada uno sería repetir el mismo evento. La memoria del aviso es de proceso (tras un reinicio puede repetirse una
 * vez) y se olvida al registrar la revisión ({@link #clearReport}).
 */
@Service
public class FungicideCap {

    /** Regla con la que queda el aviso en el historial de la macro-zona. */
    public static final String ALERT_RULE = "FungicideCap";

    private static final long MINUTES_PER_DAY = 24L * 60;

    private final FungicideLog fungicides;
    private final HistorialService history;
    private final Clock clock;
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    public FungicideCap(FungicideLog fungicides, HistorialService history, Clock clock) {
        this.fungicides = fungicides;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Cuántos fitosanitarios recibió el sector en los últimos {@code days} días, sin contar los anteriores a su última
     * revisión.
     */
    public int appliedInWindow(SectorEntity sector, Instant now, double days) {
        Instant windowStart = now.minus(Duration.ofMinutes(Math.round(days * MINUTES_PER_DAY)));
        Long review = sector.getFungicideReviewTs();
        Instant since = review != null && review > windowStart.toEpochMilli() ? Instant.ofEpochMilli(review) : windowStart;
        return fungicides.appliedSince(sector.getId(), since);
    }

    /** Avisa a la macro-zona que el sector alcanzó el tope, una sola vez hasta que se registre la revisión. */
    public void reportReached(SectorEntity sector, String reason) {
        if (!reported.add(sector.getId())) {
            return;
        }
        ZonaEntity zone = sector.getZona();
        String text = String.format("Tope de fitosanitarios alcanzado en %s: %s.", sector.getId(), reason);
        history.registrarAlerta(zone.getId(), zone.getName(), ALERT_RULE, new DetalleAlerta(NivelAlerta.WARNING, text),
                clock.millis());
    }

    /** La revisión levantó el bloqueo: si el sector vuelve a alcanzar el tope, se vuelve a avisar. */
    public void clearReport(String sectorId) {
        reported.remove(sectorId);
    }
}
