/* ============================================================
   Catálogo y esquema del motor de reglas para la demo (modo `mock`).

   El catálogo de fábrica sale de `catalogoReglas.fixture.json`, escrito fiel a los enums
   `engine/parametros/Parametros*.java` del backend (un test los compara). Sobre ese fixture
   el mock aplica los overrides que el usuario guarda durante la sesión.
   ============================================================ */
import fixture from './catalogoReglas.fixture.json';
import { parametroError } from '@/lib/parametrosValidation';
import type {
  CambioParametro,
  CatalogoReglas,
  DagSchema,
  ErrorParametro,
  RuleEdge,
  RuleNode,
} from '@/types/domain';

/** Valor guardado en la sesión para una clave. */
export interface OverrideParametro {
  valor: string;
  updatedBy: string;
  updatedTs: number;
}

const FABRICA = fixture as CatalogoReglas;

/** Catálogo vigente: el de fábrica con los overrides aplicados. Siempre una copia nueva. */
export function catalogoVigente(overrides: ReadonlyMap<string, OverrideParametro>): CatalogoReglas {
  const catalogo = structuredClone(FABRICA);
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
export function buildRuleSchema(): DagSchema {
  const ramas = ['RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'] as const;
  const nodes: RuleNode[] = [
    { id: 'start', label: 'Inicio Evaluación', type: 'input', priority: -1, branch: 'GLOBAL', parametros: [] },
    { id: 'abort-GLOBAL', label: 'Pipeline Detenido', type: 'output', priority: 999, branch: 'GLOBAL', parametros: [] },
  ];
  const edges: RuleEdge[] = [];

  for (const rama of ramas) {
    nodes.push(
      { id: `abort-${rama}`, label: `Bloqueo ${rama}`, type: 'output', priority: 999, branch: rama, parametros: [] },
      { id: `success-${rama}`, label: 'Evaluado OK', type: 'output', priority: 1000, branch: rama, parametros: [] },
    );
  }

  const ordenadas = [...FABRICA.reglas].sort((a, b) => a.prioridad - b.prioridad);
  const nodoDe = (r: (typeof ordenadas)[number]): RuleNode => ({
    id: r.id,
    label: r.label,
    type: 'default',
    priority: r.prioridad,
    branch: r.rama,
    parametros: [...r.parametros],
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
