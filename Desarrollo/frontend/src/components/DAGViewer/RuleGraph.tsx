/* ============================================================
   RuleGraph: el DAG del motor de reglas con la última evaluación de un sector (Inspector).

   Pinta cada regla con su estado según la traza y muestra "recibido vs. umbral". El grafo de una
   acción del historial es otro (`HistoryGraph`): el historial no guarda la evaluación entera.
   ============================================================ */
import { ReactFlowProvider } from '@xyflow/react';
import type { DagSchema, TrazaEvaluacion } from '@/types/domain';
import { TrazaGraph } from './TrazaGraph';

interface RuleGraphProps {
  schema: DagSchema;
  /** La última evaluación del motor: el estado de cada nodo sale de sus datos estructurados. */
  traza: TrazaEvaluacion;
  /** Regla elegida. */
  seleccionada?: string | null;
  onSeleccionar?: (ruleId: string | null) => void;
}

export function RuleGraph({ schema, traza, seleccionada, onSeleccionar }: RuleGraphProps) {
  return (
    <ReactFlowProvider>
      <TrazaGraph schema={schema} traza={traza} seleccionada={seleccionada ?? null} onSeleccionar={onSeleccionar} />
    </ReactFlowProvider>
  );
}
