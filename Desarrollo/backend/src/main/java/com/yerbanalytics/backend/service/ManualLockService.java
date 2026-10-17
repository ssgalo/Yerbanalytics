package com.yerbanalytics.backend.service;

import com.yerbanalytics.backend.dto.ManualLock;
import com.yerbanalytics.backend.dto.NewManualLock;
import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.engine.riego.ColaRiego;
import com.yerbanalytics.backend.engine.riego.SolicitudRiego;
import com.yerbanalytics.backend.exception.InvalidManualLockException;
import com.yerbanalytics.backend.exception.ManualLockConflictException;
import com.yerbanalytics.backend.exception.ManualLockNotFoundException;
import com.yerbanalytics.backend.model.ManualLockEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.repository.ManualLockRepository;
import com.yerbanalytics.backend.repository.SectorRepository;
import com.yerbanalytics.backend.repository.ZonaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bloqueo manual de la actuación autónoma (HU-19, reglas_v2 S-01).
 *
 * <p><b>Un bloqueo es de un sector o de una macro-zona, nunca de los dos</b>: es lo que suponen
 * {@code NurseryService} y {@code DespachoRiego}, que tratan cualquier bloqueo con {@code zonaId} como bloqueo de
 * toda la zona. Por eso uno de sector se guarda con {@code zonaId = null}; la respuesta sí informa su zona.
 *
 * <p><b>Al activar</b> se cancela el riego encolado del alcance y se ordena cerrar las válvulas y detener las
 * bombas (la mediasombra no se mueve: queda como está). Esos efectos no condicionan el bloqueo: si el broker o el
 * historial fallan, el bloqueo ya está guardado y el motor deja de actuar. <b>Reanudar</b> sólo desactiva (conserva
 * quién y cuándo) y no repone nada: decide la próxima lectura.
 */
@Service
public class ManualLockService {

    private static final Logger log = LoggerFactory.getLogger(ManualLockService.class);

    /** Regla a la que se atribuyen las alertas del bloqueo. */
    private static final String RULE_NAME = "ManualLockRule";

    /** Los equipos que se detienen al bloquear. */
    private static final Set<String> STOPPED_TYPES = Set.of(HardwareService.ELECTROVALVULA, HardwareService.BOMBA);

    private final ManualLockRepository locks;
    private final SectorRepository sectors;
    private final ZonaRepository zones;
    private final ActuatorShutdown shutdown;
    private final ColaRiego queue;
    private final HistorialService history;
    private final Clock clock;

    public ManualLockService(ManualLockRepository locks, SectorRepository sectors, ZonaRepository zones,
                             ActuatorShutdown shutdown, ColaRiego queue, HistorialService history, Clock clock) {
        this.locks = locks;
        this.sectors = sectors;
        this.zones = zones;
        this.shutdown = shutdown;
        this.queue = queue;
        this.history = history;
        this.clock = clock;
    }

    /**
     * Bloqueos vigentes, del más nuevo al más viejo. {@code zoneId} deja los de esa zona y los de sus sectores;
     * {@code sectorId}, sólo los de ese sector. Ambos son opcionales.
     */
    public List<ManualLock> list(String zoneId, String sectorId) {
        return locks.findByActiveTrueOrderByActivatedAtDesc().stream()
                .map(this::toDto)
                .filter(l -> zoneId == null || zoneId.equals(l.zonaId()))
                .filter(l -> sectorId == null || sectorId.equals(l.sectorId()))
                .toList();
    }

    public ManualLock activate(NewManualLock request, String user) {
        if (request == null || isBlank(request.zonaId())) {
            throw new InvalidManualLockException("La macro-zona es obligatoria.");
        }
        if (isBlank(request.reason())) {
            throw new InvalidManualLockException("El motivo del bloqueo es obligatorio.");
        }
        String zoneId = request.zonaId().trim();
        String sectorId = isBlank(request.sectorId()) ? null : request.sectorId().trim();
        ZonaEntity zone = zones.findById(zoneId)
                .orElseThrow(() -> new InvalidManualLockException("La macro-zona " + zoneId + " no existe."));
        if (sectorId != null) {
            validateSector(sectorId, zoneId);
        }
        rejectDuplicate(zoneId, sectorId);

        ManualLockEntity entity = new ManualLockEntity();
        entity.setSectorId(sectorId);
        entity.setZonaId(sectorId == null ? zoneId : null);
        entity.setActivatedBy(user);
        entity.setActivatedAt(clock.millis());
        entity.setReason(request.reason().trim());
        entity.setActive(true);
        ManualLockEntity saved = locks.save(entity);

        stopActuation(zoneId, sectorId);
        alert(zone, String.format("%s bloqueó %s. Motivo: %s. El motor no actuará hasta que se reanude.",
                user, scopeText(zoneId, sectorId), saved.getReason()));
        return toDto(saved);
    }

    public void release(Long id, String user) {
        ManualLockEntity entity = locks.findById(id)
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .orElseThrow(() -> new ManualLockNotFoundException("No hay un bloqueo vigente con id " + id + "."));
        entity.setActive(false);
        locks.save(entity);

        String zoneId = zoneOf(entity);
        zones.findById(zoneId).ifPresent(zone -> alert(zone, String.format(
                "%s reanudó la actuación autónoma de %s. La próxima lectura decide.",
                user, scopeText(zoneId, entity.getSectorId()))));
    }

    // ── Efectos ──────────────────────────────────────────────────────────────

    private void stopActuation(String zoneId, String sectorId) {
        List<String> sectorIds = sectorId != null
                ? List.of(sectorId)
                : sectors.findByZonaId(zoneId).stream().map(SectorEntity::getId).toList();
        // Primero la cola: así el despacho no abre una válvula mientras se ordena cerrar la anterior.
        if (sectorId != null) {
            queue.retirar(zoneId, sectorId);
        } else {
            queue.pendientes(zoneId).forEach(this::dequeue);
        }
        try {
            shutdown.close(zoneId, sectorIds, STOPPED_TYPES);
        } catch (RuntimeException e) {
            log.error("Bloqueo manual en {}: no se pudo ordenar el cierre de los actuadores.", zoneId, e);
        }
    }

    private void dequeue(SolicitudRiego s) {
        queue.retirar(s.zonaId(), s.sectorId());
    }

    private void alert(ZonaEntity zone, String text) {
        try {
            history.registrarAlerta(zone.getId(), zone.getName(), RULE_NAME,
                    new DetalleAlerta(NivelAlerta.INFO, text), clock.millis());
        } catch (RuntimeException e) {
            log.error("Bloqueo manual en {}: no se pudo registrar la alerta en el historial.", zone.getId(), e);
        }
    }

    // ── Validación y mapeo ───────────────────────────────────────────────────

    private void validateSector(String sectorId, String zoneId) {
        SectorEntity sector = sectors.findById(sectorId)
                .orElseThrow(() -> new InvalidManualLockException("El sector " + sectorId + " no existe."));
        if (sector.getZona() == null || !zoneId.equals(sector.getZona().getId())) {
            throw new InvalidManualLockException("El sector " + sectorId + " no pertenece a la macro-zona " + zoneId + ".");
        }
    }

    private void rejectDuplicate(String zoneId, String sectorId) {
        boolean exists = sectorId != null
                ? !locks.findBySectorIdAndActiveTrue(sectorId).isEmpty()
                : !locks.findByZonaIdAndActiveTrue(zoneId).isEmpty();
        if (exists) {
            throw new ManualLockConflictException(
                    capitalize(scopeText(zoneId, sectorId)) + " ya tiene un bloqueo vigente.");
        }
    }

    private ManualLock toDto(ManualLockEntity e) {
        return new ManualLock(e.getId(), zoneOf(e), e.getSectorId(), e.getActivatedBy(), e.getActivatedAt(),
                e.getReason());
    }

    /** La zona del bloqueo: la propia, o la del sector si es un bloqueo de sector. */
    private String zoneOf(ManualLockEntity e) {
        if (e.getZonaId() != null) {
            return e.getZonaId();
        }
        Optional<SectorEntity> sector = e.getSectorId() == null ? Optional.empty() : sectors.findById(e.getSectorId());
        return sector.map(SectorEntity::getZona).map(ZonaEntity::getId).orElse(null);
    }

    private static String scopeText(String zoneId, String sectorId) {
        return sectorId != null ? "el sector " + sectorId : "la macro-zona " + zoneId;
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
