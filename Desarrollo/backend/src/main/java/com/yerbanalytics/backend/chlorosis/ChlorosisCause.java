package com.yerbanalytics.backend.chlorosis;

/**
 * Las causas de una clorosis que el fertilizante no corrige (F-C1, reglas_v2 §8.5). Cada una dice qué es y qué hay que
 * hacer a mano, que es lo que lee el operador en el aviso.
 */
enum ChlorosisCause {

    ROOT_ASPHYXIA("asfixia radicular", "el sustrato está encharcado"),
    SALINITY("salinidad", "lavar el sustrato con riego abundante, a mano"),
    IRON_CHLOROSIS("clorosis férrica por pH alto", "corregir el pH a mano");

    private final String label;
    private final String remedy;

    ChlorosisCause(String label, String remedy) {
        this.label = label;
        this.remedy = remedy;
    }

    /** Cómo la nombra el operador. */
    String label() {
        return label;
    }

    /** Qué hay que hacer, a mano. */
    String remedy() {
        return remedy;
    }
}
