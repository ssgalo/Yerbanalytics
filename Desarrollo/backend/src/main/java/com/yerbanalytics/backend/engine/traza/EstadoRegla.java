package com.yerbanalytics.backend.engine.traza;

/** Qué pasó con una regla en una evaluación. */
public enum EstadoRegla {
    /** Se ejecutó: tiene comparaciones y acciones. */
    EVALUADA,
    /** No se ejecutó porque una regla anterior bloqueó su rama. */
    OMITIDA_RAMA_BLOQUEADA,
    /** No se ejecutó porque una regla anterior emitió {@code ABORT_ALL}. */
    NO_ALCANZADA,
    /** Lanzó una excepción al evaluarse: {@code TrazaRegla.error} dice cuál. Las reglas que seguían quedan {@code NO_ALCANZADA}. */
    ERROR
}
