package com.yerbanalytics.backend.engine.parametros;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las familias reales arrancan con los valores de HOY (design D7 del catálogo), así migrar las
 * reglas al catálogo no cambia ninguna decisión; y el catálogo de riego trae los 15 parámetros de
 * {@code reglas_v2} §11 (design D8 de implement-reglas-riego, tarea 6.1).
 */
@DisplayName("Familias reales de parámetros")
class ParametrosRealesTest {

    private final CatalogoParametros catalogo = new CatalogoParametros(java.util.List.of());

    /**
     * Valor actual en el código → clave del catálogo.
     *
     * <p>Umbral de riego y probabilidad de lluvia valen 42 y 60 TODAVÍA: las reglas viejas
     * (IrrigationRule, WeatherOverrideRule) siguen activas hasta la conmutación (bloque 10) y los
     * leen de acá. La conmutación los pasa a 45 y 70 de fábrica, junto con las reglas nuevas.
     */
    private static final Map<String, Double> VALORES_DE_HOY = Map.ofEntries(
            Map.entry("seguridad.antiguedad-max-lectura", 90.0),     // 3 intervalos de publicación del nodo (30 s)
            Map.entry("riego.umbral-humedad", 42.0),                 // idealMin de humSus (seed); 45 al conmutar
            Map.entry("riego.tiempo-max-apertura", 120.0),           // configuracion_operativa; sale al conmutar
            Map.entry("riego.max-riegos-24h", 2.0),                  // DailyVolumeLimitRule:61; sale al conmutar
            Map.entry("riego.max-riegos-24h-sector", 1.0),           // IrrigationRule:98; sale al conmutar
            Map.entry("riego.lluvia-probabilidad", 60.0),            // rain-threshold-pct; 70 al conmutar
            Map.entry("insumo.max-dosis-24h", 1.0),                  // DailyDoseLimitRule:80
            Map.entry("mediasombra.uv-umbral", 7.0),                 // uv-threshold
            Map.entry("mediasombra.apertura-proteccion-uv", 30.0),   // ShadingRule:90
            Map.entry("mediasombra.apertura-maxima", 100.0),         // configuracion_operativa
            Map.entry("diagnostico.confianza-minima", 85.0));        // SupplyRule:34 / capturas.confianza-minima

    /** Las claves nuevas de riego v2 (tabla D8): tipo, unidad, fábrica, mínimo, máximo y decimales. */
    private record Esperado(String clave, TipoParametro tipo, String unidad, String fabrica,
                            Double min, Double max, int decimales) {}

    private static final List<Esperado> RIEGO_V2 = List.of(
            new Esperado("riego.umbral-critico", TipoParametro.NUMERO, "%", "35", 25.0, 40.0, 0),
            new Esperado("riego.humedad-objetivo", TipoParametro.NUMERO, "%", "65", 55.0, 75.0, 0),
            new Esperado("riego.litros-por-punto", TipoParametro.NUMERO, "L/punto", "0.2", 0.1, 0.5, 2),
            new Esperado("riego.volumen-max-evento", TipoParametro.NUMERO, "L", "6", 3.0, 10.0, 1),
            new Esperado("riego.caudal-emisor", TipoParametro.NUMERO, "L/h", "30", 5.0, 120.0, 1),
            new Esperado("riego.saturacion-bloqueo", TipoParametro.NUMERO, "%", "75", 65.0, 85.0, 0),
            new Esperado("riego.saturacion-alerta", TipoParametro.NUMERO, "%", "80", 65.0, 85.0, 0),
            new Esperado("riego.ventana-normal", TipoParametro.VENTANA_HORARIA, "", "06:00-18:00", null, null, 0),
            new Esperado("riego.lluvia-mm", TipoParametro.NUMERO, "mm", "5", 2.0, 20.0, 1),
            new Esperado("riego.lluvia-ventana", TipoParametro.ENTERO, "h", "4", 2.0, 12.0, 0),
            new Esperado("riego.pausa-tras-aplicacion", TipoParametro.NUMERO, "h", "6", 2.0, 24.0, 0),
            new Esperado("riego.exceptuado-bloqueo", TipoParametro.NUMERO, "h", "12", 6.0, 24.0, 0),
            new Esperado("riego.sectores-simultaneos", TipoParametro.ENTERO, "sectores", "10", 1.0, 100.0, 0));

    @Test
    void cadaFabricaCoincideConElValorActual() {
        VALORES_DE_HOY.forEach((clave, esperado) ->
                assertThat(catalogo.fabricas().numero(clave)).as(clave).isEqualTo(esperado));
    }

    @Test
    void elCatalogoRealTieneLasClavesDeHoyMasLasDeRiegoV2() {
        List<String> claves = new java.util.ArrayList<>(VALORES_DE_HOY.keySet());
        RIEGO_V2.forEach(e -> claves.add(e.clave()));

        assertThat(catalogo.definiciones()).extracting(DefinicionParametro::clave)
                .containsExactlyInAnyOrderElementsOf(claves);
    }

    @Test
    void lasClavesDeRiegoV2TienenTipoUnidadFabricaRangoYDecimalesDeLaTablaD8() {
        for (Esperado e : RIEGO_V2) {
            DefinicionParametro d = catalogo.definicion(e.clave()).orElseThrow();
            assertThat(d.tipo()).as(e.clave() + " tipo").isEqualTo(e.tipo());
            assertThat(d.unidad()).as(e.clave() + " unidad").isEqualTo(e.unidad());
            assertThat(d.fabrica()).as(e.clave() + " fábrica").isEqualTo(e.fabrica());
            assertThat(d.min()).as(e.clave() + " mín").isEqualTo(e.min());
            assertThat(d.max()).as(e.clave() + " máx").isEqualTo(e.max());
            assertThat(d.decimales()).as(e.clave() + " decimales").isEqualTo(e.decimales());
            assertThat(d.familia()).isEqualTo(FamiliaParametro.RIEGO);
        }
    }

    @Test
    void losRangosYDecimalesDeLosParametrosCompartidosSonLosDeLaTablaD8() {
        DefinicionParametro umbral = catalogo.definicion("riego.umbral-humedad").orElseThrow();
        assertThat(umbral.min()).isEqualTo(35.0);
        assertThat(umbral.max()).isEqualTo(60.0);
        assertThat(umbral.decimales()).isZero();
        DefinicionParametro lluvia = catalogo.definicion("riego.lluvia-probabilidad").orElseThrow();
        assertThat(lluvia.min()).isEqualTo(50.0);
        assertThat(lluvia.max()).isEqualTo(95.0);
    }

    @Test
    void todasTienenEtiquetaDescripcionYReferencia() {
        assertThat(catalogo.definiciones()).allSatisfy(d -> {
            assertThat(d.etiqueta()).isNotBlank();
            assertThat(d.descripcion()).isNotBlank();
            assertThat(d.refSpec()).isNotBlank();
        });
    }

    @Test
    void lasDeRiegoV2CitanReglasV2() {
        assertThat(RIEGO_V2).allSatisfy(e ->
                assertThat(catalogo.definicion(e.clave()).orElseThrow().refSpec()).contains("reglas_v2"));
    }

    @Test
    void losValoresDeFabricaCumplenLasRestriccionesCruzadas() {
        assertThat(catalogo.restricciones()).hasSize(4);
        assertThat(catalogo.violaciones(catalogo.fabricas())).isEmpty();
    }
}
