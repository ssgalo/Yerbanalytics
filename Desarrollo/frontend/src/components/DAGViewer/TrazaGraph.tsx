/* ============================================================
   DAG del motor en modo traza (Inspector).

   El estado de cada nodo sale de `trazaANodos` —datos estructurados de la evaluación— y los
   nodos de regla son `ReglaNode`, que muestra "recibido vs. umbral". Con los mismos nodos y
   aristas del esquema, sólo cambia cómo se pintan; el layout lo comparten ambos modos.
   ============================================================ */
import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import {
  Background,
  Controls,
  MarkerType,
  Position,
  ReactFlow,
  useReactFlow,
  type Edge,
  type Node,
  type NodeMouseHandler,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import type { DagSchema, TrazaEvaluacion } from '@/types/domain';
import { computeLayout } from './layoutDag';
import { ReglaNode, type ReglaNodeData } from './ReglaNode';
import { trazaANodos, type EstadoNodo } from './trazaANodos';
import styles from './TrazaGraph.module.css';

const nodeTypes = { regla: ReglaNode };

/** Los nodos con comparaciones son más grandes que los del Historial. */
const ALTO_FILA = 142;
const ANCHO_COLUMNA = 330;
const ANCHO_NODO = 270;
/** Alto aproximado del nodo más alto (dos comparaciones y una nota). */
const ALTO_NODO_MAX = 135;
const ALTO_TERMINAL = 40;
const PADDING_FIT = 0.08;

interface TrazaGraphProps {
  schema: DagSchema;
  traza: TrazaEvaluacion;
  seleccionada: string | null;
  onSeleccionar?: (ruleId: string | null) => void;
}

/** Texto de los terminales de rama, según si la rama accionó. */
const TERMINAL: Record<string, { accion: string; noAccion: string }> = {
  RIEGO: { accion: 'Riego efectuado', noAccion: 'No se regó' },
  INSUMO: { accion: 'Dosificación efectuada', noAccion: 'No se dosificó' },
  MEDIASOMBRA: { accion: 'Mediasombra ajustada', noAccion: 'No se movió mediasombra' },
  SEGUIMIENTO: { accion: 'Seguimiento evaluado', noAccion: 'Sin acciones pendientes' },
};

const BASE_ESPECIAL: CSSProperties = {
  width: ANCHO_NODO,
  borderRadius: 12,
  fontFamily: 'var(--font-body)',
  fontSize: 12,
  fontWeight: 700,
  textAlign: 'center',
  padding: '9px 10px',
};

function estiloEspecial(estado: EstadoNodo): CSSProperties {
  if (estado === 'accion') {
    return {
      ...BASE_ESPECIAL,
      background: 'color-mix(in srgb, var(--info) 18%, var(--g900))',
      border: '2px solid var(--info)',
      color: 'var(--cream)',
    };
  }
  if (estado === 'noAccion') {
    return {
      ...BASE_ESPECIAL,
      background: 'var(--g900)',
      border: '1.5px dashed var(--off)',
      color: 'var(--cdim)',
    };
  }
  // inicio
  return {
    ...BASE_ESPECIAL,
    background: 'var(--g800)',
    border: '1.5px solid var(--brand)',
    color: 'var(--cream)',
    borderRadius: 22,
  };
}

/** Color de la arista: sigue el estado de la regla de origen mientras el destino haya corrido. */
function colorArista(origen: EstadoNodo, destino: EstadoNodo): { color: string; activa: boolean } {
  const apagado = destino === 'omitida' || destino === 'noAlcanzada' || destino === 'sinTraza';
  if (apagado) return { color: 'var(--off)', activa: false };
  if (destino === 'accion' && origen !== 'inicio' && origen !== 'paso')
    return { color: 'var(--info)', activa: true };
  if (origen === 'pospuso') return { color: 'var(--warn)', activa: true };
  return { color: 'var(--ok)', activa: true };
}

export function TrazaGraph({ schema, traza, seleccionada, onSeleccionar }: TrazaGraphProps) {
  // Los terminales de bloqueo (abort-*) sólo agregan ruido: el bloqueo ya está en la regla que cortó.
  const filtrado = useMemo(
    () => ({
      nodes: schema.nodes.filter((n) => !n.id.startsWith('abort-')),
      edges: schema.edges.filter(
        (e) => !e.target.startsWith('abort-') && !e.source.startsWith('abort-'),
      ),
    }),
    [schema],
  );

  const estados = useMemo(() => trazaANodos(schema, traza), [schema, traza]);
  const posiciones = useMemo(() => {
    const p = computeLayout(filtrado, { rowHeight: ALTO_FILA, columnWidth: ANCHO_COLUMNA });
    // La cadena global queda centrada sobre las columnas de las ramas.
    const ramas = new Set(filtrado.nodes.filter((n) => n.branch !== 'GLOBAL').map((n) => n.branch))
      .size;
    const centro = 50 + (Math.max(ramas, 1) - 1) * (ANCHO_COLUMNA / 2);
    for (const n of filtrado.nodes) {
      if (n.id === 'start' || (n.branch === 'GLOBAL' && n.type === 'default'))
        p[n.id] = { ...p[n.id], x: centro };
    }
    return p;
  }, [filtrado]);

  const porRegla = useMemo(() => new Map(traza.reglas.map((r) => [r.ruleId, r])), [traza]);
  const nombre = (id: string | null) =>
    id ? (schema.nodes.find((n) => n.id === id)?.label ?? id) : null;

  const nodes = useMemo<Node[]>(
    () =>
      filtrado.nodes.map((n) => {
        const estado = estados[n.id];
        const position = posiciones[n.id] ?? { x: 0, y: 0 };
        const comunes = {
          id: n.id,
          position,
          sourcePosition: Position.Bottom,
          targetPosition: Position.Top,
        };

        if (n.type === 'default') {
          const t = porRegla.get(n.id) ?? null;
          const data: ReglaNodeData = {
            label: n.label,
            ruleId: n.id,
            estado,
            traza: t,
            bloqueadaPorLabel: nombre(t?.bloqueadaPor ?? null),
            seleccionado: n.id === seleccionada,
          };
          return { ...comunes, type: 'regla', data };
        }

        // Inicio y terminales de rama
        const rama = n.id.startsWith('success-') ? n.id.slice('success-'.length) : '';
        const etiqueta =
          n.id === 'start'
            ? n.label
            : (TERMINAL[rama]?.[estado === 'accion' ? 'accion' : 'noAccion'] ?? n.label);
        return {
          ...comunes,
          type: n.id === 'start' ? 'input' : 'output',
          data: { label: etiqueta },
          style: estiloEspecial(estado),
        };
      }),
    // `nombre` depende sólo de `schema`
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [filtrado, estados, posiciones, porRegla, seleccionada],
  );

  const edges = useMemo<Edge[]>(
    () =>
      filtrado.edges.map((e) => {
        const { color, activa } = colorArista(estados[e.source], estados[e.target]);
        return {
          id: e.id,
          source: e.source,
          target: e.target,
          label: e.label === 'Continúa' ? undefined : e.label,
          animated: activa,
          style: {
            stroke: color,
            strokeWidth: activa ? 2 : 1.2,
            strokeDasharray: activa ? undefined : '5 4',
          },
          markerEnd: { type: MarkerType.ArrowClosed, color },
        };
      }),
    [filtrado, estados],
  );

  // El lienzo mide lo que el DAG necesita al zoom con el que `fitView` lo va a mostrar: si es
  // más alto sobra fondo oscuro arriba y abajo, y si es más bajo el grafo se achica de más.
  const ref = useRef<HTMLDivElement>(null);
  const [ancho, setAncho] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return undefined;
    setAncho(el.clientWidth);
    const obs = new ResizeObserver(() => setAncho(el.clientWidth));
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  const alto = useMemo(() => {
    const xs = Object.values(posiciones).map((p) => p.x);
    // Hasta donde llega el contenido: las reglas son altas, los terminales de rama no.
    const fondo = Math.max(
      0,
      ...filtrado.nodes.map(
        (n) => (posiciones[n.id]?.y ?? 0) + (n.type === 'default' ? ALTO_NODO_MAX : ALTO_TERMINAL),
      ),
    );
    const util = 1 - 2 * PADDING_FIT;
    const anchoContenido = Math.max(...xs) + ANCHO_NODO - Math.min(...xs);
    const zoom = Math.min(1, (Math.max(ancho, 1) * util) / anchoContenido);
    return Math.max(420, Math.round((fondo * zoom) / util));
  }, [posiciones, filtrado, ancho]);

  // Al abrirse el panel de detalle el lienzo se angosta: se vuelve a encuadrar (ya con el alto
  // nuevo) para que el grafo no quede cortado.
  const { fitView } = useReactFlow();
  useEffect(() => {
    if (ancho === 0) return undefined;
    const t = setTimeout(
      () => fitView({ padding: PADDING_FIT, minZoom: 0.4, maxZoom: 1, duration: 0 }),
      60,
    );
    return () => clearTimeout(t);
  }, [ancho, alto, fitView]);

  const alClic: NodeMouseHandler = (_e, node) => {
    if (node.type === 'regla') onSeleccionar?.(node.id);
  };

  return (
    <div ref={ref} className={styles.canvas} style={{ height: alto }}>
      {/* React Flow encuadra una sola vez al iniciar: se monta recién con el ancho conocido
          para que el alto ya sea el definitivo y el grafo no quede corrido. */}
      {ancho > 0 && (
        <ReactFlow
          nodes={nodes}
          edges={edges}
          nodeTypes={nodeTypes}
          onNodeClick={alClic}
          onPaneClick={() => onSeleccionar?.(null)}
          fitView
          fitViewOptions={{ padding: PADDING_FIT, minZoom: 0.4, maxZoom: 1 }}
          minZoom={0.3}
          maxZoom={1.5}
          panOnScroll
          zoomOnScroll={false}
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable
          colorMode="dark"
          style={{ width: '100%', height: '100%' }}
          proOptions={{ hideAttribution: true }}
        >
          <Background color="var(--g800)" gap={22} size={1} />
          <Controls showInteractive={false} position="top-right" />
        </ReactFlow>
      )}

      <div className={styles.leyenda}>
        <span className={`${styles.punto} ${styles.pasa}`} /> Pasó
        <span className={`${styles.punto} ${styles.bloquea}`} /> Bloqueó
        <span className={`${styles.punto} ${styles.pospone}`} /> Pospuso
        <span className={`${styles.punto} ${styles.acciona}`} /> Accionó
        <span className={`${styles.punto} ${styles.apagado}`} /> Omitida / no alcanzada
      </div>
    </div>
  );
}
