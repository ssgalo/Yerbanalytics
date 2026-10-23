/* ============================================================
   HistoryGraph: el DAG del motor para una acción del historial, en claro.

   Misma disposición que el resto de los grafos del motor (`computeLayout`): inicio arriba, la
   cadena global y una columna por rama. Cada regla activa se ve ejecutada, no ejecutada o como la
   que decidió; con `onlyExecuted` quedan sólo las que corrieron. Se recorre arrastrando y se acerca
   con la rueda o con los controles.
   ============================================================ */
import { memo, useMemo } from 'react';
import {
  Background,
  BaseEdge,
  Controls,
  Handle,
  MarkerType,
  Position,
  ReactFlow,
  ReactFlowProvider,
  type Edge,
  type EdgeProps,
  type Node,
  type NodeProps,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { ActionRecord, DagSchema, RuleNode } from '@/types/domain';
import { computeLayout } from './layoutDag';
import { historyGraph, orthogonalPath, type HistoryNode } from './historyGraphModel';
import styles from './HistoryGraph.module.css';

/** Ancho de cada tipo de nodo; `computeLayout` ubica todos como si midieran lo que una regla. */
const WIDTH: Record<HistoryNode['kind'], number> = { start: 170, lane: 140, rule: 180, end: 170 };
const RULE_WIDTH = WIDTH.rule;

type HistoryNodeData = Pick<HistoryNode, 'label' | 'kind' | 'state'>;

const HistoryNodeView = memo(function HistoryNodeView({ data }: NodeProps<Node<HistoryNodeData>>) {
  return (
    <div
      className={styles.node}
      data-kind={data.kind}
      data-state={data.state}
      style={{ width: WIDTH[data.kind] }}
    >
      {data.kind !== 'start' && <Handle type="target" position={Position.Top} className={styles.handle} />}
      {data.label}
      {data.kind !== 'end' && <Handle type="source" position={Position.Bottom} className={styles.handle} />}
    </div>
  );
});

const nodeTypes = { history: HistoryNodeView };

const OrthogonalEdge = memo(function OrthogonalEdge({ id, sourceX, sourceY, targetX, targetY, style, markerEnd }: EdgeProps) {
  return <BaseEdge id={id} path={orthogonalPath(sourceX, sourceY, targetX, targetY)} style={style} markerEnd={markerEnd} />;
});

const edgeTypes = { orthogonal: OrthogonalEdge };

interface HistoryGraphProps {
  schema: DagSchema;
  event: ActionRecord;
  /** Sólo las reglas que se ejecutaron y la que decidió. */
  onlyExecuted?: boolean;
}

function HistoryGraphInner({ schema, event, onlyExecuted = false }: HistoryGraphProps) {
  const { nodes, edges } = useMemo(() => {
    const graph = historyGraph(schema, event, onlyExecuted);
    // El layout de siempre, con los rótulos de rama como primer nodo de su columna.
    const layoutSchema: DagSchema = {
      nodes: graph.nodes.map(
        (n): RuleNode => ({
          id: n.id,
          label: n.label,
          type: n.kind === 'start' ? 'input' : n.kind === 'end' ? 'output' : 'default',
          priority: 0,
          branch: n.branch,
          parametros: [],
        }),
      ),
      edges: [],
    };
    const positions = computeLayout(layoutSchema);

    const flowNodes: Node<HistoryNodeData>[] = graph.nodes.map((n) => {
      const p = positions[n.id] ?? { x: 0, y: 0 };
      return {
        id: n.id,
        type: 'history',
        // Centrado en la columna de las reglas
        position: { x: p.x + (RULE_WIDTH - WIDTH[n.kind]) / 2, y: p.y },
        data: { label: n.label, kind: n.kind, state: n.state },
        draggable: false,
        selectable: false,
      };
    });

    // Las ejecutadas van al final: se dibujan encima de las punteadas con las que comparten tramo.
    const ordered = [...graph.edges].sort((a, b) => Number(a.executed) - Number(b.executed));
    const flowEdges: Edge[] = ordered.map((e) => {
      const color = e.executed ? 'var(--brand)' : 'var(--line-field)';
      return {
        id: e.id,
        source: e.source,
        target: e.target,
        type: 'orthogonal',
        style: { stroke: color, strokeWidth: e.executed ? 2 : 1.25, strokeDasharray: e.executed ? undefined : '4 4' },
        markerEnd: { type: MarkerType.ArrowClosed, color, width: 16, height: 16 },
      };
    });

    return { nodes: flowNodes, edges: flowEdges };
  }, [schema, event, onlyExecuted]);

  return (
    <div className={styles.canvas}>
      <ReactFlow
        // Al cambiar de vista se vuelve a encuadrar
        key={String(onlyExecuted)}
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        fitView
        fitViewOptions={{ padding: 0.12, maxZoom: 1 }}
        minZoom={0.3}
        maxZoom={1.6}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="var(--line)" gap={20} size={1.4} />
        <Controls showInteractive={false} position="bottom-right" className={styles.controls} />
      </ReactFlow>

      <div className={styles.legend}>
        <span>
          <i className={styles.swatch} data-state="executed" /> Ejecutada
        </span>
        {!onlyExecuted && (
          <span>
            <i className={styles.swatch} data-state="skipped" /> No ejecutada
          </span>
        )}
        <span>
          <i className={styles.swatch} data-state="decided" /> Decidió la acción
        </span>
      </div>
    </div>
  );
}

export function HistoryGraph(props: HistoryGraphProps) {
  return (
    <ReactFlowProvider>
      <HistoryGraphInner {...props} />
    </ReactFlowProvider>
  );
}
