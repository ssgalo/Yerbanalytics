package com.yerbanalytics.backend.engine.overrides;

/**
 * El estado operativo de una pieza, ya resuelto y de sólo lectura (lo que se cachea).
 *
 * @param label   nombre propio; {@code null} = el de fábrica
 * @param deleted {@code true} si se eliminó de la vista
 */
public record RuleState(String name, boolean active, String reason, String updatedBy, Long updatedTs,
                        String label, boolean deleted) {

    public RuleState(String name, boolean active, String reason, String updatedBy, Long updatedTs) {
        this(name, active, reason, updatedBy, updatedTs, null, false);
    }

    /**
     * {@code true} si una persona decidió el interruptor (prender, apagar, eliminar o restaurar). Una fila creada sólo
     * para el nombre propio no lo decide: rige la siembra.
     */
    public boolean switchDecided() {
        return updatedTs != null;
    }

    static RuleState of(RuleStateEntity row) {
        return new RuleState(row.getNombre(), row.isActiva(), row.getMotivo(), row.getUpdatedBy(), row.getUpdatedTs(),
                row.getEtiqueta(), Boolean.TRUE.equals(row.getEliminada()));
    }
}
