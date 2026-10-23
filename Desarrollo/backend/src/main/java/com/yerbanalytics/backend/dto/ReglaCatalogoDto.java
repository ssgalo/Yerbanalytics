package com.yerbanalytics.backend.dto;

import java.util.List;

/**
 * Pieza conmutable del motor en el catálogo (una regla por sector, una de macro-zona o un servicio que decide):
 * referencia a sus parámetros por clave, que están una sola vez en {@link CatalogoReglasDto#parametros()}.
 *
 * @param id               nombre técnico de la pieza (el mismo que el id de su nodo en {@code /api/rules/schema})
 * @param label            nombre legible, sin emojis y sin código
 * @param codigo           código de {@code reglas_v2} ({@code "R-01"}); vacío si no tiene
 * @param rama             {@code GLOBAL | RIEGO | INSUMO | MEDIASOMBRA | SEGUIMIENTO}
 * @param objetivo         grupo de la pantalla: {@code IRRIGATION | NUTRITION | SANITY | SHADE | FOLLOW_UP | PROTECTION}
 * @param efecto           chip de la tarjeta: {@code ACTS | WARNS | PROTECTS}
 * @param activa           estado operativo (lo que decidió el vivero): si corre; es la única fuente de verdad del encendido
 * @param activaPorDefecto {@code false} si figura en {@code yerbanalytics.reglas.retiradas}: así arranca sin fila
 * @param proteccion       texto de lo que pasa al apagarla si es una protección; {@code null} si no lo es
 * @param estado           último cambio del interruptor; {@code null} si nunca se cambió
 */
public record ReglaCatalogoDto(
        String id,
        String label,
        String codigo,
        String rama,
        String objetivo,
        String efecto,
        int prioridad,
        List<String> parametros,
        boolean activa,
        boolean activaPorDefecto,
        String proteccion,
        UltimoCambioReglaDto estado
) {}
