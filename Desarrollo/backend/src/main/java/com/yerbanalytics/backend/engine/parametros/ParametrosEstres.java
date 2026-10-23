package com.yerbanalytics.backend.engine.parametros;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Parámetros del estrés solar de un sector (reglas_v2 §8.5): F-S1 (radiación alta, riego de reposición) y F-S3 (sin
 * radiación alta, sólo aviso). La luz es el % de un LDR, no radiación (CLAUDE.md §2). Entre la luz baja y la alta, y entre
 * la temperatura baja y la alta, queda una zona gris donde ninguna de las dos reglas actúa: es lo que dice la v2.
 * Los rangos los propone el equipo: la v2 fija el valor de fábrica pero no cuánto se puede mover.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ParametrosEstres implements DefinicionParametro {

    HIGH_LIGHT("estres.luz-alta",
            "Luz alta",
            "Con una lectura de luz igual o mayor a esta entre las 10 y las 16 h de las últimas 48 h, el estrés solar se atribuye a la radiación (F-S1).",
            TipoParametro.NUMERO, "%", "80", 60.0, 100.0, 0, "reglas_v2 §8.5 F-S1"),
    HIGH_TEMP("estres.temp-alta",
            "Temperatura alta",
            "Con una temperatura del aire igual o mayor a esta en las últimas 48 h, el estrés solar se atribuye al calor (F-S1).",
            TipoParametro.NUMERO, "°C", "32", 28.0, 36.0, 0, "reglas_v2 §8.5 F-S1"),
    REPLENISH_MOISTURE("estres.humedad-reposicion",
            "Humedad bajo la que se repone el agua",
            "Con estrés solar por radiación, si la humedad de sustrato es menor a esta se hace un riego de reposición del sector.",
            TipoParametro.NUMERO, "%", "55", 45.0, 65.0, 0, "reglas_v2 §8.5 F-S1"),
    LOW_LIGHT("estres.luz-baja",
            "Luz baja",
            "Si en las últimas 48 h la luz no llegó a este valor (y la temperatura tampoco a la baja), el estrés solar no se explica por la radiación (F-S3).",
            TipoParametro.NUMERO, "%", "60", 40.0, 80.0, 0, "reglas_v2 §8.5 F-S3"),
    LOW_TEMP("estres.temp-baja",
            "Temperatura baja",
            "Si en las últimas 48 h la temperatura no llegó a este valor (y la luz tampoco a la baja), el estrés solar no se explica por la radiación (F-S3).",
            TipoParametro.NUMERO, "°C", "30", 25.0, 32.0, 0, "reglas_v2 §8.5 F-S3");

    private final String clave;
    private final String etiqueta;
    private final String descripcion;
    private final TipoParametro tipo;
    private final String unidad;
    private final String fabrica;
    private final Double min;
    private final Double max;
    private final int decimales;
    private final String refSpec;

    @Override
    public FamiliaParametro familia() {
        return FamiliaParametro.ESTRES;
    }
}
