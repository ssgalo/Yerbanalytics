package com.yerbanalytics.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Reloj único del vivero. Quien necesite "la hora" (el motor, el despacho de riego, el cliente de
 * pronóstico) la toma de este {@link Clock} en vez de {@code Instant.now()}: así la hora es
 * inyectable y los tests la fijan con {@code Clock.fixed}.
 */
@Configuration
public class RelojConfig {

    @Bean
    public Clock relojVivero() {
        return Clock.system(ZonaHorariaVivero.ZONA);
    }
}
