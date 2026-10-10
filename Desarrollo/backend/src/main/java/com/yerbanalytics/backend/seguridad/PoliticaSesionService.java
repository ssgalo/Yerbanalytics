package com.yerbanalytics.backend.seguridad;

import com.yerbanalytics.backend.seguridad.auditoria.AuditoriaService;
import com.yerbanalytics.backend.seguridad.auditoria.ObjetivoAuditoria;
import com.yerbanalytics.backend.seguridad.auditoria.TipoAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Tiempo máximo de inactividad (HU-01 CA-03). Se consulta en cada petición, así que se cachea en
 * memoria; el cambio rige desde la petición siguiente a que se confirme.
 */
@Service
public class PoliticaSesionService {

    private final PoliticaSesionRepository repo;
    private final AuditoriaService auditoria;

    private volatile Integer inactividadMin;

    public PoliticaSesionService(PoliticaSesionRepository repo, AuditoriaService auditoria) {
        this.repo = repo;
        this.auditoria = auditoria;
    }

    public int inactividadMin() {
        Integer v = inactividadMin;
        if (v == null) {
            v = repo.findById(PoliticaSesionEntity.ID)
                    .map(PoliticaSesionEntity::getInactividadMin)
                    .orElse(PoliticaSesionEntity.INACTIVIDAD_DEFAULT_MIN);
            inactividadMin = v;
        }
        return v;
    }

    @Transactional
    public int cambiar(Integer nuevo) {
        if (nuevo == null || nuevo < PoliticaSesionEntity.INACTIVIDAD_MINIMO
                || nuevo > PoliticaSesionEntity.INACTIVIDAD_MAXIMO) {
            throw new SeguridadExceptions.Invalida("El tiempo máximo de inactividad debe estar entre "
                    + PoliticaSesionEntity.INACTIVIDAD_MINIMO + " y " + PoliticaSesionEntity.INACTIVIDAD_MAXIMO
                    + " minutos.");
        }
        PoliticaSesionEntity p = repo.findById(PoliticaSesionEntity.ID)
                .orElseGet(() -> new PoliticaSesionEntity(PoliticaSesionEntity.ID,
                        PoliticaSesionEntity.INACTIVIDAD_DEFAULT_MIN));
        int anterior = p.getInactividadMin();
        if (anterior == nuevo) {
            return anterior;
        }
        p.setInactividadMin(nuevo);
        repo.save(p);
        auditoria.registrar(TipoAuditoria.POLITICA_SESION_CAMBIADA, ObjetivoAuditoria.POLITICA, "inactividad",
                Map.of("anterior", Map.of("inactividadMin", anterior), "nuevo", Map.of("inactividadMin", nuevo)));
        invalidarAlConfirmar();
        return nuevo;
    }

    /** Crea la fila con el valor por defecto si no existe. No se audita: no cambia nada vigente. */
    @Transactional
    public void asegurarFila() {
        if (!repo.existsById(PoliticaSesionEntity.ID)) {
            repo.save(new PoliticaSesionEntity(PoliticaSesionEntity.ID, PoliticaSesionEntity.INACTIVIDAD_DEFAULT_MIN));
        }
    }

    private void invalidarAlConfirmar() {
        inactividadMin = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    inactividadMin = null;
                }
            });
        }
    }
}
