package com.yerbanalytics.backend.engine;

/**
 * Resultado inmutable que una {@link Rule} devuelve al {@link RuleOrchestrator}.
 *
 * <p>Cada acción lleva:
 * <ul>
 *   <li>{@code type} — qué debe ocurrir (o no ocurrir).</li>
 *   <li>{@code ruleName} — qué regla la produjo (para trazabilidad en el historial).</li>
 *   <li>{@code motivo} — descripción legible que explica la decisión. Se persiste en el
 *       historial como "Decisión" o como causa del Registro de Inacción.</li>
 * </ul>
 */
public record RuleAction(
        ActionType type,
        String ruleName,
        String motivo
) {

    /** Factory para acciones ejecutoras y bloqueantes. */
    public static RuleAction of(ActionType type, String ruleName, String motivo) {
        return new RuleAction(type, ruleName, motivo);
    }

    /** Factory semántica para el Registro de Inacción. */
    public static RuleAction noopInfo(String ruleName, String motivo) {
        return new RuleAction(ActionType.NOOP_INFO, ruleName, motivo);
    }
}
