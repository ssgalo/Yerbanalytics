package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 1.5: las familias reales arrancan con los valores de HOY (design D7), así migrar las
 * reglas al catálogo no cambia ninguna decisión.
 */
@DisplayName("Familias reales de parámetros")
class ParametrosRealesTest {

    private final CatalogoParametros catalogo = new CatalogoParametros(java.util.List.of());

    /** Valor actual en el código → clave del catálogo (con la fuente citada en D7). */
    private static final Map<String, Double> VALORES_DE_HOY = Map.ofEntries(
            Map.entry("seguridad.antiguedad-max-lectura", 90.0),     // 3 intervalos de publicación del nodo (30 s)
            Map.entry("riego.umbral-humedad", 42.0),                 // idealMin de humSus (seed)
            Map.entry("riego.tiempo-max-apertura", 120.0),           // configuracion_operativa
            Map.entry("riego.max-riegos-24h", 2.0),                  // DailyVolumeLimitRule:61
            Map.entry("riego.max-riegos-24h-sector", 1.0),           // IrrigationRule:98
            Map.entry("riego.lluvia-probabilidad", 60.0),            // rain-threshold-pct
            Map.entry("insumo.max-dosis-24h", 1.0),                  // DailyDoseLimitRule:80
            Map.entry("mediasombra.uv-umbral", 7.0),                 // uv-threshold
            Map.entry("mediasombra.apertura-proteccion-uv", 30.0),   // ShadingRule:90
            Map.entry("mediasombra.apertura-maxima", 100.0),         // configuracion_operativa
            Map.entry("diagnostico.confianza-minima", 85.0));        // SupplyRule:34 / capturas.confianza-minima

    @Test
    void cadaFabricaCoincideConElValorActual() {
        VALORES_DE_HOY.forEach((clave, esperado) ->
                assertThat(catalogo.fabricas().numero(clave)).as(clave).isEqualTo(esperado));
    }

    @Test
    void elCatalogoRealTieneExactamenteEsasClaves() {
        assertThat(catalogo.definiciones()).extracting(DefinicionParametro::clave)
                .containsExactlyInAnyOrderElementsOf(VALORES_DE_HOY.keySet());
    }

    @Test
    void todasTienenEtiquetaDescripcionYReferencia() {
        assertThat(catalogo.definiciones()).allSatisfy(d -> {
            assertThat(d.etiqueta()).isNotBlank();
            assertThat(d.descripcion()).isNotBlank();
            assertThat(d.refSpec()).isNotBlank();
        });
    }
}
