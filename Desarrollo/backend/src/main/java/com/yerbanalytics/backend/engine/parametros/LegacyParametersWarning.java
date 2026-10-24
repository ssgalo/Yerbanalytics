package com.yerbanalytics.backend.engine.parametros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, avisa si quedó la tabla vieja {@code parametro_regla} con filas: un override por clave que ya no se lee
 * (desde rediseno-motor-reglas el valor es de cada pieza, en {@code regla_parametro}). Sin correr
 * {@code migracion-parametros-por-regla.sql} las copias quedan en fábrica. No migra nada: sólo avisa.
 */
@Component
public class LegacyParametersWarning {

    private static final Logger log = LoggerFactory.getLogger(LegacyParametersWarning.class);

    private final ObjectProvider<JdbcTemplate> jdbc;

    public LegacyParametersWarning(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        JdbcTemplate template = jdbc.getIfAvailable();
        if (template == null) {
            return;
        }
        Integer filas;
        try {
            filas = template.queryForObject("SELECT COUNT(*) FROM parametro_regla", Integer.class);
        } catch (DataAccessException e) {
            // La tabla no existe (base nueva o ya migrada): no hay nada que avisar.
            return;
        }
        if (filas != null && filas > 0) {
            log.warn("La tabla vieja parametro_regla tiene {} override(s) que ya no se leen: los parámetros son por regla "
                    + "y esas copias están en fábrica. Correr resources/migracion-parametros-por-regla.sql (copia cada "
                    + "override a las reglas que leen esa clave y borra la tabla).", filas);
        }
    }
}
