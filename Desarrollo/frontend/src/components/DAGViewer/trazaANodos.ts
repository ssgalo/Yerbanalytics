/* ============================================================
   De la traza de evaluación al estado visual de cada nodo del DAG.

   Función pura sobre datos estructurados: no hay regex sobre textos del historial. Es lo que
   el backend ya sabe (`estado`, `acciones`, `bloqueadaPor`) y el frontend antes adivinaba.
   ============================================================ */
import type { DagSchema, TrazaEvaluacion, TrazaRegla } from '@/types/domain';

/**
 * Estado visual de un nodo:
 * - de regla: pasó, bloqueó, pospuso, accionó, omitida (rama cortada antes), no alcanzada
 *   (cortó un ABORT_ALL), error (lanzó excepción);
 * - terminal de rama: accionó / no accionó;
 * - `inicio` para el nodo de entrada y `sinTraza` para el que la traza no menciona.
 */
export type EstadoNodo =
  | 'inicio'
  | 'paso'
  | 'bloqueo'
  | 'pospuso'
  | 'accion'
  | 'omitida'
  | 'noAlcanzada'
  | 'error'
  | 'noAccion'
  | 'sinTraza';

const BLOQUEANTES = new Set(['ABORT_RIEGO', 'ABORT_INSUMO', 'ABORT_ALL']);
const EJECUTORAS = new Set(['ACTIVAR_VALVULA', 'ACTIVAR_BOMBA', 'MOVER_MEDIASOMBRA']);

/** Estado visual de una regla según su traza. Si emite varias acciones manda la más fuerte. */
export function clasificarRegla(r: TrazaRegla): EstadoNodo {
  if (r.estado === 'OMITIDA_RAMA_BLOQUEADA') return 'omitida';
  if (r.estado === 'NO_ALCANZADA') return 'noAlcanzada';
  if (r.estado === 'ERROR') return 'error';

  const tipos = r.acciones.map((a) => a.tipo);
  if (tipos.some((t) => BLOQUEANTES.has(t))) return 'bloqueo';
  if (tipos.includes('POSTPONE_RIEGO')) return 'pospuso';
  if (tipos.some((t) => EJECUTORAS.has(t))) return 'accion';
  return 'paso';
}

/** Estado de cada nodo del esquema (por id) para pintar el DAG con una traza. */
export function trazaANodos(schema: DagSchema, traza: TrazaEvaluacion): Record<string, EstadoNodo> {
  const porRegla = new Map(traza.reglas.map((r) => [r.ruleId, clasificarRegla(r)]));
  const estados: Record<string, EstadoNodo> = {};

  for (const nodo of schema.nodes) {
    if (nodo.id === 'start') {
      estados[nodo.id] = 'inicio';
    } else if (nodo.id.startsWith('success-')) {
      // El terminal de la rama dice si ALGUNA de sus reglas actuó.
      const rama = nodo.id.slice('success-'.length);
      const actuo = traza.reglas.some(
        (r) => r.rama === rama && porRegla.get(r.ruleId) === 'accion',
      );
      estados[nodo.id] = actuo ? 'accion' : 'noAccion';
    } else {
      estados[nodo.id] = porRegla.get(nodo.id) ?? 'sinTraza';
    }
  }
  return estados;
}
