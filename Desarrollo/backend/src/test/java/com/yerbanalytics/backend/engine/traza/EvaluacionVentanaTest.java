package com.yerbanalytics.backend.engine.traza;

import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.FamiliaParametro;
import com.yerbanalytics.backend.engine.parametros.ParametroNoDeclaradoException;
import com.yerbanalytics.backend.engine.parametros.ParametrosVigentes;
import com.yerbanalytics.backend.engine.parametros.TipoParametro;
import com.yerbanalytics.backend.engine.parametros.VentanaHoraria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tarea 2.2: {@code compararVentana} registra "hora ∈ ventana" con el operador {@code EN}. */
@DisplayName("Evaluacion.compararVentana")
class EvaluacionVentanaTest {

    /** Definición de una ventana horaria como la que va a declarar R-05 (sin depender del catálogo real). */
    private record Ventana(String clave) implements DefinicionParametro {
        @Override public String etiqueta() { return "Ventana"; }
        @Override public String descripcion() { return "Ventana de prueba"; }
        @Override public FamiliaParametro familia() { return FamiliaParametro.RIEGO; }
        @Override public TipoParametro tipo() { return TipoParametro.VENTANA_HORARIA; }
        @Override public String unidad() { return ""; }
        @Override public String fabrica() { return "06:00-18:00"; }
        @Override public Double min() { return null; }
        @Override public Double max() { return null; }
        @Override public int decimales() { return 0; }
        @Override public String refSpec() { return "test"; }
    }

    private static final DefinicionParametro VENTANA = new Ventana("riego.ventana-normal");
    private static final DefinicionParametro OTRA = new Ventana("riego.otra-ventana");

    private static Rule regla(DefinicionParametro... params) {
        return new Rule() {
            @Override public int priority() { return 6; }
            @Override public String name() { return "ReglaFalsa"; }
            @Override public List<DefinicionParametro> parametros() { return List.of(params); }
            @Override public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) { return List.of(); }
        };
    }

    private static Evaluacion evaluacion(String ventana) {
        return new Evaluacion(regla(VENTANA), ParametrosVigentes.de(
                Map.of(VENTANA.clave(), VentanaHoraria.parse(ventana))));
    }

    @Test
    void registraLaComparacionConElOperadorEN() {
        Evaluacion ev = evaluacion("06:00-18:00");

        boolean adentro = ev.compararVentana("Hora local", LocalTime.of(17, 42), VENTANA);

        assertThat(adentro).isTrue();
        assertThat(ev.comparaciones()).containsExactly(new Comparacion("Hora local", "riego.ventana-normal",
                "17:42", Operador.EN, "06:00-18:00", "", true, ResultadoComparacion.CUMPLE));
        assertThat(Operador.EN.simbolo()).isEqualTo("∈");
    }

    @Test
    void afueraDeLaVentanaNoCumple() {
        Evaluacion ev = evaluacion("07:00-17:00");

        assertThat(ev.compararVentana("Hora local", LocalTime.of(6, 30), VENTANA)).isFalse();
        assertThat(ev.comparaciones().get(0).resultado()).isEqualTo(ResultadoComparacion.NO_CUMPLE);
        assertThat(ev.comparaciones().get(0).umbral()).isEqualTo("07:00-17:00");
    }

    @Test
    void lasDieciochoEnPuntoSigueAdentro_yElSegundoMinutoNo() {
        Evaluacion ev = evaluacion("06:00-18:00");

        assertThat(ev.compararVentana("Hora local", LocalTime.of(18, 0, 59), VENTANA)).isTrue();
        assertThat(ev.compararVentana("Hora local", LocalTime.of(18, 1, 0), VENTANA)).isFalse();
        assertThat(ev.comparaciones().get(0).recibido()).isEqualTo("18:00");
    }

    @Test
    void sinHoraEsSinDatoYNoCumple() {
        Evaluacion ev = evaluacion("06:00-18:00");

        assertThat(ev.compararVentana("Hora local", null, VENTANA)).isFalse();
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.SIN_DATO);
        assertThat(c.recibido()).isNull();
    }

    @Test
    void unParametroNoDeclaradoLanza() {
        Evaluacion ev = evaluacion("06:00-18:00");

        assertThatThrownBy(() -> ev.compararVentana("Hora local", LocalTime.NOON, OTRA))
                .isInstanceOf(ParametroNoDeclaradoException.class);
    }
}
