package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosSeguridad;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de seguridad por antigüedad de telemetría (HU-02 CA-03/04).
 *
 * <p><b>Prioridad:</b> 1 (segunda en correr, después de {@link ManualLockRule}).
 *
 * <p><b>Condición:</b> la última lectura de la macro-zona ({@code zona.lastReadingTime}) es más
 * vieja que {@code seguridad.antiguedad-max-lectura} (catálogo de parámetros), o no hay lectura
 * (queda como {@code SIN_DATO} en la traza y bloquea igual).
 *
 * <p><b>Acción si se cumple:</b> {@code ABORT_RIEGO} — el motor detiene toda actuación
 * de riego para el sector. Regar sin lectura válida puede causar encharcamiento o
 * daño físico a los plantines.
 *
 * <p><b>Acción si no se cumple:</b> {@code NOOP_INFO} — confirmación de que el sensor
 * reportó a tiempo; la cadena continúa evaluando las reglas ejecutoras.
 */
@Component
public class StaleSensorRule implements Rule {

    private static final int PRIORITY = 1;
    private static final String NAME = "StaleSensorRule";

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String label() {
        return "📵 Sensor sin datos recientes";
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double antiguedad = antiguedadSegundos(ctx);
        boolean vieja = ev.comparar("Antigüedad de la última lectura", antiguedad, Operador.GT,
                ParametrosSeguridad.ANTIGUEDAD_MAX_LECTURA);
        // Sin lectura no hay nada que comparar, pero tampoco se puede regar a ciegas.
        if (antiguedad == null || vieja) {
            String zonaId = ctx.zona() != null ? ctx.zona().getId() : "desconocida";
            String motivo = String.format(
                    "El nodo testigo de la macro-zona %s no reportó dentro del umbral " +
                    "de antigüedad configurado. Actuación autónoma anulada por seguridad.",
                    zonaId);
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, motivo));
        }

        return List.of(RuleAction.noopInfo(NAME,
                "Telemetría fresca: el nodo reportó dentro del umbral configurado."));
    }

    /** Segundos desde la última lectura de la zona, o {@code null} si no hay zona o nunca reportó. */
    private static Double antiguedadSegundos(RuleContext ctx) {
        if (ctx.zona() == null || ctx.zona().getLastReadingTime() == null) {
            return null;
        }
        return (ctx.now().toEpochMilli() - ctx.zona().getLastReadingTime()) / 1000.0;
    }
}
