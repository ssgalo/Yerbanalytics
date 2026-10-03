package com.yerbanalytics.backend.engine.parametros;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tareas 1.3 y 1.4: validaciones de arranque del catálogo y cobertura de riego v2. */
@DisplayName("CatalogoParametros")
class CatalogoParametrosTest {

    private static final DefinicionParametro UMBRAL = DefinicionDePrueba.numero("riego.umbral", "45", 35.0, 60.0, 0);

    private static Rule regla(String nombre, DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return 1; }
            @Override public String name() { return nombre; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx) { return List.of(); }
        };
    }

    private static CatalogoParametros catalogo(List<DefinicionParametro> defs) {
        return new CatalogoParametros(defs, List.of(), List.of());
    }

    // ------------------------------------------------------------------ 1.3

    @Test
    void claveDuplicada_fallaNombrandoLaClave() {
        List<DefinicionParametro> defs = List.of(UMBRAL, DefinicionDePrueba.numero("riego.umbral", "50", 35.0, 60.0, 0));

        assertThatThrownBy(() -> catalogo(defs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.umbral")
                .hasMessageContaining("duplicad");
    }

    @Test
    void fabricaFueraDeRango_fallaNombrandoElParametro() {
        List<DefinicionParametro> defs = List.of(DefinicionDePrueba.numero("riego.umbral", "70", 35.0, 60.0, 0));

        assertThatThrownBy(() -> catalogo(defs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.umbral")
                .hasMessageContaining("fábrica");
    }

    @Test
    void fabricaMalFormada_fallaNombrandoElParametro() {
        List<DefinicionParametro> defs = List.of(DefinicionDePrueba.ventana("riego.ventana", "25:00-18:00"));

        assertThatThrownBy(() -> catalogo(defs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.ventana");
    }

    @Test
    void claveSinPrefijoDeFamilia_oMalFormada_falla() {
        assertThatThrownBy(() -> catalogo(List.of(DefinicionDePrueba.numero("insumo.dosis", "1", 0.0, 5.0, 0))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insumo.dosis");
        assertThatThrownBy(() -> catalogo(List.of(DefinicionDePrueba.numero("riego.Umbral_Humedad", "1", 0.0, 5.0, 0))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.Umbral_Humedad");
    }

    @Test
    void reglaQueDeclaraClaveInexistente_fallaNombrandoReglaYClave() {
        DefinicionParametro fantasma = DefinicionDePrueba.numero("riego.fantasma", "1", 0.0, 5.0, 0);
        Rule rule = regla("IrrigationRule", fantasma);

        assertThatThrownBy(() -> new CatalogoParametros(List.of(UMBRAL), List.of(), List.of(rule)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IrrigationRule")
                .hasMessageContaining("riego.fantasma");
    }

    @Test
    void restriccionConClaveInexistente_falla() {
        RestriccionCruzada r = new RestriccionCruzada(List.of("riego.umbral", "riego.nada"), v -> true, "x");

        assertThatThrownBy(() -> new CatalogoParametros(List.of(UMBRAL), List.of(r), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.nada");
    }

    @Test
    void catalogoValido_exponeDefinicionesFabricasYUsadoPor() {
        Rule a = regla("A", UMBRAL);
        Rule b = regla("B", UMBRAL);
        Rule c = regla("C");
        CatalogoParametros cat = new CatalogoParametros(List.of(UMBRAL), List.of(), List.of(a, b, c));

        assertThat(cat.definiciones()).containsExactly(UMBRAL);
        assertThat(cat.definicion("riego.umbral")).contains(UMBRAL);
        assertThat(cat.definicion("nada")).isEmpty();
        assertThat(cat.fabricas().numero("riego.umbral")).isEqualTo(45.0);
        assertThat(cat.usadoPor("riego.umbral")).containsExactly("A", "B");
        assertThat(cat.reglas()).containsExactly(a, b, c);
    }


    @Test
    void minMayorQueMax_fallaNombrandoElParametro() {
        List<DefinicionParametro> defs = List.of(DefinicionDePrueba.numero("riego.umbral", "50", 60.0, 35.0, 0));

        assertThatThrownBy(() -> catalogo(defs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("riego.umbral")
                .hasMessageContaining("mínimo");
    }

    @Test
    void fabricaQueViolaUnaRestriccionCruzada_fallaAlConstruir() {
        DefinicionParametro a = DefinicionDePrueba.numero("riego.a", "50", 0.0, 100.0, 0);
        DefinicionParametro b = DefinicionDePrueba.numero("riego.b", "40", 0.0, 100.0, 0);
        RestriccionCruzada r = new RestriccionCruzada(List.of("riego.a", "riego.b"),
                v -> v.numero("riego.a") < v.numero("riego.b"), "A debe ser menor que B.");

        assertThatThrownBy(() -> new CatalogoParametros(List.of(a, b), List.of(r), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A debe ser menor que B.")
                .hasMessageContaining("fábrica");
    }

    // ------------------------------------------------------------------ 1.4

    private static CatalogoParametros catalogoRiegoV2() {
        return new CatalogoParametros(
                new ArrayList<DefinicionParametro>(Arrays.asList(ParametrosRiegoV2Fixture.values())),
                ParametrosRiegoV2Fixture.RESTRICCIONES,
                List.of());
    }

    private static ParametrosVigentes conCambios(CatalogoParametros cat, Map<String, String> cambios) {
        Map<String, ValorParametro> m = new HashMap<>(cat.fabricas().valores());
        cambios.forEach((k, v) -> m.put(k, cat.definicion(k).orElseThrow().tipo()
                .parsear(v, cat.definicion(k).orElseThrow())));
        return ParametrosVigentes.de(m);
    }

    @Test
    void familiaRiegoV2_registraLas15EntradasSinErrores() {
        CatalogoParametros cat = catalogoRiegoV2();

        assertThat(cat.definiciones()).hasSize(15);
        assertThat(cat.fabricas().ventana("riego.ventana-normal").contiene(LocalTime.of(18, 30))).isFalse();
        assertThat(cat.fabricas().numero("riego.litros-por-punto")).isEqualTo(0.2);
        assertThat(cat.fabricas().numero("riego.sectores-simultaneos")).isEqualTo(10.0);
        assertThat(cat.violaciones(cat.fabricas())).isEmpty();
    }

    @Test
    void restriccionCruzada_criticoMenorQueUmbralMenorQueObjetivo() {
        CatalogoParametros cat = catalogoRiegoV2();

        // umbral (35) no queda por encima del crítico vigente (35)
        assertThat(cat.violaciones(conCambios(cat, Map.of("riego.umbral-humedad", "35"))))
                .extracting(RestriccionCruzada::mensaje)
                .containsExactly("El umbral crítico debe ser menor que el umbral de riego.");
        // umbral por encima del objetivo (65)
        assertThat(cat.violaciones(conCambios(cat, Map.of("riego.umbral-humedad", "60", "riego.humedad-objetivo", "55"))))
                .extracting(RestriccionCruzada::mensaje)
                .containsExactly("El umbral de riego debe ser menor que la humedad objetivo.");
        // conjunto válido con varios cambios
        assertThat(cat.violaciones(conCambios(cat, Map.of("riego.umbral-humedad", "50", "riego.umbral-critico", "40"))))
                .isEmpty();
    }

    @Test
    void restriccionCruzada_bloqueoNoSuperaAlerta() {
        CatalogoParametros cat = catalogoRiegoV2();

        assertThat(cat.violaciones(conCambios(cat, Map.of("riego.saturacion-bloqueo", "82"))))
                .extracting(RestriccionCruzada::mensaje)
                .containsExactly("El bloqueo por saturación no puede superar la alerta de saturación.");
        assertThat(cat.violaciones(conCambios(cat, Map.of("riego.saturacion-bloqueo", "80"))))
                .isEmpty();
    }
}
