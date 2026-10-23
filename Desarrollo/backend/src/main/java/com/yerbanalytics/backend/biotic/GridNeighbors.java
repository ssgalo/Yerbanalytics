package com.yerbanalytics.backend.biotic;

import java.util.ArrayList;
import java.util.List;

/**
 * Los sectores lindantes de uno en la grilla de su macro-zona (F-04). Los sectores se numeran de 1 a N dentro de la zona y
 * se disponen de a {@code perRow} por fila (la disposición configurada en la topología).
 *
 * <p><b>Supuesto a validar con el vivero.</b> Esa disposición es visual: ni reglas_v2 ni el código garantizan que
 * refleje la vecindad física. Si la numeración real difiere, hace falta un mapa de adyacencia propio (cambio aparte);
 * mientras tanto este es el único lugar que lo asume.
 */
public final class GridNeighbors {

    private GridNeighbors() {
    }

    /**
     * @param n      número del sector dentro de su zona (1…{@code total})
     * @param perRow sectores por fila de la grilla
     * @param total  sectores de la zona
     * @return los números de los (hasta 8) lindantes que existen, ordenados y sin el propio sector; vacío si el sector o la
     *         grilla no son válidos
     */
    public static List<Integer> of(int n, int perRow, int total) {
        if (perRow < 1 || n < 1 || n > total) {
            return List.of();
        }
        int row = (n - 1) / perRow;
        int column = (n - 1) % perRow;
        List<Integer> neighbors = new ArrayList<>(8);
        for (int dRow = -1; dRow <= 1; dRow++) {
            for (int dColumn = -1; dColumn <= 1; dColumn++) {
                int r = row + dRow;
                int c = column + dColumn;
                boolean self = dRow == 0 && dColumn == 0;
                if (self || r < 0 || c < 0 || c >= perRow) {
                    continue;
                }
                int number = r * perRow + c + 1;
                if (number <= total) {
                    neighbors.add(number);
                }
            }
        }
        return neighbors.stream().sorted().toList();
    }
}
