package com.yerbanalytics.backend.effectiveness;

import com.yerbanalytics.backend.dto.EffectivenessReview;
import com.yerbanalytics.backend.exception.EffectivenessEventNotFoundException;
import com.yerbanalytics.backend.exception.EffectivenessNotBlockingException;
import com.yerbanalytics.backend.exception.InvalidEffectivenessReviewException;
import com.yerbanalytics.backend.model.HistorialEventoEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * La revisión humana de una acción sin efectividad (S-06, reglas_v2 §4): alguien constató en persona qué le pasa al riego, al
 * sector o al cultivo. Es lo único que levanta el bloqueo: baja la marca de todas las acciones del mismo tipo y alcance (el
 * riego, el de la macro-zona; un tratamiento, el del sector) y deja en el historial quién revisó, cuándo y qué constató.
 *
 * <p>Que una verificación posterior sí suba la humedad <b>no</b> levanta el bloqueo: la v2 pide la revisión, no un indicio.
 */
@Service
public class EffectivenessReviewService {

    private final HistorialRepository repository;
    private final HistorialService history;
    private final Clock clock;

    public EffectivenessReviewService(HistorialRepository repository, HistorialService history, Clock clock) {
        this.repository = repository;
        this.history = history;
        this.clock = clock;
    }

    /**
     * @param eventId el evento del historial que quedó «sin efectividad»
     * @param detail  lo que se constató; obligatorio
     * @param author  quien revisa (sale de la sesión)
     * @throws InvalidEffectivenessReviewException  si falta el detalle
     * @throws EffectivenessEventNotFoundException  si el evento no existe
     * @throws EffectivenessNotBlockingException    si el evento no es una acción sin efectividad que siga bloqueando
     */
    @Transactional
    public EffectivenessReview register(String eventId, String detail, String author) {
        String cleanDetail = detail == null ? "" : detail.trim();
        if (cleanDetail.isEmpty()) {
            throw new InvalidEffectivenessReviewException("El detalle de la revisión es obligatorio.");
        }
        HistorialEventoEntity event = repository.findById(eventId)
                .orElseThrow(() -> new EffectivenessEventNotFoundException(eventId));
        EffectivenessKind kind = EffectivenessKind.parse(event.getEfectividadTipo())
                .filter(k -> event.isBloqueoRepeticion())
                .orElseThrow(() -> new EffectivenessNotBlockingException(eventId));

        List<HistorialEventoEntity> blocking = kind.isZoneScoped()
                ? repository.findByZonaIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull(event.getZonaId())
                : repository.findBySectorIdAndBloqueoRepeticionTrueAndEfectividadTipoIsNotNull(event.getSectorId());
        List<HistorialEventoEntity> cleared = blocking.stream()
                .filter(e -> kind.name().equals(e.getEfectividadTipo()))
                .toList();
        cleared.forEach(e -> e.setBloqueoRepeticion(false));
        repository.saveAll(cleared);

        history.registrarRevisionEfectividad(author, event, cleanDetail);
        return new EffectivenessReview(eventId, kind.name(), cleared.size(), clock.millis());
    }
}
