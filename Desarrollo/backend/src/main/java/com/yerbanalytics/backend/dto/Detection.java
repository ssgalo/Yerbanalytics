package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Una detección del modelo que intervino en el diagnóstico.
 *
 * @param label estado de la plataforma de la caja (Clorosis, Sano, …)
 * @param score score crudo del modelo, en {@code [0, 1]}
 * @param box   caja normalizada {@code [x1, y1, x2, y2]} ({@code xyxyn}), independiente de la
 *              resolución de la imagen
 */
public record Detection(String label, Double score, List<Double> box) {
}
