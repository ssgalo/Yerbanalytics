package com.yerbanalytics.backend.constant;

import com.yerbanalytics.backend.dto.ColorPair;
import com.yerbanalytics.backend.dto.MetricSpec;

import java.util.List;
import java.util.Map;

/** Constantes de dominio — portadas de {@code frontend/src/data/mock/specs.ts}. */
public final class NurseryConstants {

    public static final String EMDASH = "\u2014";

    public static final Map<String, String> C = Map.of(
            "ok", "#3FA06A",
            "warning", "#E0972C",
            "critical", "#DD5238",
            "offline", "#A9B2AB"
    );

    public static final Map<String, String> LAB = Map.of(
            "ok", "Saludable",
            "warning", "En observaci\u00f3n",
            "critical", "Cr\u00edtico",
            "offline", "Fuera de servicio"
    );

    public static final Map<String, ColorPair> SEV_MAP = Map.of(
            "Alta", new ColorPair("#FBE6E0", "#A8331C"),
            "Media", new ColorPair("#FBF0DC", "#A66A12"),
            "Baja", new ColorPair("#E7F1EA", "#2E7A4F"),
            EMDASH, new ColorPair("#EEEDE5", "#6A776E")
    );

    /** Agrupaci\u00f3n visual del panel de sensado. */
    public static final String GRUPO_AMBIENTE = "ambiente";
    public static final String GRUPO_NUTRICION = "nutricion";

    /**
     * Las 10 m\u00e9tricas que publica el nodo testigo (claves espejadas de
     * {@code Desarrollo/embebido/comun/contrato.h}).
     *
     * <p>Notas de unidad, resueltas contra el firmware:
     * <ul>
     *   <li>{@code ce} \u2014 la sonda emite \u00b5S/cm; la ingesta convierte a dS/m, que es la
     *       unidad de estas bandas.</li>
     *   <li>{@code uv} \u2014 el nodo mide % de luz con un LDR, no radiaci\u00f3n UV. La clave se
     *       conserva por compatibilidad con el contrato MQTT, pero la m\u00e9trica es
     *       luminosidad. Si se incorpora un sensor UV real hay que separar los conceptos
     *       (ver design.md D5 de {@code move-sensado-macrozona}).</li>
     * </ul>
     *
     * <p>Las cinco \u00faltimas son informativas ({@code afectaEstado = false}): sus rangos son
     * provisionales y no deben mover el estado de los sectores hasta validarlos.
     */
    public static final List<MetricSpec> SPECS = List.of(
            // --- Ambiente ---
            new MetricSpec("humSus", "Humedad de sustrato", "%", arr(42.0, 68.0), arr(32.0, 80.0), arr(22.0, 90.0), 0, 55.0, GRUPO_AMBIENTE, true),
            new MetricSpec("humAmb", "Humedad ambiental", "%", arr(62.0, 84.0), arr(52.0, 91.0), arr(42.0, 96.0), 0, 72.0, GRUPO_AMBIENTE, true),
            new MetricSpec("temp", "Temperatura del aire", "\u00b0C", arr(18.0, 27.0), arr(15.0, 31.0), arr(11.0, 35.0), 1, 23.0, GRUPO_AMBIENTE, true),
            new MetricSpec("uv", "Luminosidad", "%", arr(35.0, 70.0), arr(20.0, 85.0), arr(10.0, 95.0), 0, 50.0, GRUPO_AMBIENTE, true),
            new MetricSpec("tempSuelo", "Temperatura del sustrato", "\u00b0C", arr(16.0, 24.0), arr(13.0, 28.0), arr(10.0, 32.0), 1, 20.0, GRUPO_AMBIENTE, false),
            // --- Nutrici\u00f3n del sustrato ---
            new MetricSpec("ce", "Nutrientes (CE)", "dS/m", arr(1.0, 1.9), arr(0.8, 2.5), arr(0.5, 3.1), 1, 1.4, GRUPO_NUTRICION, true),
            new MetricSpec("phSuelo", "pH del sustrato", "pH", arr(5.0, 6.0), arr(4.5, 6.5), arr(4.0, 7.0), 1, 5.5, GRUPO_NUTRICION, false),
            new MetricSpec("n", "Nitr\u00f3geno", "mg/kg", arr(100.0, 200.0), arr(70.0, 260.0), arr(40.0, 320.0), 0, 150.0, GRUPO_NUTRICION, false),
            new MetricSpec("p", "F\u00f3sforo", "mg/kg", arr(30.0, 60.0), arr(20.0, 80.0), arr(10.0, 100.0), 0, 45.0, GRUPO_NUTRICION, false),
            new MetricSpec("k", "Potasio", "mg/kg", arr(120.0, 240.0), arr(90.0, 300.0), arr(60.0, 380.0), 0, 180.0, GRUPO_NUTRICION, false)
    );

    public static final List<ZonaDef> ZONA_DEFS = List.of(
            new ZonaDef("MZ-1", "Macro-zona 1", "Sector norte"),
            new ZonaDef("MZ-2", "Macro-zona 2", "Sector norte"),
            new ZonaDef("MZ-3", "Macro-zona 3", "Sector centro"),
            new ZonaDef("MZ-4", "Macro-zona 4", "Sector centro"),
            new ZonaDef("MZ-5", "Macro-zona 5", "Sector sur"),
            new ZonaDef("MZ-6", "Macro-zona 6", "Sector sur")
    );

    public static final Map<String, List<PathoEntry>> PATHOS = Map.of(
            "warning", List.of(
                    new PathoEntry("Clorosis", "Media"),
                    new PathoEntry("Estr\u00e9s solar", "Media")
            ),
            "critical", List.of(
                    new PathoEntry("Da\u00f1o f\u00fangico", "Alta"),
                    new PathoEntry("Plaga foliar", "Alta"),
                    new PathoEntry("Estr\u00e9s solar", "Alta")
            )
    );

    public static final Map<String, String> TINTS = Map.ofEntries(
            Map.entry("Sano", "radial-gradient(circle at 35% 30%, #5BBE82, #2C7A4E)"),
            Map.entry("Estr\u00e9s solar", "radial-gradient(circle at 35% 30%, #E89B5A, #B5572A)"),
            Map.entry("Clorosis", "radial-gradient(circle at 35% 30%, #E4CE5E, #B79A2C)"),
            Map.entry("Plaga foliar", "radial-gradient(circle at 35% 30%, #6FB0A6, #2F6F66)"),
            Map.entry("Da\u00f1o f\u00fangico", "radial-gradient(circle at 35% 30%, #B89B7D, #6E563C)"),
            Map.entry("No concluyente", "radial-gradient(circle at 35% 30%, #B7BEB8, #7C857E)"),
            Map.entry("Sin diagn\u00f3stico", "linear-gradient(135deg,#C4CAC4,#9AA39D)")
    );

    public static final Map<String, ActMeta> ACT = Map.of(
            "Riego", new ActMeta("#E2EEF3", "#2A6E8C", "M12 2.7s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11Z"),
            "Insumo", new ActMeta("#EDEAF6", "#5A4B9E", "M9 2h6M10 2v4l-4.5 9A2 2 0 0 0 7.3 18h9.4a2 2 0 0 0 1.8-3L14 6V2"),
            "Mediasombra", new ActMeta("#F3ECDD", "#8A6A22", "M4 12h16M12 4v3M6 7l1.5 1.5M18 7l-1.5 1.5M3 16h18a9 9 0 0 0-18 0Z"),
            "Info", new ActMeta("#EEEDE5", "#6A776E", "M12 22c5.523 0 10-4.477 10-10S17.523 2 12 2 2 6.477 2 12s4.477 10 10 10zm0-6v-4m0-4h.01")
    );

    public static final Map<String, ColorPair> RES_MAP = Map.of(
            "Efectiva", new ColorPair("#E7F1EA", "#2E7A4F"),
            "En seguimiento", new ColorPair("#F3ECDD", "#8A6A22"),
            "Pospuesta", new ColorPair("#FBE7D7", "#9A4410"),
            "Abortada", new ColorPair("#FBE6E0", "#A8331C"),
            "Informativo", new ColorPair("#EEEDE5", "#6A776E")
    );

    public static final List<ActTemplate> ACT_TPL = List.of(
            new ActTemplate("Riego", "Riego ejecutado", "Humedad de sustrato bajo umbral (38%). Microaspersor abierto 95 s \u00b7 0,42 L.", "Efectiva", "hace 6 min"),
            new ActTemplate("Riego", "Riego pospuesto", "D\u00e9ficit h\u00eddrico detectado pero API meteorol\u00f3gica confirma lluvia inminente (60%).", "Pospuesta", "hace 22 min"),
            new ActTemplate("Insumo", "Dosificaci\u00f3n de fungicida", "Da\u00f1o f\u00fangico (confianza 93%) + sustrato >80%. Bomba perist\u00e1ltica inyect\u00f3 4,5 ml.", "En seguimiento", "hace 48 min"),
            new ActTemplate("Mediasombra", "Apertura de mediasombra", "Plan de rustificaci\u00f3n d\u00eda 12 \u00b7 apertura gradual 35% \u2192 45%.", "Efectiva", "hace 1 h"),
            new ActTemplate("Insumo", "Dosificaci\u00f3n de nutrientes", "Clorosis por d\u00e9ficit nutricional (confianza 89%). Inyecci\u00f3n de 3,0 ml de NPK.", "En seguimiento", "hace 1 h"),
            new ActTemplate("Riego", "Riego abortado", "Sensor testigo sin reporte hace 2 h. Actuaci\u00f3n aut\u00f3noma anulada por seguridad.", "Abortada", "hace 2 h"),
            new ActTemplate("Mediasombra", "Retracci\u00f3n de mediasombra", "Pico de radiaci\u00f3n UV 9 detectado. Cobertura llevada a 70% para proteger plantines.", "Efectiva", "hace 3 h"),
            new ActTemplate("Insumo", "Dosificaci\u00f3n bloqueada", "L\u00edmite qu\u00edmico diario alcanzado (sector ya recibi\u00f3 dosis m\u00e1x. en 24 h).", "Abortada", "hace 4 h")
    );

    private NurseryConstants() {
    }

    private static Double[] arr(double a, double b) {
        return new Double[]{a, b};
    }

    public record ZonaDef(String id, String name, String sub) {
    }

    public record PathoEntry(String estado, String sev) {
    }

    public record ActMeta(String tint, String ink, String path) {
    }

    public record ActTemplate(String tipo, String title, String detail, String res, String time) {
    }
}
