package com.yerbanalytics.backend.engine;

/** Qué hace una pieza cuando decide: lo que dice el chip de su tarjeta (Actúa, Avisa, Protege). */
public enum RuleEffect {
    /** Ordena una acción sobre el vivero (regar, dosificar, mover la malla). */
    ACTS,
    /** Sólo avisa o deja constancia: no toca actuadores. */
    WARNS,
    /** Frena o impide una acción; apagarla pide motivo y confirmación (ver {@code DemoControllable#protection()}). */
    PROTECTS
}
