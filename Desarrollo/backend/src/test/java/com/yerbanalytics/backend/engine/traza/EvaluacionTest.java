package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.ValorParametro;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tareas 3.1 a 3.3: {@link Evaluacion} compara, registra y sólo lee lo que la regla declaró. */
@DisplayName("Evaluacion")
class EvaluacionTest {

    private static final DefinicionParametro UMBRAL = ParametrosRiego.UMBRAL_HUMEDAD;

    /** Regla falsa que declara sólo el umbral de riego. */
    private static Rule reglaConUmbral() {
        return new Rule() {
            @Override public int priority() { return 10; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public List<DefinicionParametro> parametros() { return List.of(UMBRAL); }
            @Override public List<RuleAction> evaluate(RuleContext ctx) { return List.of(); }
        };
    }

    private static ParametrosVigentes vigentes(double umbral) {
        return ParametrosVigentes.de(Map.of(
                UMBRAL.clave(), new ValorParametro.Numero(umbral),
                ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA.clave(), new ValorParametro.Numero(30)));
    }

    private static Evaluacion evaluacion(double umbral) {
        return new Evaluacion(reglaConUmbral(), vigentes(umbral));
    }


    // ------------------------------------------------------------------ NaN y cero con signo

    @ParameterizedTest
    @CsvSource({"LT", "LE", "GT", "GE", "EQ"})
    void comparar_unRecibidoNaNEsSinDatoYNuncaCumple(Operador op) {
        Evaluacion ev = evaluacion(42);

        assertThat(ev.comparar("Humedad de sustrato", Double.NaN, op, UMBRAL)).isFalse();
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
    }

    @Test
    void comparar_menosCeroYCeroSonIguales() {
        Evaluacion ev = evaluacion(0);

        assertThat(ev.comparar("x", -0.0, Operador.EQ, UMBRAL)).isTrue();
        assertThat(ev.comparar("x", -0.0, Operador.LT, UMBRAL)).isFalse();
        assertThat(ev.comparar("x", 0.0, Operador.GT, UMBRAL)).isFalse();
        assertThat(ev.comparar("x", -0.0, Operador.LE, UMBRAL)).isTrue();
        assertThat(ev.comparar("x", -0.0, Operador.GE, UMBRAL)).isTrue();
    }

    @Test
    void compararFijo_NaNEsSinDatoYMenosCeroIgualACero() {
        Evaluacion ev = evaluacion(42);

        assertThat(ev.compararFijo("n", Double.NaN, Operador.GT, 0.0)).isFalse();
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
        assertThat(ev.compararFijo("z", -0.0, Operador.EQ, 0.0)).isTrue();
        assertThat(ev.compararFijo("z", -0.0, Operador.LT, 0.0)).isFalse();
    }

    // ------------------------------------------------------------------ 3.1

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            "38, LT, 42, true",   "42, LT, 42, false", "50, LT, 42, false",
            "42, LE, 42, true",   "43, LE, 42, false",
            "43, GT, 42, true",   "42, GT, 42, false",
            "42, GE, 42, true",   "41, GE, 42, false",
            "42, EQ, 42, true",   "41, EQ, 42, false"
    })
    void comparar_devuelveElBooleanoCorrectoParaCadaOperador(double recibido, Operador op, double umbral, boolean esperado) {
        Evaluacion ev = evaluacion(umbral);

        assertThat(ev.comparar("Humedad de sustrato", recibido, op, UMBRAL)).isEqualTo(esperado);
    }

    @Test
    void comparar_registraCumpleYNoCumple() {
        Evaluacion ev = evaluacion(42);

        ev.comparar("Humedad de sustrato", 38.0, Operador.LT, UMBRAL);
        ev.comparar("Humedad de sustrato", 50.0, Operador.LT, UMBRAL);

        assertThat(ev.comparaciones()).extracting(Comparacion::resultado)
                .containsExactly(ResultadoComparacion.CUMPLE, ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    void comparar_registraTodosLosCamposDeLaComparacion() {
        Evaluacion ev = evaluacion(42);

        ev.comparar("Humedad de sustrato", 38.0, Operador.LT, UMBRAL);

        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.etiqueta()).isEqualTo("Humedad de sustrato");
        assertThat(c.clave()).isEqualTo("riego.umbral-humedad");
        assertThat(c.recibido()).isEqualTo(38.0);
        assertThat(c.operador()).isEqualTo(Operador.LT);
        assertThat(c.umbral()).isEqualTo(42.0);
        assertThat(c.unidad()).isEqualTo(UMBRAL.unidad());
        assertThat(c.configurable()).isTrue();
    }

    @Test
    void comparar_sinDatoDevuelveFalseYRegistraSinDato() {
        Evaluacion ev = evaluacion(42);

        boolean r = ev.comparar("Humedad de sustrato", null, Operador.LT, UMBRAL);

        assertThat(r).isFalse();
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.recibido()).isNull();
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
        assertThat(c.umbral()).isEqualTo(42.0);
    }

    @Test
    void comparar_guardaElValorDelUmbralNoLaReferencia() {
        // Dos evaluaciones con snapshots distintos: cada traza conserva contra qué se comparó.
        Evaluacion vieja = evaluacion(42);
        vieja.comparar("Humedad de sustrato", 38.0, Operador.LT, UMBRAL);
        Evaluacion nueva = evaluacion(35);
        nueva.comparar("Humedad de sustrato", 38.0, Operador.LT, UMBRAL);

        assertThat(vieja.comparaciones().get(0).umbral()).isEqualTo(42.0);
        assertThat(vieja.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
        assertThat(nueva.comparaciones().get(0).umbral()).isEqualTo(35.0);
        assertThat(nueva.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
    }

    @Test
    void comparaciones_noSePuedeModificarDesdeAfuera() {
        Evaluacion ev = evaluacion(42);
        ev.comparar("Humedad de sustrato", 38.0, Operador.LT, UMBRAL);

        assertThatThrownBy(() -> ev.comparaciones().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void comparar_conUmbralNoDeclaradoLanza() {
        Evaluacion ev = evaluacion(42);

        assertThatThrownBy(() -> ev.comparar("Antigüedad", 10.0, Operador.GT, ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA))
                .isInstanceOf(ParametroNoDeclaradoException.class);
        assertThat(ev.comparaciones()).isEmpty();
    }

    // ------------------------------------------------------------------ 3.2

    @Test
    void numero_devuelveElValorVigenteDeUnParametroDeclarado() {
        assertThat(evaluacion(42).numero(UMBRAL)).isEqualTo(42.0);
    }

    @Test
    void numero_conParametroNoDeclaradoLanzaNombrandoReglaYClave() {
        Evaluacion ev = evaluacion(42);

        assertThatThrownBy(() -> ev.numero(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA))
                .isInstanceOf(ParametroNoDeclaradoException.class)
                .hasMessageContaining("ReglaFalsa")
                .hasMessageContaining("seguridad.antiguedad-max-lectura");
    }

    @Test
    void horaYVentana_tambienExigenQueElParametroEsteDeclarado() {
        Evaluacion ev = evaluacion(42);

        assertThatThrownBy(() -> ev.hora(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA))
                .isInstanceOf(ParametroNoDeclaradoException.class);
        assertThatThrownBy(() -> ev.ventana(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA))
                .isInstanceOf(ParametroNoDeclaradoException.class);
    }

    // ------------------------------------------------------------------ 3.3

    @Test
    void compararFijo_registraNoConfigurableYSinClave() {
        Evaluacion ev = evaluacion(42);

        boolean r = ev.compararFijo("Estado del sector", "critical", Operador.EQ, "critical");

        assertThat(r).isTrue();
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.configurable()).isFalse();
        assertThat(c.clave()).isNull();
        assertThat(c.unidad()).isEmpty();
        assertThat(c.recibido()).isEqualTo("critical");
        assertThat(c.umbral()).isEqualTo("critical");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    void compararFijo_noCumpleYSinDato() {
        Evaluacion ev = evaluacion(42);

        assertThat(ev.compararFijo("Estado del sector", "ok", Operador.EQ, "critical")).isFalse();
        assertThat(ev.compararFijo("Estado del sector", null, Operador.EQ, "critical")).isFalse();

        assertThat(ev.comparaciones()).extracting(Comparacion::resultado)
                .containsExactly(ResultadoComparacion.NO_CUMPLE, ResultadoComparacion.SIN_DATO);
    }

    @Test
    void compararFijo_conNumerosUsaElOperador() {
        Evaluacion ev = evaluacion(42);

        assertThat(ev.compararFijo("Dosis en 24 h", 2.0, Operador.GT, 0.0)).isTrue();
        assertThat(ev.compararFijo("Dosis en 24 h", 0.0, Operador.GT, 0.0)).isFalse();
    }
}
