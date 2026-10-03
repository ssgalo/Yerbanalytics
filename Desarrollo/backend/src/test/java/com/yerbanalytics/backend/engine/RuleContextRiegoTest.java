package com.yerbanalytics.backend.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 3.2: el contexto de riego es opcional; los constructores viejos lo dejan vacío. */
@DisplayName("RuleContext.riego")
class RuleContextRiegoTest {

    @Test
    void elConstructorDeNueveArgumentosDejaElContextoDeRiegoVacio() {
        RuleContext ctx = new RuleContext(RuleContextTestFactory.sectorBasico("MZ-1-001"),
                RuleContextTestFactory.zonaBasica("MZ-1"), List.of(), List.of(),
                RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false);

        assertThat(ctx.riego()).isEqualTo(ContextoRiego.vacio());
    }

    @Test
    void elVacioNoTieneNingunDato() {
        ContextoRiego v = ContextoRiego.vacio();

        assertThat(v.inicioCiclo()).isNull();
        assertThat(v.ultimoRiegoMs()).isNull();
        assertThat(v.ultimoRiegoCriticoMs()).isNull();
        assertThat(v.ultimaAplicacionMs()).isNull();
        assertThat(v.riegoEnCursoHastaMs()).isNull();
        assertThat(v.humSusTs()).isNull();
    }

    @Test
    void elConstructorCompletoConservaElContexto() {
        Instant ciclo = Instant.parse("2026-10-03T13:00:00Z");
        ContextoRiego c = new ContextoRiego(ciclo, 1L, 2L, 3L, 4L, 5L);

        RuleContext ctx = new RuleContext(RuleContextTestFactory.sectorBasico("MZ-1-001"),
                RuleContextTestFactory.zonaBasica("MZ-1"), List.of(), List.of(),
                RuleContextTestFactory.defaultConfig(), "ok", Instant.now(), null, false, c);

        assertThat(ctx.riego()).isSameAs(c);
    }
}
