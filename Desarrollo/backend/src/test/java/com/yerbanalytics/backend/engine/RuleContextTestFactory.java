package com.yerbanalytics.backend.engine;

import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.model.SectorEntity;
import com.yerbanalytics.backend.model.ZonaEntity;

import java.time.Instant;
import java.util.List;

/**
 * Fábrica de {@link RuleContext} para uso en tests unitarios.
 *
 * <p>Centraliza la construcción de contextos de prueba con defaults razonables,
 * evitando que cada test repita boilerplate de configuración.
 */
public final class RuleContextTestFactory {

    private RuleContextTestFactory() {}

    /** Crea un contexto mínimo con los campos más comunes preconfigurados. */
    public static RuleContext basico(SectorEntity sector, ZonaEntity zona) {
        return new RuleContext(
                sector,
                zona,
                List.of(),
                List.of(),
                defaultConfig(),
                "ok",
                Instant.now(),
                false,       // sensorStale
                null,        // forecast
                false        // bloqueoManualActivo
        );
    }

    public static RuleContext conSensorStale(SectorEntity sector, ZonaEntity zona, boolean stale) {
        return new RuleContext(
                sector, zona, List.of(), List.of(), defaultConfig(),
                "ok", Instant.now(), stale, null, false);
    }

    public static RuleContext conForecast(SectorEntity sector, ZonaEntity zona, WeatherForecast forecast) {
        return new RuleContext(
                sector, zona, List.of(), List.of(), defaultConfig(),
                "ok", Instant.now(), false, forecast, false);
    }

    public static RuleContext conBloqueo(SectorEntity sector, ZonaEntity zona, boolean bloqueo) {
        return new RuleContext(
                sector, zona, List.of(), List.of(), defaultConfig(),
                "ok", Instant.now(), false, null, bloqueo);
    }

    /** ConfiguracionOperativaEntity con valores de prueba sensatos. */
    public static ConfiguracionOperativaEntity defaultConfig() {
        return new ConfiguracionOperativaEntity(
                1,
                120.0,  // riegoTiempoMaxSeg
                500.0,  // riegoVolMaxDiarioMl
                50.0,   // insumoDosisMax24hMl
                70.0,   // mediasombraAperturaMaxPct
                5,      // seguimientoLatenciaMin
                5.0,    // seguimientoDeltaMin
                "test",
                System.currentTimeMillis()
        );
    }

    /** SectorEntity con valores mínimos para tests. */
    public static SectorEntity sectorBasico(String id) {
        SectorEntity s = new SectorEntity();
        s.setId(id);
        s.setStatus("ok");
        s.setColor("#3FA06A");
        s.setStatusLabel("Saludable");
        s.setTip(id + " · Saludable");
        s.setReason("Sin alerta");
        s.setDiagnosisEstado("Sano");
        s.setDiagnosisConf(98.0);
        s.setDiagnosisSev("—");
        s.setActuadorValve("Cerrada");
        s.setActuadorPump("En espera");
        s.setActuadorShade(50);
        return s;
    }

    /** ZonaEntity mínima para tests. */
    public static ZonaEntity zonaBasica(String id) {
        ZonaEntity z = new ZonaEntity();
        z.setId(id);
        z.setName("Zona test");
        z.setSub("Test");
        z.setLastReadingTime(System.currentTimeMillis());
        return z;
    }
}
