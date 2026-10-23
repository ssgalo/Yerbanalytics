package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea 4.10: los umbrales viven en el catálogo. Ninguna regla lee un {@code @Value} (salvo el
 * dato del lote de {@code ShadingRule}) y todas implementan la firma con {@code Evaluacion}.
 */
@DisplayName("Reglas del motor - arquitectura")
class ReglasArquitecturaTest {

    private static List<Class<?>> reglas() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Rule.class));
        List<Class<?>> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.yerbanalytics.backend.engine.rules")) {
            out.add(Class.forName(bd.getBeanClassName()));
        }
        return out;
    }

    @Test
    void hayReglasParaVerificar() throws Exception {
        assertThat(reglas()).isNotEmpty();
    }

    @Test
    void ningunaReglaLeeUnValue() throws Exception {
        List<String> encontrados = new ArrayList<>();
        for (Class<?> c : reglas()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(Value.class)) {
                    encontrados.add(c.getSimpleName() + "." + f.getName());
                }
            }
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                for (Parameter p : k.getParameters()) {
                    if (p.isAnnotationPresent(Value.class)) {
                        encontrados.add(c.getSimpleName() + "(" + p.getType().getSimpleName() + ")");
                    }
                }
            }
        }
        // La fecha de siembra de ShadingRule se fue con la rustificación global: ahora no queda ninguna excepción.
        assertThat(encontrados).isEmpty();
    }

    @Test
    void lasPrioridadesSonUnicasYElHardwareVaEntreElBloqueoYLaFrescura() throws Exception {
        Map<String, Integer> prioridades = new HashMap<>();
        for (Class<?> c : reglas()) {
            Field f = c.getDeclaredField("PRIORITY");
            f.setAccessible(true);
            prioridades.put(c.getSimpleName(), f.getInt(null));
        }

        // Un empate dejaría el orden de dos reglas librado al de la inyección de Spring.
        assertThat(prioridades.values()).as(prioridades.toString()).doesNotHaveDuplicates();
        assertThat(prioridades.get("ManualLockRule")).isLessThan(prioridades.get("IncompleteHardwareRule"));
        assertThat(prioridades.get("IncompleteHardwareRule")).isLessThan(prioridades.get("StaleSensorRule"));
    }

    @Test
    void lasReglasDeFallaVanAntesDeLasQueActuan() throws Exception {
        Map<String, Integer> prioridades = new HashMap<>();
        for (Class<?> c : reglas()) {
            Field f = c.getDeclaredField("PRIORITY");
            f.setAccessible(true);
            prioridades.put(c.getSimpleName(), f.getInt(null));
        }

        // R-02 y las demás reglas de riego no pueden ordenar agua antes de saber que la válvula está averiada.
        assertThat(prioridades.get("IncompleteHardwareRule")).isLessThan(prioridades.get("HydraulicFaultRule"));
        assertThat(prioridades.get("HydraulicFaultRule"))
                .isLessThan(prioridades.get("StaleSensorRule"))
                .isLessThan(prioridades.get("DeficitCriticoRule"))
                .isLessThan(prioridades.get("RiegoPorDeficitRule"))
                .isLessThan(prioridades.get("SupplyRule"));
        // La malla (reglas de zona) no se mueve sin antes saber que está averiada.
        assertThat(new com.yerbanalytics.backend.mediasombra.ShadeFaultRule().priority())
                .isLessThan(new com.yerbanalytics.backend.mediasombra.ShadingRule().priority());
    }

    @Test
    void todaReglaImplementaLaFirmaConEvaluacionYNingunaConservaLaVieja() throws Exception {
        for (Class<?> c : reglas()) {
            List<Method> evaluate = java.util.Arrays.stream(c.getDeclaredMethods())
                    .filter(m -> m.getName().equals("evaluate")).toList();
            assertThat(evaluate).as(c.getSimpleName())
                    .hasSize(1)
                    .allSatisfy(m -> assertThat(m.getParameterCount()).isEqualTo(2));
        }
    }

    // ------------------------------------------------------------------
    // Declaración de cada pieza conmutable (add-motor-reglas-operativo, D-3, D-4, D-12)
    // ------------------------------------------------------------------

    /** Lo que D-4 declara como protección: apagarlas pide motivo y confirmación. */
    private static final Set<String> PROTECCIONES_ESPERADAS = Set.of(
            "ManualLockRule", "EscalamientoNodo", "StaleSensorRule", "ReadingValidationGuard",
            "IncompleteHardwareRule", "HydraulicFaultRule", "HardwareFaultService", "SustratoSaturadoRule",
            "CicloLecturaRiegoRule", "ShadeFaultRule", "DailyDoseLimitRule");

    /**
     * Los pictogramas de Unicode (el equivalente de {@code \p{Extended_Pictographic}}, que Java 17 no tiene). Quedan
     * afuera ✓ y ✗: son tipografía de la traza, no emojis.
     */
    static final Pattern EMOJI = Pattern.compile("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{26FF}\\x{2702}-\\x{2712}"
            + "\\x{2714}-\\x{2716}\\x{2718}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}]");
    private static final Pattern CODIGO_EN_ETIQUETA = Pattern.compile("\\([A-Z]-[A-Z0-9]{1,3}[^)]*\\)");
    private static final Pattern CODIGO = Pattern.compile("[A-Z]-[A-Z0-9]{1,3}(, [A-Z]-[A-Z0-9]{1,3})*");

    /** Las piezas de producción, instanciadas sin constructor: sólo se les piden constantes. */
    private static List<DemoControllable> piezas() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DemoControllable.class));
        var objenesis = new org.springframework.objenesis.ObjenesisStd();
        List<DemoControllable> out = new ArrayList<>();
        for (var bd : scanner.findCandidateComponents("com.yerbanalytics.backend")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            if (!c.getProtectionDomain().getCodeSource().getLocation().getPath().contains("test-classes")) {
                out.add((DemoControllable) objenesis.newInstance(c));
            }
        }
        return out;
    }

    @Test
    void hayPiezasConmutablesParaVerificar() throws Exception {
        assertThat(piezas()).hasSizeGreaterThanOrEqualTo(23);
    }

    @Test
    @DisplayName("ninguna label() lleva emojis ni el código entre paréntesis (D-12)")
    void lasEtiquetasNoLlevanEmojisNiCodigo() throws Exception {
        assertThat(piezas()).allSatisfy(p -> {
            assertThat(EMOJI.matcher(p.label()).find()).as("%s: emoji en '%s'", p.name(), p.label()).isFalse();
            assertThat(CODIGO_EN_ETIQUETA.matcher(p.label()).find())
                    .as("%s: código en '%s'", p.name(), p.label()).isFalse();
        });
    }

    @Test
    @DisplayName("code() es vacío o un código de reglas_v2 (o una lista de ellos)")
    void elCodigoTieneFormatoValido() throws Exception {
        assertThat(piezas()).allSatisfy(p -> assertThat(p.code())
                .as("%s", p.name())
                .satisfiesAnyOf(c -> assertThat(c).isEmpty(), c -> assertThat(c).matches(CODIGO)));
    }

    @Test
    @DisplayName("effect() == PROTECTS si y sólo si declara la consecuencia de apagarla")
    void protegeSiYSoloSiDeclaraSuConsecuencia() throws Exception {
        assertThat(piezas()).allSatisfy(p -> {
            boolean protege = p.effect() == RuleEffect.PROTECTS;
            assertThat(p.protection().isPresent()).as(p.name()).isEqualTo(protege);
            p.protection().ifPresent(t -> assertThat(t).as(p.name()).isNotBlank());
        });
    }

    @Test
    @DisplayName("las protecciones declaradas son exactamente las de D-4")
    void lasProteccionesSonLasEsperadas() throws Exception {
        Set<String> declaradas = new TreeSet<>();
        for (DemoControllable p : piezas()) {
            if (p.protection().isPresent()) {
                declaradas.add(p.name());
            }
        }
        assertThat(declaradas).isEqualTo(new TreeSet<>(PROTECCIONES_ESPERADAS));
    }

    @Test
    @DisplayName("el objetivo por defecto sale de la rama y SANITY sólo lo declaran las F-*")
    void elObjetivoSaleDeLaRama() throws Exception {
        assertThat(piezas()).allSatisfy(p -> assertThat(p.objective()).as(p.name()).isNotNull());
        Map<String, RuleObjective> porNombre = new HashMap<>();
        piezas().forEach(p -> porNombre.put(p.name(), p.objective()));
        assertThat(porNombre)
                .containsEntry("RiegoPorDeficitRule", RuleObjective.IRRIGATION)
                .containsEntry("ManualLockRule", RuleObjective.PROTECTION)
                .containsEntry("SupplyRule", RuleObjective.NUTRITION)
                .containsEntry("ShadingRule", RuleObjective.SHADE)
                .containsEntry("FollowUpRule", RuleObjective.FOLLOW_UP);
    }
}
