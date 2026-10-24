/* ============================================================
   Vistas derivadas del catálogo para la pantalla Motor de reglas: grupos por objetivo, filas de
   ejecución (servicios que leen parámetros y no tienen interruptor) y el estado del interruptor
   de cada grupo. Todo en memoria y O(piezas + copias). Una regla nueva del backend trae su
   objetivo: no hay un mapa que tocar acá.
   ============================================================ */
import type { CatalogoReglas, ObjetivoRegla, ParametroRegla, ReglaCatalogo } from '@/types/domain';

/** Orden de los grupos de la pantalla. */
export const ORDEN_OBJETIVOS: ObjetivoRegla[] = [
  'IRRIGATION',
  'NUTRITION',
  'SANITY',
  'SHADE',
  'FOLLOW_UP',
  'PROTECTION',
];

export const NOMBRE_OBJETIVO: Record<ObjetivoRegla, string> = {
  IRRIGATION: 'Riego',
  NUTRITION: 'Nutrición',
  SANITY: 'Sanidad',
  SHADE: 'Mediasombra',
  FOLLOW_UP: 'Seguimiento',
  PROTECTION: 'Protecciones',
};

/**
 * Servicios que leen parámetros y no son piezas con interruptor: ejecutan lo que las reglas deciden. El backend
 * los nombra como dueños de sus copias (`ParametroRegla.regla`); acá se les da nombre y grupo. Uno que no esté en
 * el mapa no se muestra.
 */
export const CONSUMIDORES: Record<string, { titulo: string; objetivo: ObjetivoRegla }> = {
  DespachoRiego: { titulo: 'Despacho del riego', objetivo: 'IRRIGATION' },
  ApplicationService: { titulo: 'Aplicación de insumos', objetivo: 'NUTRITION' },
  TankService: { titulo: 'Tanques de insumo', objetivo: 'NUTRITION' },
  NutritionPlanService: { titulo: 'Ejecución del plan de nutrición', objetivo: 'NUTRITION' },
  PulsoSensado: { titulo: 'Lectura de los nodos', objetivo: 'PROTECTION' },
  ReadingPurge: { titulo: 'Historial de lecturas', objetivo: 'PROTECTION' },
};

/** Una fila de ejecución: un servicio sin interruptor con sus copias de parámetros. */
export interface FilaEjecucion {
  id: string;
  titulo: string;
  parametros: ParametroRegla[];
}

export interface GrupoObjetivo {
  objetivo: ObjetivoRegla;
  titulo: string;
  /** Las piezas no eliminadas, por código. */
  reglas: ReglaCatalogo[];
  ejecucion: FilaEjecucion[];
}

/** Nombre interno de una pieza sin el sufijo `Rule` (`ShadingRule` → `Shading`). */
export const nombreInterno = (id: string): string => id.replace(/Rule$/, '') || id;

/** Lo que va entre paréntesis junto al nombre: el código de `reglas_v2` o, si no tiene, el nombre interno. */
export const codigoVisible = (r: Pick<ReglaCatalogo, 'id' | 'codigo'>): string =>
  r.codigo || nombreInterno(r.id);

/** Las copias de parámetros de una pieza (o de un servicio de ejecución), en el orden del catálogo. */
export function parametrosDe(catalogo: CatalogoReglas, id: string): ParametroRegla[] {
  return catalogo.parametros.filter((p) => p.regla === id);
}

/** Interruptor del grupo: prendido si todas lo están, apagado si ninguna, a medio camino si hay de las dos. */
export function estadoGrupo(reglas: readonly ReglaCatalogo[]): boolean | 'mixed' {
  const activas = reglas.filter((r) => r.activa).length;
  if (activas === 0) return false;
  return activas === reglas.length ? true : 'mixed';
}

/** Primer código de una pieza ("S-01, S-04" → "S-01"); "" si no tiene. */
const primerCodigo = (r: ReglaCatalogo): string => r.codigo.split(',')[0].trim();

/** Por código en orden natural (R-02 antes que R-10); las sin código al final, por nombre. */
export function compararPiezas(a: ReglaCatalogo, b: ReglaCatalogo): number {
  const porPrioridad = a.prioridad - b.prioridad || a.id.localeCompare(b.id);
  const ca = primerCodigo(a);
  const cb = primerCodigo(b);
  if (ca && cb) return ca.localeCompare(cb, 'es', { numeric: true }) || porPrioridad;
  if (ca) return -1;
  if (cb) return 1;
  return a.label.localeCompare(b.label, 'es') || porPrioridad;
}

/** Las piezas no eliminadas en sus grupos, con sus filas de ejecución. Un grupo sin nada que mostrar no sale. */
export function agruparPorObjetivo(catalogo: CatalogoReglas): GrupoObjetivo[] {
  const piezas = new Set(catalogo.reglas.map((r) => r.id));
  const ejecucion = new Map<string, ParametroRegla[]>();
  for (const p of catalogo.parametros) {
    if (piezas.has(p.regla) || !CONSUMIDORES[p.regla]) continue;
    ejecucion.set(p.regla, [...(ejecucion.get(p.regla) ?? []), p]);
  }
  return ORDEN_OBJETIVOS.map(
    (objetivo): GrupoObjetivo => ({
      objetivo,
      titulo: NOMBRE_OBJETIVO[objetivo],
      reglas: catalogo.reglas
        .filter((r) => r.objetivo === objetivo && !r.eliminada)
        .sort(compararPiezas),
      ejecucion: [...ejecucion]
        .filter(([id]) => CONSUMIDORES[id].objetivo === objetivo)
        .map(([id, parametros]) => ({ id, titulo: CONSUMIDORES[id].titulo, parametros })),
    }),
  ).filter((g) => g.reglas.length + g.ejecucion.length > 0);
}
