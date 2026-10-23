package com.yerbanalytics.backend.engine;

/**
 * Qué quiere lograr el agrónomo con una pieza: el grupo en que la pantalla Motor de reglas la muestra. Es distinto de
 * la {@link RuleBranch}, que ordena cómo corre el motor, no cómo piensa quien lo opera.
 */
public enum RuleObjective {
    /** Regar: decidir cuándo, cuánto y cuándo no. */
    IRRIGATION,
    /** Nutrir: plan de nutrición, CE baja y aplicación de insumos. */
    NUTRITION,
    /** Sanar: reglas que reaccionan al diagnóstico de IA (plagas, hongos, clorosis, estrés solar). */
    SANITY,
    /** Sombrear: la malla, su horario y su plan de rustificación. */
    SHADE,
    /** Seguimiento: verificar que lo hecho surtió efecto. */
    FOLLOW_UP,
    /** Protecciones transversales: bloqueo manual, frescura y validez de las lecturas, hardware y fallas. */
    PROTECTION;

    /** El objetivo que se infiere de la rama; {@link #SANITY} lo declaran explícitamente las reglas F-*. */
    public static RuleObjective of(RuleBranch branch) {
        return switch (branch) {
            case GLOBAL -> PROTECTION;
            case RIEGO -> IRRIGATION;
            case INSUMO -> NUTRITION;
            case MEDIASOMBRA -> SHADE;
            case SEGUIMIENTO -> FOLLOW_UP;
        };
    }
}
