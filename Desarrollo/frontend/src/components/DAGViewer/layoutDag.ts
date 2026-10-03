/* ============================================================
   Layout del DAG del motor: una cadena global arriba y una columna por rama debajo.
   Lo comparten el modo Historial (nodos chicos) y el modo traza (nodos con comparaciones,
   más altos y anchos), que sólo difieren en el alto de fila y el ancho de columna.
   ============================================================ */
import type { DagSchema } from '@/types/domain';

/** Calcula el layout de posición X,Y para cada nodo soportando ramas horizontales. */
export function computeLayout(
  schema: DagSchema,
  { rowHeight = 90, columnWidth = 300 }: { rowHeight?: number; columnWidth?: number } = {},
): Record<string, { x: number; y: number }> {
  const positions: Record<string, { x: number; y: number }> = {};
  const ROW_HEIGHT = rowHeight;

  // Calculamos anchos dinámicos para centrar ramas que sobrevivieron al filtro
  const branches = ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'].filter((b) =>
    schema.nodes.some((n) => n.branch === b),
  );

  const COLUMN_WIDTH = columnWidth;
  const startX = branches.length > 0 ? (branches.length * COLUMN_WIDTH) / 2 : 150;

  const BRANCH_X: Record<string, number> = { GLOBAL: startX };
  branches.forEach((b, i) => {
    BRANCH_X[b] = 50 + i * COLUMN_WIDTH;
  });

  const globalNodes = schema.nodes.filter((n) => n.branch === 'GLOBAL' && n.type === 'default');
  let currentY = 0;

  if (schema.nodes.some((n) => n.id === 'start')) {
    positions['start'] = { x: startX, y: currentY };
    currentY += ROW_HEIGHT;
  }

  globalNodes.forEach((node) => {
    positions[node.id] = { x: startX, y: currentY };
    currentY += ROW_HEIGHT;
  });

  if (schema.nodes.some((n) => n.id === 'abort-GLOBAL')) {
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

    if (schema.nodes.some((n) => n.id === `abort-${branch}`)) {
      positions[`abort-${branch}`] = { x: baseX + 180, y: currentY };
    }
    if (schema.nodes.some((n) => n.id === `success-${branch}`)) {
      positions[`success-${branch}`] = { x: baseX, y: branchY };
    }
  });

  return positions;
}
