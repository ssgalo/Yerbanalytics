package com.yerbanalytics.backend.dto;

/**
 * Seguimiento de efectividad de una acción del historial (E-01…E-03).
 *
 * @param motivo     por qué la evaluación dio efectiva o sin efectividad; {@code null} mientras está en seguimiento
 * @param bloqueando {@code true} si la acción quedó sin efectividad y todavía bloquea su repetición (S-06): es lo que habilita
 *                   «Registrar revisión» en la interfaz
 */
public record Evolution(
        boolean show, String metric, String antes, String ahora, String unit,
        String delta, String latencia, String verdict, String vSoft, String vInk,
        String motivo, boolean bloqueando
) {}
