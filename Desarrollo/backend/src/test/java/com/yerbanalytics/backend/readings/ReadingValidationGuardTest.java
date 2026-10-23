package com.yerbanalytics.backend.readings;

import com.yerbanalytics.backend.dto.MetricSpec;
import com.yerbanalytics.backend.engine.ReglaTestSupport;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContextTestFactory;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.overrides.RuleOverride;
import com.yerbanalytics.backend.engine.overrides.TestRuleOverrides;
import com.yerbanalytics.backend.engine.parametros.CatalogoParametrosService;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.model.ZonaEntity;
import com.yerbanalytics.backend.mqtt.ComandoZonaPublisher;
import com.yerbanalytics.backend.service.ConfiguracionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S-03, el guard de la ingesta: valida, pide relectura inmediata (hasta {@code seguridad.relecturas-invalidas}),
 * usa la relectura que corrige y descarta sólo la métrica que agotó sus relecturas.
 */
@DisplayName("ReadingValidationGuard - relectura y descarte (S-03)")
class ReadingValidationGuardTest {

    private static final Instant CICLO_10 = Instant.parse("2026-10-17T13:05:00Z");   // 10:05 locales: ciclo de las 10:00

    /** Reloj que el test avanza. */
    private static final class RelojMutable extends Clock {
        Instant ahora = CICLO_10;

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    private final RelojMutable reloj = new RelojMutable();
    private final TestRuleOverrides overrides = new TestRuleOverrides();
    private ComandoZonaPublisher publisher;
    private InvalidReadingEscalation escalation;
    private CatalogoParametrosService catalogo;
    private ReadingValidationGuard guard;
    private ZonaEntity zona;

    @BeforeEach
    void setUp() {
        publisher = mock(ComandoZonaPublisher.class);
        when(publisher.leerAhora(anyString())).thenReturn(new ComandoZonaPublisher.Resultado(true, "cmd", null));
        escalation = mock(InvalidReadingEscalation.class);
        catalogo = mock(CatalogoParametrosService.class);
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.fabrica());
        ConfiguracionService configuracion = mock(ConfiguracionService.class);
        when(configuracion.getConfiguracionOperativa()).thenReturn(RuleContextTestFactory.defaultConfig());
        when(configuracion.getEffectiveSpecs()).thenReturn(List.of(
                spec("humSus", "Humedad de sustrato"), spec("temp", "Temperatura del aire"), spec("phSuelo", "pH del sustrato")));
        guard = new ReadingValidationGuard(escalation, publisher, catalogo, configuracion, overrides, reloj);
        zona = new ZonaEntity();
        zona.setId("MZ-1");
        zona.setName("Macro-zona 1");
    }

    private static MetricSpec spec(String key, String label) {
        return new MetricSpec(key, label, "", new Double[]{0.0, 1.0}, new Double[]{0.0, 1.0}, new Double[]{0.0, 1.0},
                0, 0.0, "ambiente", true);
    }

    private static ReadingMetrics lectura(Object... pares) {
        java.util.HashMap<String, Double> m = new java.util.HashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], ((Number) pares[i + 1]).doubleValue());
        }
        return ReadingMetrics.of(m);
    }

    // ------------------------------------------------------------------ lectura válida

    @Test
    @DisplayName("una lectura válida pasa entera, sin relectura, y reinicia la racha de sus métricas")
    void lecturaValida() {
        ReadingCheck r = guard.check(zona, lectura("humSus", 41.0, "temp", 22.0));

        assertThat(r.accepted().keys()).containsExactlyInAnyOrder("humSus", "temp");
        assertThat(r.pending()).isEmpty();
        assertThat(r.discarded()).isEmpty();
        assertThat(r.awaitsReread()).isFalse();
        verify(escalation).registerValid(eq("MZ-1"), anyCollection());
        verifyNoInteractions(publisher);
    }

    // ------------------------------------------------------------------ relectura

    @Test
    @DisplayName("humedad 112 %: la métrica queda fuera de lo aceptado, pendiente, y se pide la lectura de nuevo")
    void valorInvalidoPideRelectura() {
        ReadingCheck r = guard.check(zona, lectura("humSus", 112.0, "temp", 22.0));

        assertThat(r.accepted().keys()).containsExactly("temp");
        assertThat(r.pending()).containsExactly("humSus");
        assertThat(r.discarded()).isEmpty();
        assertThat(r.awaitsReread()).isTrue();
        verify(publisher).leerAhora("MZ-1");
        verify(escalation, never()).registerDiscard(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("varias métricas inválidas en la misma lectura piden UNA sola relectura")
    void unaRelecturaPorLectura() {
        guard.check(zona, lectura("humSus", 112.0, "temp", 99.0, "phSuelo", 11.0));

        verify(publisher, times(1)).leerAhora("MZ-1");
    }

    @Test
    @DisplayName("la relectura corrige: el pH de 11 y luego 5,4 usa el 5,4 y no deja ninguna alerta")
    void relecturaCorrige() {
        guard.check(zona, lectura("phSuelo", 11.0));

        ReadingCheck r = guard.check(zona, lectura("phSuelo", 5.4));

        assertThat(r.accepted().get("phSuelo")).isEqualTo(5.4);
        assertThat(r.awaitsReread()).isFalse();
        verify(escalation, never()).registerDiscard(any(), anyString(), anyString(), any(), any());
        verify(publisher, times(1)).leerAhora("MZ-1");
    }

    @Test
    @DisplayName("tras una relectura que corrige, el contador de esa métrica vuelve a cero")
    void relecturaCorrigeReiniciaElContador() {
        guard.check(zona, lectura("phSuelo", 11.0));   // pendiente 1
        guard.check(zona, lectura("phSuelo", 5.4));    // corrige
        guard.check(zona, lectura("phSuelo", 11.0));   // pendiente 1 de nuevo, no descartada
        ReadingCheck r = guard.check(zona, lectura("phSuelo", 11.0));   // pendiente 2

        assertThat(r.pending()).containsExactly("phSuelo");
        assertThat(r.discarded()).isEmpty();
    }

    // ------------------------------------------------------------------ descarte

    @Test
    @DisplayName("inválida en la lectura y en sus dos relecturas: se descarta esa métrica y las demás siguen")
    void tresInvalidasDescartan() {
        ReadingCheck primera = guard.check(zona, lectura("humSus", 112.0, "temp", 22.0));
        ReadingCheck segunda = guard.check(zona, lectura("humSus", 130.0, "temp", 22.0));
        ReadingCheck tercera = guard.check(zona, lectura("humSus", 115.0, "temp", 22.0));

        assertThat(primera.pending()).containsExactly("humSus");
        assertThat(segunda.pending()).containsExactly("humSus");
        assertThat(tercera.pending()).isEmpty();
        assertThat(tercera.discarded()).containsExactly("humSus");
        assertThat(tercera.awaitsReread()).isFalse();
        assertThat(tercera.accepted().keys()).containsExactly("temp");
        verify(publisher, times(2)).leerAhora("MZ-1");   // 2 relecturas, no 3
        verify(escalation).registerDiscard(eq(zona), eq("humSus"), eq("Humedad de sustrato"), any(), any());
        verify(escalation, never()).registerDiscard(any(), eq("temp"), anyString(), any(), any());
    }

    @Test
    @DisplayName("el descarte se informa con el ciclo de lectura y el anterior")
    void descarteConLosCiclos() {
        guard.check(zona, lectura("humSus", 112.0));
        guard.check(zona, lectura("humSus", 112.0));
        guard.check(zona, lectura("humSus", 112.0));

        // 10:05 locales → ciclo 10:00 locales (13:00Z); el anterior, 06:00 locales (09:00Z).
        verify(escalation).registerDiscard(zona, "humSus", "Humedad de sustrato",
                Instant.parse("2026-10-17T13:00:00Z"), Instant.parse("2026-10-17T09:00:00Z"));
    }

    @Test
    @DisplayName("el contador de relecturas es por ciclo: en el ciclo siguiente vuelve a empezar")
    void contadorPorCiclo() {
        guard.check(zona, lectura("temp", 99.0));   // pendiente 1, el relevo nunca llegó
        reloj.ahora = reloj.ahora.plus(Duration.ofHours(4));

        ReadingCheck r = guard.check(zona, lectura("temp", 99.0));

        assertThat(r.pending()).containsExactly("temp");
        assertThat(r.discarded()).isEmpty();
    }

    @Test
    @DisplayName("la cantidad de relecturas sale del catálogo: con 1 se descarta a la segunda lectura inválida")
    void relecturasDelCatalogo() {
        when(catalogo.vigentes()).thenReturn(ReglaTestSupport.con(Map.of(ParametrosSeguridad.RELECTURAS_INVALIDAS, "1")));

        ReadingCheck primera = guard.check(zona, lectura("temp", 99.0));
        ReadingCheck segunda = guard.check(zona, lectura("temp", 99.0));

        assertThat(primera.pending()).containsExactly("temp");
        assertThat(segunda.discarded()).containsExactly("temp");
        verify(publisher, times(1)).leerAhora("MZ-1");
    }

    @Test
    @DisplayName("las zonas no comparten contador")
    void contadorPorZona() {
        ZonaEntity otra = new ZonaEntity();
        otra.setId("MZ-2");
        otra.setName("Macro-zona 2");
        guard.check(zona, lectura("temp", 99.0));
        guard.check(zona, lectura("temp", 99.0));

        ReadingCheck r = guard.check(otra, lectura("temp", 99.0));

        assertThat(r.pending()).containsExactly("temp");
    }

    @Test
    @DisplayName("si no se puede publicar la relectura la ingesta sigue: el valor inválido igual queda afuera")
    void fallaAlPublicar() {
        when(publisher.leerAhora(anyString())).thenThrow(new IllegalStateException("broker caído"));

        ReadingCheck r = guard.check(zona, lectura("temp", 99.0));

        assertThat(r.pending()).containsExactly("temp");
        assertThat(r.accepted().isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------ simulador

    @Test
    @DisplayName("es controlable desde el simulador: bypass 'retries' y sin cooldowns")
    void esDemoControllable() {
        assertThat(guard).isInstanceOf(DemoControllable.class);
        assertThat(guard.name()).isEqualTo("ReadingValidationGuard");
        assertThat(guard.label()).isNotBlank().isNotEqualTo(guard.name());
        assertThat(guard.branch()).isEqualTo(RuleBranch.GLOBAL);
        assertThat(guard.bypassKeys()).containsExactly("retries");
        assertThat(guard.cooldownKeys()).isEmpty();
    }

    @Test
    @DisplayName("declara los parámetros que lee: los 5 rangos (mínimo y máximo) y las relecturas")
    void declaraSusParametros() {
        assertThat(guard.parametros()).extracting("clave").containsExactlyInAnyOrder(
                "seguridad.rango-hum-min", "seguridad.rango-hum-max",
                "seguridad.rango-temp-min", "seguridad.rango-temp-max",
                "seguridad.rango-ph-min", "seguridad.rango-ph-max",
                "seguridad.rango-ce-min", "seguridad.rango-ce-max",
                "seguridad.rango-luz-min", "seguridad.rango-luz-max",
                "seguridad.relecturas-invalidas");
    }

    @Test
    @DisplayName("apagado: humedad 112 % se acepta como cualquier otra lectura")
    void apagado() {
        overrides.turnOff(ReadingValidationGuard.NAME);

        ReadingCheck r = guard.check(zona, lectura("humSus", 112.0));

        assertThat(r.accepted().get("humSus")).isEqualTo(112.0);
        assertThat(r.pending()).isEmpty();
        assertThat(r.discarded()).isEmpty();
        verifyNoInteractions(publisher);
        verifyNoInteractions(escalation);
    }

    @Test
    @DisplayName("bypass 'retries': no pide relecturas y el valor inválido se descarta de inmediato")
    void bypassRetries() {
        overrides.put(ReadingValidationGuard.NAME, new RuleOverride(Map.of(), Set.of("retries")));

        ReadingCheck r = guard.check(zona, lectura("humSus", 112.0));

        assertThat(r.pending()).isEmpty();
        assertThat(r.discarded()).containsExactly("humSus");
        verifyNoInteractions(publisher);
        verify(escalation).registerDiscard(eq(zona), eq("humSus"), anyString(), any(), any());
    }

    @Test
    @DisplayName("sin etiqueta conocida para la métrica, la alerta usa su clave")
    void etiquetaDeRespaldo() {
        guard.check(zona, lectura("ce", 12.0));
        guard.check(zona, lectura("ce", 12.0));
        guard.check(zona, lectura("ce", 12.0));

        verify(escalation).registerDiscard(any(), eq("ce"), eq("ce"), any(), any());
    }

    @Test
    @DisplayName("una lectura sin métricas no pide nada ni toca la racha")
    void lecturaVacia() {
        ReadingCheck r = guard.check(zona, ReadingMetrics.of(Map.of()));

        assertThat(r.accepted().isEmpty()).isTrue();
        assertThat(r.awaitsReread()).isFalse();
        verifyNoInteractions(publisher);
        verify(escalation).registerValid(eq("MZ-1"), eq(List.of()));
    }
}
