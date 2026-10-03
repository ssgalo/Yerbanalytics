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

    /**
     * Aplica el operador con los operadores primitivos: {@code -0.0 == 0.0} y cualquier
     * comparación con NaN da {@code false}. ({@code Double.compare} haría lo contrario en ambos casos.)
     */
    boolean cumple(double recibido, double umbral) {
        return switch (this) {
            case LT -> recibido < umbral;
            case LE -> recibido <= umbral;
            case GT -> recibido > umbral;
            case GE -> recibido >= umbral;
            case EQ -> recibido == umbral;
        };
    }
}
