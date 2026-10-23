package com.yerbanalytics.backend.engine.overrides;

import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleEffect;
import com.yerbanalytics.backend.engine.RuleObjective;

import java.util.Optional;
import java.util.Set;

/**
 * Cualquier pieza que decide una acción del sistema: las reglas por sector, las reglas por macro-zona y los servicios
 * que revalidan o evalúan por su cuenta. La prende o apaga el vivero desde Motor de reglas (estado operativo,
 * {@code OperationalRuleStates}) y, sólo en modo demo, la apaga, acelera con cooldowns y bypass, o prende a la fuerza
 * el simulador ({@code RuleOverridesStore}); mientras el override de demo existe, gana sobre el operativo.
 *
 * <p>Qué hace cada una con su estado (consultar {@link RuleOverrides} antes de actuar) es responsabilidad de la pieza.
 * Además de identificarse, declara cómo se presenta: {@link #code()}, {@link #objective()}, {@link #effect()} y, si es
 * una protección, {@link #protection()}.
 *
 * <p>El nombre quedó de cuando sólo la controlaba el simulador; se mantiene para no tocar las piezas que están en
 * revisión y se renombra después de que entren.
 */
public interface DemoControllable {

    /** Nombre técnico, único: es la clave del override y el de la ruta {@code /api/demo/rules/{name}}. */
    String name();

    /**
     * Nombre legible para el agrónomo, sin emojis y sin el código entre paréntesis (eso va en {@link #code()}); la
     * interfaz compone "nombre · código".
     */
    String label();

    /**
     * Código de {@code reglas_v2} ({@code "R-01"}, {@code "S-05"}), o lista separada por coma si la pieza cubre varias.
     * Vacío si la pieza no tiene código en {@code reglas_v2}: no se inventan.
     */
    default String code() {
        return "";
    }

    /** Grupo de la pantalla Motor de reglas. Por defecto sale de la rama; {@code SANITY} lo declaran las F-*. */
    default RuleObjective objective() {
        return RuleObjective.of(branch());
    }

    /** Chip de la tarjeta: actúa, avisa o protege. */
    default RuleEffect effect() {
        return RuleEffect.ACTS;
    }

    /**
     * Si la pieza es una protección: el texto de lo que pasa al apagarla, en lenguaje del agrónomo. Vacío si no lo es.
     * {@code effect() == PROTECTS} si y sólo si hay texto (lo verifica {@code ReglasArquitecturaTest}).
     */
    default Optional<String> protection() {
        return Optional.empty();
    }

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
