package com.yerbanalytics.backend.biotic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lindantes de un sector en la grilla de su macro-zona (F-04)")
class GridNeighborsTest {

    // Grilla de 10 por fila y 100 sectores: la fila r tiene los sectores r*10+1 … r*10+10.

    @Test
    void unSectorInteriorTieneOchoLindantes() {
        // 45 está en la fila 4, columna 4.
        assertThat(GridNeighbors.of(45, 10, 100)).containsExactlyInAnyOrder(34, 35, 36, 44, 46, 54, 55, 56);
    }

    @Test
    void laEsquinaSuperiorIzquierdaTieneTres() {
        assertThat(GridNeighbors.of(1, 10, 100)).containsExactlyInAnyOrder(2, 11, 12);
    }

    @Test
    void laEsquinaSuperiorDerechaNoSaltaALaFilaDeAbajoPorElBorde() {
        // 10 es la última columna: 11 es el primero de la fila siguiente, no su vecino.
        assertThat(GridNeighbors.of(10, 10, 100)).containsExactlyInAnyOrder(9, 19, 20);
    }

    @Test
    void elBordeIzquierdoNoSaltaALaFilaDeArriba() {
        assertThat(GridNeighbors.of(21, 10, 100)).containsExactlyInAnyOrder(11, 12, 22, 31, 32);
    }

    @Test
    void laEsquinaInferiorDerechaTieneTres() {
        assertThat(GridNeighbors.of(100, 10, 100)).containsExactlyInAnyOrder(89, 90, 99);
    }

    @Test
    void laUltimaFilaIncompletaNoTieneVecinosInexistentes() {
        // 25 sectores de a 10: la fila 2 tiene sólo 21…25.
        assertThat(GridNeighbors.of(25, 10, 25)).containsExactlyInAnyOrder(14, 15, 16, 24);
        assertThat(GridNeighbors.of(15, 10, 25)).containsExactlyInAnyOrder(4, 5, 6, 14, 16, 24, 25);
    }

    @Test
    void conUnaSolaColumnaSoloHayArribaYAbajo() {
        assertThat(GridNeighbors.of(3, 1, 5)).containsExactlyInAnyOrder(2, 4);
    }

    @Test
    void conUnaSolaFilaSoloHayIzquierdaYDerecha() {
        assertThat(GridNeighbors.of(3, 5, 5)).containsExactlyInAnyOrder(2, 4);
    }

    @Test
    void unSectorFueraDeLaZonaOUnaGrillaInvalidaNoTienenLindantes() {
        assertThat(GridNeighbors.of(0, 10, 100)).isEmpty();
        assertThat(GridNeighbors.of(101, 10, 100)).isEmpty();
        assertThat(GridNeighbors.of(5, 0, 100)).isEmpty();
    }

    @Test
    void elResultadoVieneOrdenadoYSinElPropioSector() {
        List<Integer> neighbors = GridNeighbors.of(45, 10, 100);
        assertThat(neighbors).isSorted().doesNotContain(45);
    }
}
