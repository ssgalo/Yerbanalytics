package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.rules.StaleSensorRule;
import com.yerbanalytics.backend.engine.traza.Comparacion;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.traza.ResultadoComparacion;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.yerbanalytics.backend.engine.ReglaTestSupport.ev;
import static com.yerbanalytics.backend.engine.ReglaTestSupport.evaluar;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitarios de {@link StaleSensorRule}. La antigüedad se calcula acá con
 * {@code zona.lastReadingTime} y {@code ctx.now()} contra el parámetro del catálogo.
 */
@DisplayName("StaleSensorRule")
class StaleSensorRuleTest {

    private static final Instant AHORA = Instant.parse("2026-01-01T12:00:00Z");

    private StaleSensorRule rule;
    private SectorEntity sector;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        rule = new StaleSensorRule();
        sector = RuleContextTestFactory.sectorBasico("MZ-1-001");
        zona = RuleContextTestFactory.zonaBasica("MZ-1");
    }

    /** Contexto con la última lectura de la zona {@code segundos} antes de {@code AHORA}; null = nunca. */
    private RuleContext conLecturaHace(Double segundos) {
        zona.setLastReadingTime(segundos == null ? null : AHORA.toEpochMilli() - (long) (segundos * 1000));
        return new RuleContext(sector, zona, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false);
    }

    @Test
    @DisplayName("lectura más vieja que el umbral → ABORT_RIEGO")
    void cuandoLaLecturaEsVieja_emiteAbortRiego() {
        List<RuleAction> acciones = evaluar(rule, conLecturaHace(91.0));

        assertThat(acciones).hasSize(1);
        assertThat(acciones.get(0).type()).isEqualTo(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).ruleName()).isEqualTo("StaleSensorRule");
        assertThat(acciones.get(0).motivo()).contains("MZ-1");
    }

    @Test
    @DisplayName("lectura reciente → NOOP_INFO")
    void cuandoLaLecturaEsReciente_emiteNoopInfo() {
        assertThat(evaluar(rule, conLecturaHace(10.0))).extracting(RuleAction::type)
                .containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("exactamente en el umbral todavía es fresca (comparación estricta)")
    void exactamenteEnElUmbral_esFresca() {
        assertThat(evaluar(rule, conLecturaHace(90.0))).extracting(RuleAction::type)
                .containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("sin lectura → SIN_DATO y bloquea igual que antes")
    void sinLectura_bloqueaYQuedaSinDato() {
        Evaluacion ev = ev(rule);

        List<RuleAction> acciones = rule.evaluate(conLecturaHace(null), ev);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(ev.comparaciones()).extracting(Comparacion::resultado).containsExactly(ResultadoComparacion.SIN_DATO);
    }

    @Test
    @DisplayName("sin zona → bloquea (zona desconocida)")
    void sinZona_bloquea() {
        RuleContext ctx = new RuleContext(sector, null, List.of(), List.of(), RuleContextTestFactory.defaultConfig(),
                "ok", AHORA, null, false);

        List<RuleAction> acciones = evaluar(rule, ctx);

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.ABORT_RIEGO);
        assertThat(acciones.get(0).motivo()).contains("desconocida");
    }

    @Test
    @DisplayName("la traza registra la antigüedad en segundos contra el parámetro")
    void laTrazaRegistraLaAntiguedadContraElParametro() {
        Evaluacion ev = ev(rule);

        rule.evaluate(conLecturaHace(95.0), ev);

        assertThat(rule.parametros()).containsExactly(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA);
        Comparacion c = ev.comparaciones().get(0);
        assertThat(c.recibido()).isEqualTo(95.0);
        assertThat(c.operador()).isEqualTo(Operador.GT);
        assertThat(c.umbral()).isEqualTo(90.0);
        assertThat(c.unidad()).isEqualTo("s");
        assertThat(c.clave()).isEqualTo("seguridad.antiguedad-max-lectura");
        assertThat(c.resultado()).isEqualTo(ResultadoComparacion.CUMPLE);
    }

    @Test
    @DisplayName("el umbral sale del catálogo")
    void elUmbralSaleDelCatalogo() {
        // Con 120 s de tolerancia, una lectura de 95 s (vieja para la fábrica) sigue siendo fresca.
        List<RuleAction> acciones = rule.evaluate(conLecturaHace(95.0),
                ev(rule, Map.of(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA, "120")));

        assertThat(acciones).extracting(RuleAction::type).containsExactly(ActionType.NOOP_INFO);
    }

    @Test
    @DisplayName("ABORT_RIEGO es una acción bloqueante")
    void abortRiegoEsBloqueante() {
        assertThat(ActionType.ABORT_RIEGO.isBlocking()).isTrue();
    }
}
