package com.yerbanalytics.backend.engine.zone;

import com.yerbanalytics.backend.engine.RuleAction;
import com.yerbanalytics.backend.engine.overrides.DemoControllable;
import com.yerbanalytics.backend.engine.parametros.ConsumidorParametros;
import com.yerbanalytics.backend.engine.traza.Evaluacion;

import java.util.List;

/**
 * Una regla que decide sobre una <b>macro-zona</b> entera y no sobre un sector: el plan de nutrición es de la MZ y la CE
 * que lo adelanta también (hay un solo nodo testigo por zona). El {@link ZoneRuleEngine} las corre una vez por zona, al
 * cierre de la evaluación de sus sectores.
 *
 * <p>Mismo reparto que las reglas por sector: la regla <b>decide</b> y devuelve acciones; quien las ejecuta es el
 * {@link ZoneActionExecutor}. Es {@link DemoControllable}, así que el simulador la apaga y la acelera por
 * {@code /api/demo/rules}, y {@link ConsumidorParametros}, así que sus umbrales viven en el catálogo.
 *
 * <p>Las prioridades se comparten con las reglas por sector en {@code /api/demo/rules} y en el grafo del Inspector:
 * por eso son únicas también entre ambos grupos.
 */
public interface ZoneRule extends ConsumidorParametros, DemoControllable {

    /** Etiqueta legible para el agrónomo (Inspector y catálogo de parámetros). */
    @Override
    String label();

    /**
     * Evalúa la regla contra el estado de la zona. Los umbrales se leen y se comparan a través de la {@link Evaluacion}:
     * decidir y registrar son el mismo gesto.
     *
     * @return acciones a ejecutar; nunca {@code null}, puede ser vacía
     */
    List<RuleAction> evaluate(ZoneRuleContext ctx, Evaluacion ev);
}
