package com.yerbanalytics.backend.engine.weather;

import com.yerbanalytics.backend.engine.DetalleAlerta;
import com.yerbanalytics.backend.engine.NivelAlerta;
import com.yerbanalytics.backend.service.HistorialService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Avisa cuando el sistema entra y sale del modo sin pronóstico (reglas_v2 §10, HU-13): un {@code INFO} "Sin pronóstico: el sistema
 * decide con las lecturas y supuestos conservadores" al entrar y otro al volver, uno por evento. Es global (el pronóstico es uno
 * solo para el vivero), no por zona. Sin esto, la pérdida del pronóstico pasaba en silencio: las reglas seguían decidiendo con su
 * supuesto y nadie lo sabía.
 *
 * <p>Pasar de vencido a ausente (o al revés) no es un evento: el sistema sigue sin pronóstico. Si arranca sin conexión, avisa una vez
 * al primer chequeo. Cada chequeo también pide el pronóstico sin esperar, para que el refresco siga intentándose aunque no llegue
 * telemetría.
 */
@Component
public class ForecastModeMonitor {

    public static final String RULE_NAME = "ForecastModeMonitor";

    private static final String ZONE_ID = "—";
    private static final String ZONE_NAME = "Sistema";

    private final WeatherService weather;
    private final HistorialService history;
    private final Clock clock;
    /** Si el último chequeo vio al sistema sin pronóstico; {@code null} antes del primero. */
    private Boolean degraded;

    public ForecastModeMonitor(WeatherService weather, HistorialService history, Clock clock) {
        this.weather = weather;
        this.history = history;
        this.clock = clock;
    }

    /** El primer chequeo espera a que el precalentamiento del arranque haya tenido su oportunidad. */
    @Scheduled(initialDelayString = "${yerbanalytics.weather.monitor-interval-ms:60000}",
            fixedDelayString = "${yerbanalytics.weather.monitor-interval-ms:60000}")
    public synchronized void check() {
        weather.getForecastSinEspera();
        boolean nowDegraded = weather.status().isDegraded();
        boolean entered = nowDegraded && !Boolean.TRUE.equals(degraded);
        boolean left = !nowDegraded && Boolean.TRUE.equals(degraded);
        degraded = nowDegraded;
        if (entered) {
            notifyInfo("Sin pronóstico: el sistema decide con las lecturas y supuestos conservadores (no llueve, sin UV pronosticado).");
        } else if (left) {
            notifyInfo("Pronóstico recuperado: las reglas vuelven a usarlo.");
        }
    }

    private void notifyInfo(String text) {
        history.registrarAlerta(ZONE_ID, ZONE_NAME, RULE_NAME, new DetalleAlerta(NivelAlerta.INFO, text), clock.millis());
    }
}
