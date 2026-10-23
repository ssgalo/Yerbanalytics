/* ============================================================
   Grafo del motor para una acción del historial: qué reglas se ejecutaron y cuál decidió.

   El historial guarda la regla que decidió (`regla`), no la evaluación entera. Lo demás se infiere
   del orden del motor: las globales corren antes que las ramas, y dentro de una rama corren por
   prioridad hasta la que decidió. Sólo entran las reglas activas (`activa`); las apagadas no
   corren, así que no se dibujan. Funciones puras; `HistoryGraph` sólo las pinta.
   ============================================================ */
import type { ActionRecord, DagSchema, RuleNode } from '@/types/domain';

export type HistoryNodeState = 'decided' | 'executed' | 'skipped';
export type HistoryNodeKind = 'start' | 'lane' | 'rule' | 'end';

export interface HistoryNode {
  id: string;
  label: string;
  kind: HistoryNodeKind;
  branch: string;
  state: HistoryNodeState;
}

export interface HistoryEdge {
  id: string;
  source: string;
  target: string;
  executed: boolean;
}

export const BRANCHES = ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'] as const;

const BRANCH_NAME: Record<string, string> = {
  RIEGO: 'Riego',
  INSUMO: 'Insumo',
  MEDIASOMBRA: 'Mediasombra',
  SEGUIMIENTO: 'Seguimiento',
};

/** Cierre de cada rama: si la acción se hizo o no. */
const END_LABEL: Record<string, [string, string]> = {
  RIEGO: ['Riego efectuado', 'No se regó'],
  INSUMO: ['Dosificación efectuada', 'No se dosificó'],
  MEDIASOMBRA: ['Mediasombra ajustada', 'No se movió la mediasombra'],
  SEGUIMIENTO: ['Seguimiento evaluado', 'Seguimiento evaluado'],
};

/** Regla que se atribuye a un evento sin `regla` (historial viejo): la de siempre de su tipo. */
const RULE_BY_TYPE: Record<string, string> = {
  Riego: 'RiegoPorDeficitRule',
  Mediasombra: 'ShadingRule',
};

/** El backend antepone un emoji a cada etiqueta de regla; el historial no los muestra. */
export const plainLabel = (label: string): string =>
  label.replace(/^[\p{Extended_Pictographic}\p{Emoji_Component}️‍\s]+/u, '').trim() || label;

const isActive = (n: RuleNode) => n.type === 'default' && n.activa !== false;

/** Reglas activas del esquema, por prioridad. */
export function activeRules(schema: DagSchema): RuleNode[] {
  return schema.nodes.filter(isActive).sort((a, b) => a.priority - b.priority);
}

/**
 * Regla activa que decidió el evento, o `null` si no hay una evaluación que mostrar. Las alertas son
 * de la macro-zona: avisan algo, no son una decisión sobre un sector.
 */
export function decidingRule(schema: DagSchema, event: ActionRecord): RuleNode | null {
  if (event.tipo === 'Alerta') return null;
  const rules = activeRules(schema);
  const id = event.regla || RULE_BY_TYPE[event.tipo];
  return rules.find((r) => r.id === id) ?? null;
}

/** La acción se hizo (no quedó pospuesta ni abortada). */
const acted = (event: ActionRecord) => event.res === 'Efectiva' || event.res === 'En seguimiento';

/**
 * Nodos y aristas del grafo de un evento: inicio, cadena global y una columna por rama, cada una
 * con un rótulo arriba y su cierre abajo. Con `onlyExecuted` quedan sólo las reglas ejecutadas y la
 * que decidió (y las ramas que tienen alguna).
 */
export function historyGraph(
  schema: DagSchema,
  event: ActionRecord,
  onlyExecuted = false,
): { nodes: HistoryNode[]; edges: HistoryEdge[] } {
  const decided = decidingRule(schema, event);
  const rules = activeRules(schema);

  const ran = (r: RuleNode): boolean => {
    if (!decided) return false;
    if (r.branch === 'GLOBAL') return decided.branch !== 'GLOBAL' || r.priority <= decided.priority;
    return r.branch === decided.branch && r.priority <= decided.priority;
  };
  const stateOf = (r: RuleNode): HistoryNodeState =>
    r.id === decided?.id ? 'decided' : ran(r) ? 'executed' : 'skipped';

  const nodes: HistoryNode[] = [
    { id: 'start', label: 'Inicio de la evaluación', kind: 'start', branch: 'GLOBAL', state: 'executed' },
  ];
  const edges: HistoryEdge[] = [];
  const link = (source: HistoryNode, target: HistoryNode) =>
    edges.push({
      id: `${source.id}->${target.id}`,
      source: source.id,
      target: target.id,
      executed: source.state !== 'skipped' && target.state !== 'skipped',
    });

  let last = nodes[0];
  for (const r of rules.filter((x) => x.branch === 'GLOBAL')) {
    const node: HistoryNode = { id: r.id, label: plainLabel(r.label), kind: 'rule', branch: 'GLOBAL', state: stateOf(r) };
    nodes.push(node);
    link(last, node);
    last = node;
  }
  const lastGlobal = last;

  for (const branch of BRANCHES) {
    const branchRules = rules.filter((x) => x.branch === branch);
    if (branchRules.length === 0) continue;
    const reached = branchRules.some((r) => stateOf(r) !== 'skipped');
    const done = decided?.branch === branch && acted(event);
    const lane: HistoryNode = {
      id: `lane-${branch}`,
      label: BRANCH_NAME[branch],
      kind: 'lane',
      branch,
      state: reached ? 'executed' : 'skipped',
    };
    nodes.push(lane);
    link(lastGlobal, lane);
    let prev = lane;
    for (const r of branchRules) {
      const node: HistoryNode = { id: r.id, label: plainLabel(r.label), kind: 'rule', branch, state: stateOf(r) };
      nodes.push(node);
      link(prev, node);
      prev = node;
    }
    const end: HistoryNode = {
      id: `success-${branch}`,
      label: END_LABEL[branch][done ? 0 : 1],
      kind: 'end',
      branch,
      state: done ? 'executed' : 'skipped',
    };
    nodes.push(end);
    // Una rama que decidió y actuó llega al cierre desde la regla que decidió, aunque haya reglas después.
    if (done && decided && prev.id !== decided.id) {
      edges.push({ id: `${decided.id}->${end.id}`, source: decided.id, target: end.id, executed: true });
    } else {
      link(prev, end);
    }
  }

  if (!onlyExecuted) return { nodes, edges };

  const kept = nodes.filter((n) => n.state !== 'skipped');
  const keptIds = new Set(kept.map((n) => n.id));
  // Entre dos reglas ejecutadas puede haber una salteada: la arista se reconecta por encima.
  const keptEdges: HistoryEdge[] = [];
  for (const n of kept) {
    if (n.kind === 'start') continue;
    const prev = previousKept(n.id, edges, keptIds);
    if (prev) keptEdges.push({ id: `${prev}->${n.id}`, source: prev, target: n.id, executed: true });
  }
  return { nodes: kept, edges: keptEdges };
}

/** Primer antecesor conservado de un nodo, recorriendo las aristas hacia atrás. */
function previousKept(id: string, edges: HistoryEdge[], kept: Set<string>): string | null {
  // El cierre de una rama que actuó entra desde la regla que decidió; ésa es la arista que vale.
  const incoming = edges.filter((e) => e.target === id);
  const edge = incoming.find((e) => e.executed) ?? incoming[0];
  if (!edge) return null;
  return kept.has(edge.source) ? edge.source : previousKept(edge.source, edges, kept);
}

/**
 * Recorrido en ángulo recto: baja, cruza a la mitad del camino y vuelve a bajar. Todas las aristas que
 * salen de un nodo hacia la misma fila cruzan a la misma altura, así el tramo horizontal es uno solo.
 */
export function orthogonalPath(sx: number, sy: number, tx: number, ty: number, radius = 8): string {
  if (Math.abs(sx - tx) < 1) return `M${sx},${sy} L${sx},${ty}`;
  const my = Math.round((sy + ty) / 2);
  const dir = Math.sign(tx - sx);
  const r = Math.min(radius, Math.abs(tx - sx) / 2, Math.abs(ty - sy) / 2);
  return [
    `M${sx},${sy}`,
    `L${sx},${my - r}`,
    `Q${sx},${my} ${sx + dir * r},${my}`,
    `L${tx - dir * r},${my}`,
    `Q${tx},${my} ${tx},${my + r}`,
    `L${tx},${ty}`,
  ].join(' ');
}
