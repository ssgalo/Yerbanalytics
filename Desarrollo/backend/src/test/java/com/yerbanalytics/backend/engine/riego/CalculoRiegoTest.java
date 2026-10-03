package com.yerbanalytics.backend.engine.riego;

import com.yerbanalytics.backend.engine.DetalleRiego;
import com.yerbanalytics.backend.mqtt.ContratoNodo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tarea 7.1: volumen y tiempo de riego (D3). Fábrica: objetivo 65 %, 0,2 L/punto, máximo 6 L, 30 L/h.
 */
@DisplayName("CalculoRiego")
class CalculoRiegoTest {

    private static CalculoRiego.PlanRiego deficit(double humedad) {
        return CalculoRiego.porDeficit(humedad, 65, 0.2, 6, 30);
    }

    @Test
    void ejemploDeV2_humedad44con9() {
        CalculoRiego.PlanRiego p = deficit(44.9);

        assertThat(p.volumenL()).isEqualTo(4.02);
        assertThat(p.duracionSeg()).isEqualTo(483);
        assertThat(p.recortado()).isFalse();
    }

    @Test
    void humedad44_da505NoPorElErrorDePuntoFlotante() {
        // 21 × 0,2 en double es 4,2000000000000002 y ese error sumaba un segundo (505 s).
        CalculoRiego.PlanRiego p = deficit(44);

        assertThat(p.volumenL()).isEqualTo(4.2);
        assertThat(p.duracionSeg()).isEqualTo(504);
    }

    @Test
    void elTopeDeVolumenGanaSobreLaFormula() {
        // 0,3 L/punto y humedad 40 → 7,5 L, tope 6 L → 720 s.
        CalculoRiego.PlanRiego p = CalculoRiego.porDeficit(40, 65, 0.3, 6, 30);

        assertThat(p.volumenL()).isEqualTo(6.0);
        assertThat(p.duracionSeg()).isEqualTo(720);
        assertThat(p.recortado()).isFalse();
    }

    @Test
    void elDeficitCriticoRiegaElVolumenMaximoYElCaudalCalibradoCambiaElTiempo() {
        CalculoRiego.PlanRiego p = CalculoRiego.volumenMaximo(6, 60);

        assertThat(p.volumenL()).isEqualTo(6.0);
        assertThat(p.duracionSeg()).isEqualTo(360);
        assertThat(CalculoRiego.volumenMaximo(6, 30).duracionSeg()).isEqualTo(720);
    }

    @Test
    void laDuracionMaximaExactaDelContratoNoSeRecorta() {
        // 10 L a 30 L/h = 1200 s justos; 6 L a 18 L/h también.
        CalculoRiego.PlanRiego a = CalculoRiego.volumenMaximo(10, 30);
        CalculoRiego.PlanRiego b = CalculoRiego.volumenMaximo(6, 18);

        assertThat(a.duracionSeg()).isEqualTo(ContratoNodo.DURACION_VALVULA_MAX_SEG).isEqualTo(1200);
        assertThat(a.recortado()).isFalse();
        assertThat(b.duracionSeg()).isEqualTo(1200);
        assertThat(b.recortado()).isFalse();
    }

    @Test
    void siLaDuracionSuperaElContratoSeRecortaYSeMarca() {
        // 10 L a 20 L/h son 1800 s: se recorta a 1200 s.
        CalculoRiego.PlanRiego p = CalculoRiego.volumenMaximo(10, 20);

        assertThat(p.volumenL()).isEqualTo(10.0);
        assertThat(p.duracionSeg()).isEqualTo(1200);
        assertThat(p.recortado()).isTrue();
    }

    @Test
    void unSegundoPorEncimaDelContratoYaSeRecorta() {
        // 1200,x s → se redondea hacia arriba a 1201 y se recorta.
        CalculoRiego.PlanRiego p = CalculoRiego.volumenMaximo(10, 29.9);

        assertThat(p.recortado()).isTrue();
        assertThat(p.duracionSeg()).isEqualTo(1200);
    }

    @Test
    void elTiempoSiempreSeRedondeaHaciaArriba() {
        // 0,01 L a 30 L/h son 1,2 s → 2 s.
        assertThat(CalculoRiego.volumenMaximo(0.01, 30).duracionSeg()).isEqualTo(2);
    }

    @Test
    void sinDeficitNoHayRiego() {
        CalculoRiego.PlanRiego p = deficit(65);

        assertThat(p.volumenL()).isZero();
        assertThat(p.duracionSeg()).isZero();
        assertThat(deficit(70).volumenL()).isZero();
    }

    @Test
    void elCaudalDebeSerPositivo() {
        assertThatThrownBy(() -> CalculoRiego.volumenMaximo(6, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoRiego.porDeficit(40, 65, 0.2, 6, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elPlanSeConvierteEnDetalleDeRiegoConLaHumedad() {
        DetalleRiego d = deficit(44).aDetalle(44.0);

        assertThat(d).isEqualTo(new DetalleRiego(4.2, 504, 44.0, false));
    }

    @Test
    void cabeEnLaValvula_esExactoEnElLimite() {
        assertThat(CalculoRiego.cabeEnLaValvula(10, 30)).isTrue();
        assertThat(CalculoRiego.cabeEnLaValvula(10, 29.9)).isFalse();
        assertThat(CalculoRiego.cabeEnLaValvula(6, 30)).isTrue();
    }

    @Test
    void sinDeficitONegativoElPlanQuedaVacio() {
        // h ≥ objetivo: nada que regar. Un plan vacío (0 L, 0 s) nunca debe llegar a una válvula.
        assertThat(CalculoRiego.porDeficit(65, 65, 0.2, 6, 30)).isEqualTo(new CalculoRiego.PlanRiego(0.0, 0, false));
        assertThat(CalculoRiego.porDeficit(70, 65, 0.2, 6, 30).duracionSeg()).isZero();
    }

    @Test
    void unDeficitQueRedondeaACeroLitrosDaPlanVacio() {
        // (65 − 64,999) × 0,2 = 0,0002 L → 0,00 L: sin volumen no hay tiempo.
        CalculoRiego.PlanRiego p = CalculoRiego.porDeficit(64.999, 65, 0.2, 6, 30);

        assertThat(p.volumenL()).isZero();
        assertThat(p.duracionSeg()).isZero();
    }

    @Test
    void unVolumenMaximoNegativoDaPlanVacioNoUnaDuracionNegativa() {
        assertThat(CalculoRiego.volumenMaximo(-3, 30)).isEqualTo(new CalculoRiego.PlanRiego(0.0, 0, false));
        assertThat(CalculoRiego.porDeficit(40, 65, 0.2, -6, 30).duracionSeg()).isZero();
    }

    @Test
    void losValoresNoFinitosSonUnError() {
        assertThatThrownBy(() -> CalculoRiego.porDeficit(Double.NaN, 65, 0.2, 6, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoRiego.volumenMaximo(Double.NaN, 30)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoRiego.volumenMaximo(6, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoRiego.volumenMaximo(Double.POSITIVE_INFINITY, 30)).isInstanceOf(IllegalArgumentException.class);
    }
}
