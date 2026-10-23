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
     * <p>Tras la conmutación (bloque 10) el umbral de riego vale 45 y la probabilidad de lluvia 70 de
     * fábrica ({@code reglas_v2} §11), y las tres claves de las reglas viejas ya no existen.
     */
    private static final Map<String, Double> VALORES_DE_HOY = Map.ofEntries(
            Map.entry("seguridad.antiguedad-max-lectura", 90.0),     // 3 intervalos de publicación del nodo (30 s)
            Map.entry("seguridad.rango-hum-min", 0.0),               // reglas_v2 S-03: rango físico de la humedad
            Map.entry("seguridad.rango-hum-max", 100.0),
            Map.entry("seguridad.rango-temp-min", -10.0),
            Map.entry("seguridad.rango-temp-max", 60.0),
            Map.entry("seguridad.rango-ph-min", 3.0),
            Map.entry("seguridad.rango-ph-max", 9.0),
            Map.entry("seguridad.rango-ce-min", 0.0),
            Map.entry("seguridad.rango-ce-max", 10.0),
            Map.entry("seguridad.rango-luz-min", 0.0),
            Map.entry("seguridad.rango-luz-max", 100.0),
            Map.entry("seguridad.relecturas-invalidas", 2.0),
            Map.entry("riego.umbral-humedad", 45.0),                 // reglas_v2 §11 (era 42, idealMin de humSus)
            Map.entry("riego.lluvia-probabilidad", 70.0),            // reglas_v2 §11 (era 60)
            Map.entry("insumo.max-dosis-24h", 1.0),                  // DailyDoseLimitRule:80
            Map.entry("insumo.dosis-fertilizante", 10.0),            // INSUMO_SEG_POR_DEFECTO 10 s del firmware, a 1 s por ml
            Map.entry("insumo.dosis-fitosanitario", 10.0),
            Map.entry("insumo.intervalo-fertilizante", 5.0),         // reglas_v2 §8.2 P-D2
            Map.entry("insumo.intervalo-fitosanitario", 7.0),
            Map.entry("insumo.temp-max-aplicar", 30.0),              // reglas_v2 §8.2 P-D3
            Map.entry("insumo.humedad-min-aplicar", 45.0),           // reglas_v2 §8.2 P-D5
            Map.entry("insumo.lluvia-impide-mm", 5.0),               // reglas_v2 §8.2 P-D4
            Map.entry("insumo.lluvia-impide-horas", 6.0),
            Map.entry("insumo.reingreso-horas", 24.0),               // reglas_v2 §8.3
            Map.entry("insumo.stock-aviso", 20.0),
            Map.entry("insumo.pendiente-vence-dias", 7.0),           // add-dosificacion-base D-5
            Map.entry("nutricion.intervalo-plan", 7.0),              // reglas_v2 §8.4 N-01
            Map.entry("nutricion.ce-baja", 0.5),                     // reglas_v2 §8.4 N-02
            Map.entry("nutricion.ce-lecturas", 6.0),
            Map.entry("nutricion.sin-fertilizar-dias", 5.0),
            Map.entry("diagnostico.vigencia-horas", 48.0),           // reglas_v2 §1.3 P-D1
            Map.entry("mediasombra.uv-umbral", 7.0),                 // uv-threshold
            Map.entry("readings.retention-days", 14.0),              // add-historial-lecturas D-4
            Map.entry("sensado.espera-respuesta", 60.0),             // reglas_v2 §11 Telemetría
            Map.entry("sensado.reintentos", 2.0),
            Map.entry("sensado.espera-reintento", 1.0),
            Map.entry("sensado.reintento-fuera-servicio", 15.0),
            Map.entry("seguimiento.latencia-min", 2.0),              // add-motor-reglas-operativo D-14 (era seguimiento_latencia_min)
            Map.entry("seguimiento.delta-min", 5.0),
            Map.entry("diagnostico.confianza-minima", 70.0));        // reglas_v2 §1.3 y §11 (era 85)

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
    void losParametrosDeSensadoSonLosDeReglasV2Seccion11() {
        record Esperado(String clave, String unidad, String fabrica, double min, double max) {}
        List<Esperado> esperados = List.of(
                new Esperado("sensado.espera-respuesta", "s", "60", 10, 300),
                new Esperado("sensado.reintentos", "reintentos", "2", 1, 5),
                new Esperado("sensado.espera-reintento", "min", "1", 1, 10),
                new Esperado("sensado.reintento-fuera-servicio", "min", "15", 5, 60));

        for (Esperado e : esperados) {
            DefinicionParametro d = catalogo.definicion(e.clave()).orElseThrow();
            assertThat(d.familia()).as(e.clave()).isEqualTo(FamiliaParametro.SENSADO);
            assertThat(d.tipo()).as(e.clave()).isEqualTo(TipoParametro.ENTERO);
            assertThat(d.unidad()).as(e.clave()).isEqualTo(e.unidad());
            assertThat(d.fabrica()).as(e.clave()).isEqualTo(e.fabrica());
            assertThat(d.min()).as(e.clave()).isEqualTo(e.min());
            assertThat(d.max()).as(e.clave()).isEqualTo(e.max());
            assertThat(d.refSpec()).as(e.clave()).contains("reglas_v2");
        }
    }

    @Test
    void losParametrosDeSeguimientoVienenDeLaConfiguracionOperativaYSeEditanEnElMotor() {
        DefinicionParametro latencia = catalogo.definicion("seguimiento.latencia-min").orElseThrow();
        assertThat(latencia.familia()).isEqualTo(FamiliaParametro.SEGUIMIENTO);
        assertThat(latencia.tipo()).isEqualTo(TipoParametro.ENTERO);
        assertThat(latencia.unidad()).isEqualTo("min");
        assertThat(latencia.fabrica()).isEqualTo("2");
        assertThat(latencia.min()).isEqualTo(1.0);
        assertThat(latencia.max()).isEqualTo(1440.0);

        DefinicionParametro delta = catalogo.definicion("seguimiento.delta-min").orElseThrow();
        assertThat(delta.familia()).isEqualTo(FamiliaParametro.SEGUIMIENTO);
        assertThat(delta.tipo()).isEqualTo(TipoParametro.NUMERO);
        assertThat(delta.fabrica()).isEqualTo("5");
        assertThat(delta.min()).isEqualTo(0.5);
        assertThat(delta.max()).isEqualTo(50.0);
        assertThat(delta.decimales()).isEqualTo(1);
    }

    @Test
    void losRangosFisicosDeS03SonDeSeguridadYSoloSeEditanHaciaAdentro() {
        record Esperado(String clave, String unidad, String fabrica, double min, double max) {}
        List<Esperado> esperados = List.of(
                new Esperado("seguridad.rango-hum-min", "%", "0", 0, 50),
                new Esperado("seguridad.rango-hum-max", "%", "100", 50, 100),
                new Esperado("seguridad.rango-temp-min", "°C", "-10", -40, 25),
                new Esperado("seguridad.rango-temp-max", "°C", "60", 25, 85),
                new Esperado("seguridad.rango-ph-min", "", "3", 0, 7),
                new Esperado("seguridad.rango-ph-max", "", "9", 7, 14),
                new Esperado("seguridad.rango-ce-min", "dS/m", "0", 0, 5),
                new Esperado("seguridad.rango-ce-max", "dS/m", "10", 5, 20),
                new Esperado("seguridad.rango-luz-min", "%", "0", 0, 50),
                new Esperado("seguridad.rango-luz-max", "%", "100", 50, 100),
                new Esperado("seguridad.relecturas-invalidas", "relecturas", "2", 1, 5));

        for (Esperado e : esperados) {
            DefinicionParametro d = catalogo.definicion(e.clave()).orElseThrow();
            assertThat(d.familia()).as(e.clave()).isEqualTo(FamiliaParametro.SEGURIDAD);
            assertThat(d.unidad()).as(e.clave()).isEqualTo(e.unidad());
            assertThat(d.fabrica()).as(e.clave()).isEqualTo(e.fabrica());
            assertThat(d.min()).as(e.clave()).isEqualTo(e.min());
            assertThat(d.max()).as(e.clave()).isEqualTo(e.max());
            assertThat(d.refSpec()).as(e.clave()).contains("S-03");
        }
    }

    @Test
    void laConfianzaMinimaDelDiagnosticoVaDe50a95PorCiento() {
        DefinicionParametro d = catalogo.definicion("diagnostico.confianza-minima").orElseThrow();

        assertThat(d.min()).isEqualTo(50.0);
        assertThat(d.max()).isEqualTo(95.0);
        assertThat(d.refSpec()).contains("reglas_v2");
    }

    @Test
    void lasDosisDeInsumoVanDe1a50MlComoElTopeDelFirmware() {
        for (String clave : List.of("insumo.dosis-fertilizante", "insumo.dosis-fitosanitario")) {
            DefinicionParametro d = catalogo.definicion(clave).orElseThrow();

            assertThat(d.familia()).as(clave).isEqualTo(FamiliaParametro.INSUMO);
            assertThat(d.tipo()).as(clave).isEqualTo(TipoParametro.ENTERO);
            assertThat(d.unidad()).as(clave).isEqualTo("ml");
            assertThat(d.min()).as(clave).isEqualTo(1.0);
            // Una dosis mayor a la que corta el nodo se rechaza al guardar, no se recorta en silencio.
            assertThat(d.max()).as(clave).isEqualTo((double) com.yerbanalytics.backend.mqtt.ContratoNodo.BOMBA_ML_MAX);
        }
        assertThat(catalogo.definicion("insumo.dosis-ml")).isEmpty();
    }

    @Test
    void losParametrosDeAplicacionSonLosDeReglasV2Seccion8() {
        record Esperado(String clave, TipoParametro tipo, String unidad, String fabrica, Double min, Double max) {}
        List<Esperado> esperados = List.of(
                new Esperado("insumo.intervalo-fertilizante", TipoParametro.NUMERO, "d", "5", 3.0, 14.0),
                new Esperado("insumo.intervalo-fitosanitario", TipoParametro.NUMERO, "d", "7", 5.0, 21.0),
                new Esperado("insumo.ventana-manana", TipoParametro.VENTANA_HORARIA, "", "07:00-10:00", null, null),
                new Esperado("insumo.ventana-tarde", TipoParametro.VENTANA_HORARIA, "", "17:00-19:00", null, null),
                new Esperado("insumo.temp-max-aplicar", TipoParametro.NUMERO, "°C", "30", 25.0, 32.0),
                new Esperado("insumo.humedad-min-aplicar", TipoParametro.NUMERO, "%", "45", 35.0, 60.0),
                new Esperado("insumo.lluvia-impide-mm", TipoParametro.NUMERO, "mm", "5", 2.0, 20.0),
                new Esperado("insumo.lluvia-impide-horas", TipoParametro.ENTERO, "h", "6", 2.0, 12.0),
                new Esperado("insumo.reingreso-horas", TipoParametro.NUMERO, "h", "24", 12.0, 72.0),
                new Esperado("insumo.stock-aviso", TipoParametro.NUMERO, "%", "20", 10.0, 50.0),
                new Esperado("insumo.pendiente-vence-dias", TipoParametro.ENTERO, "d", "7", 1.0, 30.0),
                new Esperado("diagnostico.vigencia-horas", TipoParametro.ENTERO, "h", "48", 12.0, 168.0));

        for (Esperado e : esperados) {
            DefinicionParametro d = catalogo.definicion(e.clave()).orElseThrow();
            assertThat(d.tipo()).as(e.clave()).isEqualTo(e.tipo());
            assertThat(d.unidad()).as(e.clave()).isEqualTo(e.unidad());
            assertThat(d.fabrica()).as(e.clave()).isEqualTo(e.fabrica());
            assertThat(d.min()).as(e.clave()).isEqualTo(e.min());
            assertThat(d.max()).as(e.clave()).isEqualTo(e.max());
            assertThat(d.refSpec()).as(e.clave()).isNotBlank();
        }
    }

    @Test
    void losParametrosDeNutricionSonLosDeReglasV2Seccion8_4() {
        record Esperado(String clave, TipoParametro tipo, String unidad, String fabrica, Double min, Double max) {}
        List<Esperado> esperados = List.of(
                new Esperado("nutricion.intervalo-plan", TipoParametro.NUMERO, "d", "7", 3.0, 30.0),
                new Esperado("nutricion.ce-baja", TipoParametro.NUMERO, "dS/m", "0.5", 0.3, 1.0),
                new Esperado("nutricion.ce-lecturas", TipoParametro.ENTERO, "lecturas", "6", 3.0, 12.0),
                new Esperado("nutricion.sin-fertilizar-dias", TipoParametro.NUMERO, "d", "5", 3.0, 14.0));

        for (Esperado e : esperados) {
            DefinicionParametro d = catalogo.definicion(e.clave()).orElseThrow();
            assertThat(d.familia()).as(e.clave()).isEqualTo(FamiliaParametro.NUTRICION);
            assertThat(d.tipo()).as(e.clave()).isEqualTo(e.tipo());
            assertThat(d.unidad()).as(e.clave()).isEqualTo(e.unidad());
            assertThat(d.fabrica()).as(e.clave()).isEqualTo(e.fabrica());
            assertThat(d.min()).as(e.clave()).isEqualTo(e.min());
            assertThat(d.max()).as(e.clave()).isEqualTo(e.max());
            assertThat(d.refSpec()).as(e.clave()).contains("reglas_v2 §8.4");
        }
    }

    @Test
    void lasClavesDeLasReglasViejasYaNoExisten() {
        assertThat(catalogo.definicion("riego.tiempo-max-apertura")).isEmpty();
        assertThat(catalogo.definicion("riego.max-riegos-24h")).isEmpty();
        assertThat(catalogo.definicion("riego.max-riegos-24h-sector")).isEmpty();
    }

    @Test
    void elCatalogoRealTieneLasClavesDeHoyMasLasDeRiegoV2() {
        List<String> claves = new java.util.ArrayList<>(VALORES_DE_HOY.keySet());
        RIEGO_V2.forEach(e -> claves.add(e.clave()));
        claves.addAll(List.of("insumo.ventana-manana", "insumo.ventana-tarde"));   // ventanas: no son numéricas

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
        assertThat(catalogo.restricciones()).hasSize(9);
        assertThat(catalogo.violaciones(catalogo.fabricas())).isEmpty();
    }
}
