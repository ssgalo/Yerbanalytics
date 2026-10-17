package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.RuleBranch;

import java.util.Set;

/**
 * Cualquier pieza que decide una acción del sistema y que el simulador puede apagar, o acelerar con cooldowns
 * y bypass: las reglas por sector, las reglas por macro-zona y los servicios que revalidan o evalúan por su cuenta.
 *
 * <p>Es lo único que {@code /api/demo/rules} necesita saber de ellas. Qué hace cada una con su override
 * (consultar {@link RuleOverrides} antes de actuar) es responsabilidad de la pieza.
 */
public interface DemoControllable {

    /** Nombre técnico, único: es la clave del override y el de la ruta {@code /api/demo/rules/{name}}. */
    String name();

    /** Etiqueta legible para el operador del simulador. */
    String label();

    RuleBranch branch();

    /** Orden de evaluación (menor = antes). Sólo ordena el listado para las piezas fuera de la cadena por sector. */
    int priority();

    /**
     * Esperas, intervalos, latencias o ventanas de tiempo que se pueden acortar (minutos; {@code 0} la elimina).
     * Se leen del contexto con {@code cooldownOverrideMinutes}.
     */
    default Set<String> cooldownKeys() {
        return Set.of();
    }

    /** Guardas de estado o de franja horaria que se pueden saltear. Se leen del contexto con {@code isBypassed}. */
    default Set<String> bypassKeys() {
        return Set.of();
    }
}
