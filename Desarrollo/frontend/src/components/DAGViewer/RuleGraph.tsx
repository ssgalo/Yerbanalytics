/* ============================================================
   RuleGraph: visualizador interactivo del DAG del motor de reglas.

   Recibe el esquema base (topología estática) y un arreglo de ActionRecord
   (activeEvents). A partir de ellos, filtra el esquema para mostrar solo
   las ramas relevantes al ciclo, y pinta el DAG con los colores correspondientes.
   ============================================================ */
import { useCallback, useEffect, useMemo } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  MarkerType,
  Position,
  type Node,
  type Edge,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { ActionRecord, DagSchema } from '@/types/domain';
import styles from './RuleGraph.module.css';

// -----------------------------------------------------------------------
// Constantes de estilo por estado del nodo
// -----------------------------------------------------------------------

const STYLE_IDLE: React.CSSProperties = {
  background: '#1e293b',
  border: '1.5px solid #334155',
  color: '#94a3b8',
  borderRadius: 10,
  fontSize: 12,
  fontWeight: 500,
  width: 180,
};

const STYLE_PASSED: React.CSSProperties = {
  background: '#14532d',
  border: '2px solid #22c55e',
  color: '#bbf7d0',
  borderRadius: 10,
  fontSize: 12,
  fontWeight: 600,
  width: 180,
};

const STYLE_BLOCKED: React.CSSProperties = {
  background: '#450a0a',
  border: '2px solid #ef4444',
  color: '#fecaca',
  borderRadius: 10,
  fontSize: 12,
  fontWeight: 700,
  width: 180,
  boxShadow: '0 0 12px rgba(239,68,68,0.4)',
};

const STYLE_POSTPONED: React.CSSProperties = {
  background: '#422006',
  border: '2px solid #f59e0b',
  color: '#fde68a',
  borderRadius: 10,
  fontSize: 12,
  fontWeight: 700,
  width: 180,
  boxShadow: '0 0 12px rgba(245,158,11,0.4)',
};

const STYLE_ACTION: React.CSSProperties = {
  background: '#0c2a6b',
  border: '2px solid #3b82f6',
  color: '#bfdbfe',
  borderRadius: 10,
  fontSize: 12,
  fontWeight: 700,
  width: 180,
  boxShadow: '0 0 12px rgba(59,130,246,0.4)',
};

const STYLE_SKIPPED: React.CSSProperties = {
  background: '#0f172a',
  border: '1.5px dashed #1e293b',
  color: '#334155',
  borderRadius: 10,
  fontSize: 12,
  width: 180,
};

const STYLE_START: React.CSSProperties = {
  background: '#1e3a5f',
  border: '1.5px solid #3b82f6',
  color: '#93c5fd',
  borderRadius: 20,
  fontSize: 11,
  fontWeight: 600,
  width: 160,
};

const STYLE_ABORT_TERMINAL: React.CSSProperties = {
  background: '#1c0606',
  border: '1.5px solid #7f1d1d',
  color: '#f87171',
  borderRadius: 10,
  fontSize: 11,
  width: 160,
};

const STYLE_SUCCESS_TERMINAL: React.CSSProperties = {
  background: '#052e16',
  border: '1.5px solid #15803d',
  color: '#4ade80',
  borderRadius: 10,
  fontSize: 11,
  width: 160,
};

// -----------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------

/** Extrae el nombre de la regla desde el campo `lectura` de un ActionRecord. */
function extractRuleName(record: ActionRecord): string | null {
  const match = record.lectura?.match(/Ciclo de evaluaci[oó]n:\s*(\w+)/i);
  if (match) return match[1];
  switch (record.tipo) {
    case 'Riego':      return 'IrrigationRule';
    case 'Insumo':     return 'SupplyRule';
    case 'Mediasombra':
    case 'Sombra':     return 'ShadingRule';
    default:           return null;
  }
}

/** Determina si el evento es una acción de éxito (actuó físicamente). */
function isActionEvent(record: ActionRecord): boolean {
  return ['Riego', 'Insumo', 'Mediasombra', 'Sombra'].includes(record.tipo);
}

/** Determina si el evento es un postpone de riego. */
function isPostpone(record: ActionRecord): boolean {
  return record.lectura?.includes('POSTPONE') === true ||
         record.decision?.toLowerCase().includes('posterg') === true ||
         record.decision?.toLowerCase().includes('lluvia inminente') === true;
}

/** Calcula el layout de posición X,Y para cada nodo soportando ramas horizontales. */
function computeLayout(schema: DagSchema): Record<string, { x: number; y: number }> {
  const positions: Record<string, { x: number; y: number }> = {};
  const ROW_HEIGHT = 90;

  // Calculamos anchos dinámicos para centrar ramas que sobrevivieron al filtro
  const branches = ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'].filter(b => 
    schema.nodes.some(n => n.branch === b)
  );
  
  const COLUMN_WIDTH = 300;
  const startX = branches.length > 0 ? (branches.length * COLUMN_WIDTH) / 2 : 150;
  
  const BRANCH_X: Record<string, number> = { GLOBAL: startX };
  branches.forEach((b, i) => {
    BRANCH_X[b] = 50 + (i * COLUMN_WIDTH);
  });

  const globalNodes = schema.nodes.filter((n) => n.branch === 'GLOBAL' && n.type === 'default');
  let currentY = 0;

  if (schema.nodes.some(n => n.id === 'start')) {
    positions['start'] = { x: startX, y: currentY };
    currentY += ROW_HEIGHT;
  }

  globalNodes.forEach((node) => {
    positions[node.id] = { x: startX, y: currentY };
    currentY += ROW_HEIGHT;
  });

  if (schema.nodes.some(n => n.id === 'abort-GLOBAL')) {
    positions['abort-GLOBAL'] = { x: startX + 180, y: ROW_HEIGHT };
  }

  branches.forEach((branch) => {
    const branchNodes = schema.nodes.filter((n) => n.branch === branch && n.type === 'default');
    const baseX = BRANCH_X[branch];
    let branchY = currentY; 

    branchNodes.forEach((node) => {
      positions[node.id] = { x: baseX, y: branchY };
      branchY += ROW_HEIGHT;
    });

    if (schema.nodes.some(n => n.id === `abort-${branch}`)) {
      positions[`abort-${branch}`] = { x: baseX + 180, y: currentY };
    }
    if (schema.nodes.some(n => n.id === `success-${branch}`)) {
      positions[`success-${branch}`] = { x: baseX, y: branchY };
    }
  });

  return positions;
}

// -----------------------------------------------------------------------
// Componente principal
// -----------------------------------------------------------------------

interface RuleGraphProps {
  schema: DagSchema;
  activeEvents: ActionRecord[];
}

function RuleGraphInner({ schema, activeEvents }: RuleGraphProps) {
  const filteredSchema = useMemo(() => {
    // Excluir nodos abort-* (terminales de bloqueo de rama) — solo aportan ruido visual.
    // La información de bloqueo ya está representada en el nodo que generó el bloqueo
    // (coloreado en rojo) y en las aristas correspondientes.
    const nodes = schema.nodes.filter((n) => !n.id.startsWith('abort-'));
    const edges = schema.edges.filter(
      (e) => !e.target.startsWith('abort-') && !e.source.startsWith('abort-')
    );
    return { nodes, edges };
  }, [schema, activeEvents]);

  const positions = useMemo(() => computeLayout(filteredSchema), [filteredSchema]);

  const buildNodes = useCallback((): Node[] => {
    return filteredSchema.nodes.map((n) => {
      const pos = positions[n.id] ?? { x: 0, y: 0 };
      let style: React.CSSProperties = STYLE_IDLE;
      let nodeLabel = n.label;

      if (n.id === 'start') {
        style = STYLE_START;
      } else if (n.id.startsWith('abort-')) {
        style = STYLE_ABORT_TERMINAL;
      } else if (n.id.startsWith('success-')) {
        style = STYLE_SUCCESS_TERMINAL;
        const branch = n.id.replace('success-', '');
        
        // Calcular si la rama tuvo acción o si fue bloqueada para cambiar el texto final
        const hadAction = activeEvents.some(e => {
          const rName = extractRuleName(e);
          const evNode = filteredSchema.nodes.find(x => x.id === rName);
          return evNode?.branch === branch && isActionEvent(e);
        });
        const hadBlock = activeEvents.some(e => {
          const rName = extractRuleName(e);
          const evNode = filteredSchema.nodes.find(x => x.id === rName);
          return evNode?.branch === branch && (e.tipo.startsWith('ABORT') || e.decision?.toLowerCase().includes('posterg'));
        });

        if (branch === 'RIEGO') nodeLabel = hadAction ? 'Riego efectuado' : 'No se regó';
        if (branch === 'INSUMO') nodeLabel = hadAction ? 'Dosificación efectuada' : 'No se dosificó';
        if (branch === 'MEDIASOMBRA') nodeLabel = hadAction ? 'Mediasombra ajustada' : 'No se movió mediasombra';

        if (hadBlock) {
          style = STYLE_SKIPPED; // Si la rama se bloqueó, nunca llegó al success
        }
      } else if (activeEvents && activeEvents.length > 0) {
        let isPassed = false;
        let isBlocked = false;
        let isPostponed = false;
        let isAction = false;

        activeEvents.forEach((e) => {
          const ruleName = extractRuleName(e);
          const evNode = filteredSchema.nodes.find((x) => x.id === ruleName);
          if (!evNode) return;

          const isRelevantBranch = n.branch === 'GLOBAL' || n.branch === evNode.branch;
          if (isRelevantBranch) {
            if (n.id === ruleName) {
              if (isActionEvent(e)) isAction = true;
              else if (isPostpone(e)) isPostponed = true;
              else if (
                e.tipo.startsWith('ABORT') ||
                e.accion?.includes('bloqueada') ||
                (e.tipo === 'Info' && e.lectura?.includes('Sensor sin datos'))
              ) {
                isBlocked = true;
              } else {
                isPassed = true;
              }
            } else if (n.priority < evNode.priority) {
              isPassed = true;
            }
          }
        });

        if (isAction) style = STYLE_ACTION;
        else if (isPostponed) style = STYLE_POSTPONED;
        else if (isBlocked) style = STYLE_BLOCKED;
        else if (isPassed) style = STYLE_PASSED;
        else style = STYLE_SKIPPED;
      }

      return {
        id: n.id,
        data: { label: nodeLabel },
        position: pos,
        type: n.type === 'input' ? 'input' : n.type === 'output' ? 'output' : 'default',
        sourcePosition: Position.Bottom,
        targetPosition: Position.Top,
        style,
      };
    });
  }, [filteredSchema, positions, activeEvents]);

  const buildEdges = useCallback((): Edge[] => {
    return filteredSchema.edges.map((e) => {
      let animated = false;
      let strokeColor = '#334155';

      if (activeEvents && activeEvents.length > 0) {
        const sourceNode = filteredSchema.nodes.find((n) => n.id === e.source);
        const targetNode = filteredSchema.nodes.find((n) => n.id === e.target);
        const srcPriority = sourceNode?.priority ?? 0;
        const tgtPriority = targetNode?.priority ?? 0;

        activeEvents.forEach((ev) => {
          const ruleName = extractRuleName(ev);
          const evNode = filteredSchema.nodes.find((x) => x.id === ruleName);
          if (!evNode) return;

          const isRelevantBranch =
            sourceNode && (sourceNode.branch === 'GLOBAL' || sourceNode.branch === evNode.branch);

          if (isRelevantBranch) {
            const isBlock = ev.tipo.startsWith('ABORT') || ev.accion?.includes('bloqueada') || (ev.tipo === 'Info' && ev.lectura?.includes('Sensor sin datos'));
            const isPass = !isBlock && !isActionEvent(ev); // e.g. NOOP_INFO

            if (e.label === 'Continúa' && srcPriority < evNode.priority && tgtPriority <= evNode.priority) {
              animated = true;
              strokeColor = '#22c55e';
            } else if (
              e.source === ruleName &&
              (e.target.startsWith('abort-') || e.label === 'Bloquea') &&
              isBlock
            ) {
              animated = true;
              strokeColor = isPostpone(ev) ? '#f59e0b' : '#ef4444';
            } else if (e.target.startsWith('success-') && isActionEvent(ev) && e.source === ruleName) {
              animated = true;
              strokeColor = '#3b82f6';
            } else if (e.source === ruleName && e.target.startsWith('success-') && isPass) {
              // Si la regla terminal (RiegoRule) devolvió NOOP_INFO, llega al final verde.
              animated = true;
              strokeColor = '#22c55e';
            }
          }
        });
      }

      return {
        id: e.id,
        source: e.source,
        target: e.target,
        label: e.label,
        animated,
        style: { stroke: strokeColor, strokeWidth: animated ? 2 : 1 },
        markerEnd: { type: MarkerType.ArrowClosed, color: strokeColor },
        labelStyle: { fontSize: 10, fill: '#64748b' },
        labelBgStyle: { fill: '#0f172a', fillOpacity: 0.9 },
      };
    });
  }, [filteredSchema, activeEvents]);

  const [nodes, setNodes, onNodesChange] = useNodesState(buildNodes());
  const [edges, setEdges, onEdgesChange] = useEdgesState(buildEdges());

  useEffect(() => {
    setNodes(buildNodes());
    setEdges(buildEdges());
  }, [buildNodes, buildEdges, setNodes, setEdges]);

  const maxY = useMemo(() => {
    let max = 0;
    for (const id in positions) {
      if (positions[id].y > max) max = positions[id].y;
    }
    return Math.max(380, max + 100);
  }, [positions]);

  return (
    <div className={styles.canvas} style={{ height: maxY }}>
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        fitView
        fitViewOptions={{ padding: 0.2, minZoom: 0.9, maxZoom: 1.1 }}
        minZoom={0.5}
        maxZoom={1.5}
        panOnScroll={true}
        zoomOnScroll={false}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={false}
        colorMode="dark"
        style={{ width: '100%', height: '100%' }}
        proOptions={{ hideAttribution: true }}
      >
        <Background color="#1e293b" gap={20} size={1} />
        <Controls showInteractive={false} />
        <MiniMap nodeColor={(n) => (n.style as React.CSSProperties)?.borderColor ?? '#334155'} />
      </ReactFlow>

      {/* Leyenda */}
      <div className={styles.legend}>
        <span className={`${styles.dot} ${styles.dotPassed}`} /> Pasó
        <span className={`${styles.dot} ${styles.dotBlocked}`} /> Bloqueó
        <span className={`${styles.dot} ${styles.dotPostpone}`} /> Pospuso
        <span className={`${styles.dot} ${styles.dotAction}`} /> Accionó
        <span className={`${styles.dot} ${styles.dotSkipped}`} /> No evaluado
      </div>
    </div>
  );
}

export function RuleGraph(props: RuleGraphProps) {
  return (
    <ReactFlowProvider>
      <RuleGraphInner {...props} />
    </ReactFlowProvider>
  );
}
