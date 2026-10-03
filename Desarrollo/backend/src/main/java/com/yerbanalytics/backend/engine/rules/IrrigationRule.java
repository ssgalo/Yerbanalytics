package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.parametros.DefinicionParametro;
import com.yerbanalytics.backend.engine.parametros.ParametrosRiego;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;
import com.yerbanalytics.backend.model.ConfiguracionOperativaEntity;
import com.yerbanalytics.backend.repository.HistorialRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Regla de riego autónomo por humedad de sustrato (HU-06).
 *
 * <p><b>Prioridad:</b> 10 (ejecutora).
 *
 * <p><b>Condición:</b> humedad de sustrato actual menor al umbral {@code riego.umbral-humedad}
 * del catálogo de parámetros (ya no depende de la banda ideal de la métrica).
 *
 * <p><b>Guards de límites operativos (HU-15 CA-04):</b>
 * <ul>
 *   <li>Si el sector ya regó en las últimas 24 h, se bloquea por volumen diario.</li>
 *   <li>El tiempo máximo de riego ({@code riego.tiempo-max-apertura}) se adjunta al motivo
 *       para que el {@code ActionExecutor} lo considere al enviar el comando MQTT.</li>
 * </ul>
 *
 * <p><b>Acción si se cumple:</b> {@code ACTIVAR_VALVULA} — el {@link com.yerbanalytics.backend.engine.ActionExecutor}
 * abrirá la electroválvula y registrará el riego en el historial.
 *
 * <p><b>Acción si no se cumple:</b> {@code NOOP_INFO} — Registro de Inacción
 * para que el usuario sepa que el sistema evaluó la condición y no la encontró necesaria.
 */
@Component
public class IrrigationRule implements Rule {

    private static final int PRIORITY = 10;
    private static final String NAME = "IrrigationRule";
    private static final long MS_EN_24H = 24L * 60 * 60 * 1000;

    private final HistorialRepository historialRepository;

    public IrrigationRule(HistorialRepository historialRepository) {
        this.historialRepository = historialRepository;
    }

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
        return "💦 Riego";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<DefinicionParametro> parametros() {
        return List.of(ParametrosRiego.UMBRAL_HUMEDAD,
                ParametrosRiego.MAX_RIEGOS_24H_SECTOR,
                ParametrosRiego.TIEMPO_MAX_APERTURA);
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        Double humSus = ctx.metricRaw("humSus");
        boolean bajoElUmbral = ev.comparar("Humedad de sustrato", humSus, Operador.LT, ParametrosRiego.UMBRAL_HUMEDAD);
        double umbral = ev.numero(ParametrosRiego.UMBRAL_HUMEDAD);

        // Sin lectura disponible: no actuar (el StaleSensorRule lo manejará)
        if (humSus == null || humSus.isNaN()) {
            return List.of(RuleAction.noopInfo(NAME,
                    "Sin lectura de humedad de sustrato — no se puede evaluar riego."));
        }

        if (!bajoElUmbral) {
            String motivo = String.format(
                    "Humedad de sustrato %.0f%% dentro del rango aceptable (umbral: %.0f%%). No se riega.",
                    humSus, umbral);
            return List.of(RuleAction.noopInfo(NAME, motivo));
        }

        // Guard de límite operativo: verificar volumen diario (HU-15 CA-04)
        ConfiguracionOperativaEntity config = ctx.config();
        if (config != null) {
            long desde = System.currentTimeMillis() - MS_EN_24H;
            long riegosEn24h = historialRepository.countByTipoAndSectorAndPeriod(
                    ctx.sector().getId(), "Riego", desde);

            if (ev.comparar("Riegos en las últimas 24 h", (double) riegosEn24h, Operador.GE,
                    ParametrosRiego.MAX_RIEGOS_24H_SECTOR)) {
                String motivoBloqueo = String.format(
                        "Humedad de sustrato %.0f%% bajo el umbral (%.0f%%), pero el sector %s " +
                        "ya recibió %d riego(s) en las últimas 24 h (volumen diario máx: %.0f ml). " +
                        "Riego autónomo bloqueado por límite operativo.",
                        humSus, umbral, ctx.sector().getId(), riegosEn24h, config.getRiegoVolMaxDiarioMl());
                return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, motivoBloqueo));
            }

            // Adjuntar tiempo máximo al motivo para el ActionExecutor (futura implementación MQTT)
            String motivo = String.format(
                    "Humedad de sustrato %.0f%% bajo el umbral mínimo de %.0f%%. " +
                    "[tiempo-max-seg=%.0f]",
                    humSus, umbral, ev.numero(ParametrosRiego.TIEMPO_MAX_APERTURA));
            return List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, motivo));
        }

        // Sin configuración: actuar con motivo simple
        String motivo = String.format("Humedad de sustrato %.0f%% bajo el umbral mínimo de %.0f%%.",
                humSus, umbral);
        return List.of(RuleAction.of(ActionType.ACTIVAR_VALVULA, NAME, motivo));
    }
}
