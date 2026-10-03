package com.yerbanalytics.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Tarea 1.1: la zona horaria del vivero es fija y el reloj del contexto la usa. */
@DisplayName("Reloj y zona horaria del vivero")
class RelojConfigTest {

    @Test
    void laZonaDelViveroEsBuenosAires() {
        assertThat(ZonaHorariaVivero.ZONA).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
    }

    @Test
    void elBeanClockDelContextoUsaEsaZona() {
        new ApplicationContextRunner().withUserConfiguration(RelojConfig.class).run(ctx -> {
            Clock reloj = ctx.getBean(Clock.class);
            assertThat(reloj.getZone()).isEqualTo(ZonaHorariaVivero.ZONA);
        });
    }

    @Test
    void laHoraLocalNoDependeDeLaZonaDelJvm() {
        // 20:30 UTC son las 17:30 en Buenos Aires (UTC-3), sin importar dónde corra el JVM.
        Clock fijo = Clock.fixed(Instant.parse("2026-10-03T20:30:00Z"), ZonaHorariaVivero.ZONA);
        assertThat(ZonedDateTime.now(fijo).toLocalTime().toString()).isEqualTo("17:30");
    }
}
