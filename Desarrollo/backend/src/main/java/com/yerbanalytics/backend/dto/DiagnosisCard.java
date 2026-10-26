package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Tarjeta de la vista de Diagnósticos de IA.
 *
 * <p>{@code thumb} es un gradiente CSS de respaldo, no una imagen. {@code imagenUrl} apunta a
 * la fotografía real cuando el diagnóstico proviene de una captura; viene {@code null} en los
 * diagnósticos que se derivan del estado del sector, y entonces la vista usa el gradiente.
 *
 * <p>{@code creadoEn} es el momento del diagnóstico en ms epoch: el alta, si nació de una
 * captura; la última lectura de la zona, si se deriva del sector. Viene {@code null} cuando la
 * zona nunca reportó. {@code time} es el mismo dato ya redactado ("hace 12 min") y se conserva
 * para quien sólo lo muestra.
 *
 * <p>{@code conf} es el score promedio × 100 ({@code null} en No concluyente) y
 * {@code confidence}, el nivel de confianza. {@code detectionCount} dice cuántas cajas tiene el
 * diagnóstico sin traerlas: se piden aparte a {@code GET /api/diagnosticos/{id}/detecciones}.
 * {@code concluyente} = el estado no es No concluyente. {@code crop} es la región analizada
 * ({@code [x1, y1, x2, y2]} normalizado), para que la miniatura muestre lo mismo que el visor;
 * {@code null} si el diagnóstico no tiene recorte.
 */
public record DiagnosisCard(
        String id, String sectorId, String zonaName, String estado, Double conf,
        String sev, String sevSoft, String sevInk, String thumb, String time, boolean concluyente,
        String imagenUrl, Long creadoEn, String confidence, int detectionCount,
        List<Double> crop
) {}
