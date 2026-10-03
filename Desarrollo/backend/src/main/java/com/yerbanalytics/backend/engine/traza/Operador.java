package com.yerbanalytics.backend.engine.traza;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/** Operador de una comparación registrada en la traza. */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum Operador {

    LT("<"),
    LE("≤"),
    GT(">"),
    GE("≥"),
    EQ("=");

    private final String simbolo;

    /** Aplica el operador al resultado de {@code Double.compare(recibido, umbral)}. */
    boolean cumple(int comparacion) {
        return switch (this) {
            case LT -> comparacion < 0;
            case LE -> comparacion <= 0;
            case GT -> comparacion > 0;
            case GE -> comparacion >= 0;
            case EQ -> comparacion == 0;
        };
    }
}
