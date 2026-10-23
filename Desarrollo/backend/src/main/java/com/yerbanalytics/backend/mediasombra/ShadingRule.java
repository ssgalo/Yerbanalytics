package com.yerbanalytics.backend.mediasombra;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.DetalleMediasombra;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosMediasombra;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.engine.weather.WeatherForecast;
import com.yerbanalytics.backend.engine.zone.ZoneRule;
import com.yerbanalytics.backend.engine.zone.ZoneRuleContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Lo reactivo de la mediasombra de la macro-zona, en el pulso con motor: hoy, cerrarla ante un pico de radiación UV.
 *
 * <p>Abrir y cerrar según el plan de rustificación (o el horario por defecto) <b>no es del motor</b>: lo hace el
 * {@link HorarioMediasombraService} en los bordes de la ventana. Esta regla puede mover la malla en cualquier momento,
 * independientemente del plan; el horario no la corrige después (la vuelve a abrir el próximo borde de apertura).
 *
 * <p>La malla es binaria: el firmware enrolla (abre) con una apertura &ge; 50 y despliega (cierra) por debajo.
 *
 * <p><b>Prioridad:</b> 14 — después de la guarda de la rama ({@link ShadeFaultRule}).
 *
 * <p><b>Pico de UV:</b> si el índice UV pronosticado alcanza {@code mediasombra.uv-umbral}, ordena cerrarla <b>cada vez
 * que el pico está activo</b>, aunque la zona diga que ya está cerrada: ese valor es sólo lo último que mandó el backend,
 * y el nodo pudo reiniciarse o moverla una secuencia de Demo Expo. El firmware ignora un SET a su posición actual
 * (contesta {@code sin_cambio}), así que reenviarlo no hace daño.
 */
@Component
public class ShadingRule implements ZoneRule {

    private static final int PRIORITY = 14;
    public static final String NAME = "ShadingRule";

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
        return "Control de mediasombra";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.MEDIASOMBRA;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosMediasombra.UV_UMBRAL);
    }

    @Override
    public List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev) {
        WeatherForecast forecast = ctx.forecast();
        boolean picoUv = ev.comparar("Índice UV pronosticado", forecast != null ? forecast.uvIndex() : null,
                Operador.GE, ParametrosMediasombra.UV_UMBRAL);
        if (picoUv) {
            String motivo = String.format(Locale.ROOT,
                    "Mediasombra cerrada por pico de radiación UV (índice %.1f ≥ umbral %.1f).",
                    forecast.uvIndex(), ev.numero(ParametrosMediasombra.UV_UMBRAL));
            return List.of(RuleAction.of(ActionType.MOVER_MEDIASOMBRA, NAME, motivo, new DetalleMediasombra(false)));
        }
        return List.of(RuleAction.noopInfo(NAME,
                "Sin pico de UV: la mediasombra sigue el horario de la macro-zona (plan de rustificación u horario por defecto)."));
    }
}
