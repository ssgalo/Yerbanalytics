/* ============================================================
   Catálogo y esquema del motor de reglas para la demo (modo `mock`).

   El catálogo de fábrica sale de `catalogoReglas.fixture.json`, escrito fiel a los enums
   `engine/parametros/Parametros*.java` del backend (un test los compara). Sobre ese fixture
   el mock aplica los overrides que el usuario guarda durante la sesión.
   ============================================================ */
import fixture from './catalogoReglas.fixture.json';
import { EstadoReglaError } from '@/data/estadoReglaError';
import { parametroError } from '@/lib/parametrosValidation';
import type {
  CambioEstadoRegla,
  CambioParametro,
  CatalogoReglas,
  DagSchema,
  ErrorParametro,
  ReglaCatalogo,
  RuleEdge,
  RuleNode,
  UltimoCambioRegla,
} from '@/types/domain';

/** Valor guardado en la sesión para una clave. */
export interface OverrideParametro {
  valor: string;
  updatedBy: string;
  updatedTs: number;
}

/** Último cambio del interruptor de una pieza en la sesión (como una fila de `regla_estado` del backend). */
export interface EstadoRegla {
  activa: boolean;
  motivo: string | null;
  updatedBy: string;
  updatedTs: number;
}

const FABRICA = fixture as CatalogoReglas;

/** Máximo de caracteres del motivo, como el backend. */
const MOTIVO_MAX = 500;

/**
 * Servicios que deciden y tienen interruptor, pero no son una regla de la cadena: el DAG del motor no los dibuja ni la
 * traza los evalúa (como `RuleEngineSchemaController`, que sólo arma el grafo con `Rule` y `ZoneRule`).
 */
const SERVICIOS = new Set([
  'EscalamientoNodo',
  'ReadingValidationGuard',
  'HardwareFaultService',
  'HorarioMediasombraService',
  'ApplicationPreconditions',
]);

/** Sólo las reglas de la cadena (sin los servicios): lo que dibuja el grafo y evalúa la traza. */
export const soloReglasDeLaCadena = (catalogo: CatalogoReglas): CatalogoReglas => ({
  ...catalogo,
  reglas: catalogo.reglas.filter((r) => !SERVICIOS.has(r.id)),
});

/** La pieza de fábrica con ese nombre, o `undefined`. */
export const reglaDeFabrica = (nombre: string): ReglaCatalogo | undefined => FABRICA.reglas.find((r) => r.id === nombre);

/**
 * Valida el cambio de estado con las mismas reglas del backend: apagar una protección exige motivo y confirmación;
 * prender nunca pide nada; el motivo tiene tope. Lanza `EstadoReglaError`.
 */
export function validarCambioEstado(regla: ReglaCatalogo, cambio: CambioEstadoRegla): void {
  const motivo = cambio.motivo?.trim() ?? '';
  if (motivo.length > MOTIVO_MAX) {
    throw new EstadoReglaError('MOTIVO_DEMASIADO_LARGO', `El motivo no puede pasar de ${MOTIVO_MAX} caracteres.`);
  }
  if (cambio.activa || regla.proteccion === null) return;
  if (motivo === '') {
    throw new EstadoReglaError('MOTIVO_REQUERIDO', 'Para apagar una protección hay que decir el motivo.');
  }
  if (cambio.confirmaProteccion !== true) {
    throw new EstadoReglaError(
      'CONFIRMACION_REQUERIDA',
      'Apagar una protección necesita confirmación.',
      regla.proteccion,
    );
  }
}

/** Catálogo vigente: el de fábrica con los overrides de parámetros y los estados de las reglas aplicados. Siempre una copia nueva. */
export function catalogoVigente(
  overrides: ReadonlyMap<string, OverrideParametro>,
  estados: ReadonlyMap<string, EstadoRegla> = new Map(),
): CatalogoReglas {
  const catalogo = structuredClone(FABRICA);
  for (const r of catalogo.reglas) {
    const e = estados.get(r.id);
    if (!e) continue;
    r.activa = e.activa;
    const ultimo: UltimoCambioRegla = { motivo: e.motivo, updatedBy: e.updatedBy, updatedTs: e.updatedTs };
    r.estado = ultimo;
  }
  for (const p of catalogo.parametros) {
    const o = overrides.get(p.clave);
    if (!o) continue;
    p.valor = o.valor;
    p.modificado = o.valor !== p.fabrica;
    p.updatedBy = o.updatedBy;
    p.updatedTs = o.updatedTs;
  }
  return catalogo;
}

/** Errores del lote, con la misma validación que usa la UI. Vacío = se puede guardar. */
export function validarCambios(cambios: CambioParametro[]): ErrorParametro[] {
  const errores: ErrorParametro[] = [];
  for (const c of cambios) {
    const def = FABRICA.parametros.find((p) => p.clave === c.clave);
    if (!def) {
      errores.push({ clave: c.clave, mensaje: `El parámetro ${c.clave} no existe.` });
    } else if (c.valor !== null) {
      const mensaje = parametroError(def, c.valor);
      if (mensaje) errores.push({ clave: c.clave, mensaje });
    }
  }
  return errores;
}

/** Esquema del DAG, armado igual que `RuleEngineSchemaController` a partir del catálogo. */
export function buildRuleSchema(estados: ReadonlyMap<string, EstadoRegla> = new Map()): DagSchema {
  const ramas = ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'] as const;
  const nodes: RuleNode[] = [
    { id: 'start', label: 'Inicio Evaluación', type: 'input', priority: -1, branch: 'GLOBAL', parametros: [], codigo: '', activa: true, activaPorDefecto: true },
    { id: 'abort-GLOBAL', label: 'Pipeline Detenido', type: 'output', priority: 999, branch: 'GLOBAL', parametros: [], codigo: '', activa: true, activaPorDefecto: true },
  ];
  const edges: RuleEdge[] = [];

  for (const rama of ramas) {
    nodes.push(
      { id: `abort-${rama}`, label: `Bloqueo ${rama}`, type: 'output', priority: 999, branch: rama, parametros: [], codigo: '', activa: true, activaPorDefecto: true },
      { id: `success-${rama}`, label: 'Evaluado OK', type: 'output', priority: 1000, branch: rama, parametros: [], codigo: '', activa: true, activaPorDefecto: true },
    );
  }

  const ordenadas = [...soloReglasDeLaCadena(FABRICA).reglas].sort((a, b) => a.prioridad - b.prioridad);
  const nodoDe = (r: (typeof ordenadas)[number]): RuleNode => ({
    id: r.id,
    label: r.label,
    type: 'default',
    priority: r.prioridad,
    branch: r.rama,
    parametros: [...r.parametros],
    codigo: r.codigo,
    activa: estados.get(r.id)?.activa ?? r.activaPorDefecto,
    activaPorDefecto: r.activaPorDefecto,
  });

  // Cadena global
  let ultimoGlobal = 'start';
  for (const r of ordenadas.filter((x) => x.rama === 'GLOBAL')) {
    nodes.push(nodoDe(r));
    edges.push({ id: `e_${ultimoGlobal}_${r.id}`, source: ultimoGlobal, target: r.id, label: 'Continúa' });
    edges.push({ id: `e_${r.id}_abort`, source: r.id, target: 'abort-GLOBAL', label: 'Bloquea' });
    ultimoGlobal = r.id;
  }

  // Ramas independientes: parten del último nodo global
  for (const rama of ramas) {
    const deLaRama = ordenadas.filter((x) => x.rama === rama);
    if (deLaRama.length === 0) continue;
    let previo = ultimoGlobal;
    for (const r of deLaRama) {
      nodes.push(nodoDe(r));
      edges.push({ id: `e_${previo}_${r.id}`, source: previo, target: r.id, label: 'Continúa' });
      edges.push({ id: `e_${r.id}_abort`, source: r.id, target: `abort-${rama}`, label: 'Bloquea' });
      previo = r.id;
    }
    edges.push({ id: `e_${previo}_success`, source: previo, target: `success-${rama}`, label: 'Completado' });
  }

  return { nodes, edges };
}
