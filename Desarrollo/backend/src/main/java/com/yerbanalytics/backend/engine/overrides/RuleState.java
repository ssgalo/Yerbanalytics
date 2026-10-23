package com.yerbanalytics.backend.engine.overrides;

/** El estado operativo de una pieza, ya resuelto y de sólo lectura (lo que se cachea). */
public record RuleState(String name, boolean active, String reason, String updatedBy, Long updatedTs) {

    static RuleState of(RuleStateEntity row) {
        return new RuleState(row.getNombre(), row.isActiva(), row.getMotivo(), row.getUpdatedBy(), row.getUpdatedTs());
    }
}
