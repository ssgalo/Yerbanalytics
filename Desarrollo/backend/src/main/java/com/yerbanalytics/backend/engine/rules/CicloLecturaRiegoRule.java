package com.yerbanalytics.backend.engine.rules;

import com.yerbanalytics.backend.engine.ActionType;
import com.yerbanalytics.backend.engine.ContextoRiego;
import com.yerbanalytics.backend.engine.Rule;
import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.RuleBranch;
import com.yerbanalytics.backend.engine.RuleContext;
import com.yerbanalytics.backend.engine.traza.Evaluacion;
import com.yerbanalytics.backend.engine.traza.Operador;

import java.util.List;

/**
 * Protección contra el riego en bucle: un sector recibe a lo sumo un riego por ciclo de lectura, y
 * ninguno mientras tiene uno en curso (design D5 de implement-reglas-riego).
 *
 * <p>{@code reglas_v2} se protege con la cadencia (una lectura cada 4 h, una decisión por lectura). El
 * nodo real publica cada 30 s, así que sin esta regla R-01 volvería a regar el sector apenas cierra la
 * válvula: la humedad sale de UN nodo testigo que puede estar en un sector que todavía no se regó.
 *
 * <p><b>Prioridad 2</b>, antes de todas las reglas que riegan (R-02 incluida: con lecturas de 4 h v2
 * tampoco regaría dos veces en un ciclo). Condición fija, no configurable: su único "umbral" es el
 * inicio del ciclo, que viaja en el {@link ContextoRiego}. Con {@link ContextoRiego#vacio()} (el barrido)
 * no corta.
 */
public class CicloLecturaRiegoRule implements Rule {

    private static final int PRIORITY = 2;
    private static final String NAME = "CicloLecturaRiegoRule";
    private static final double MS_POR_MINUTO = 60_000.0;

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
        return "🔁 Un riego por ciclo de lectura";
    }

    @Override
    public RuleBranch branch() {
        return RuleBranch.RIEGO;
    }

    @Override
    public List<RuleAction> evaluate(RuleContext ctx, Evaluacion ev) {
        ContextoRiego riego = ctx.riego();
        long ahora = ctx.now().toEpochMilli();

        // ¿Ya se regó desde que empezó este ciclo? (minutos desde el último riego ≤ minutos del ciclo)
        Double minutosDesdeRiego = riego.ultimoRiegoMs() == null ? null
                : (ahora - riego.ultimoRiegoMs()) / MS_POR_MINUTO;
        Double minutosDeCiclo = riego.inicioCiclo() == null ? null
                : (ahora - riego.inicioCiclo().toEpochMilli()) / MS_POR_MINUTO;
        boolean yaRegado = minutosDesdeRiego != null && minutosDeCiclo != null
                && ev.compararFijo("Minutos desde el último riego (contra los del ciclo en curso)",
                        minutosDesdeRiego, Operador.LE, minutosDeCiclo);
        if (minutosDesdeRiego == null || minutosDeCiclo == null) {
            ev.compararFijo("Minutos desde el último riego (contra los del ciclo en curso)", null, Operador.LE, 0.0);
        }

        // ¿Hay un riego abierto? (segundos que le quedan > 0)
        double segundosRestantes = riego.riegoEnCursoHastaMs() == null ? 0.0
                : Math.max(0.0, (riego.riegoEnCursoHastaMs() - ahora) / 1000.0);
        boolean enCurso = ev.compararFijo("Segundos restantes del riego en curso", segundosRestantes, Operador.GT, 0.0);

        if (yaRegado) {
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                    "El sector ya se regó en este ciclo de lectura (último riego a las %s, el ciclo empezó a las %s). "
                            + "Un riego por sector y ciclo.",
                    RiegoRuleSupport.hora(riego.ultimoRiegoMs()), RiegoRuleSupport.hora(riego.inicioCiclo()))));
        }
        if (enCurso) {
            return List.of(RuleAction.of(ActionType.ABORT_RIEGO, NAME, String.format(
                    "El sector tiene un riego en curso hasta las %s: no se riega de nuevo.",
                    RiegoRuleSupport.hora(riego.riegoEnCursoHastaMs()))));
        }
        return List.of(RuleAction.noopInfo(NAME, "El sector no regó en este ciclo de lectura ni tiene un riego en curso."));
    }
}
