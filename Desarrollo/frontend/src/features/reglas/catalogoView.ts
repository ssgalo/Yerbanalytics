/* ============================================================
   Vistas derivadas del catálogo: agrupación por rama, resúmenes y filtros. Todo en memoria y
   O(reglas + parámetros): el catálogo llega normalizado (cada parámetro una vez), así que
   escala a decenas de reglas sin cambios.
   ============================================================ */
import type { CatalogoReglas, ParametroRegla, RamaRegla, ReglaCatalogo } from '@/types/domain';

/** Orden de las ramas del motor, de la más general a la más específica. */
export const ORDEN_RAMAS: RamaRegla[] = ['GLOBAL', 'RIEGO', 'INSUMO', 'MEDIASOMBRA', 'SEGUIMIENTO'];

export const NOMBRE_RAMA: Record<RamaRegla, string> = {
  GLOBAL: 'Global',
  RIEGO: 'Riego',
  INSUMO: 'Insumos',
  MEDIASOMBRA: 'Mediasombra',
  SEGUIMIENTO: 'Seguimiento',
};

/** Qué hace cada rama, para el encabezado del grupo. */
export const DESCRIPCION_RAMA: Record<RamaRegla, string> = {
  GLOBAL: 'Corren primero y aplican a todo el motor',
  RIEGO: 'Electroválvulas',
  INSUMO: 'Bombas peristálticas',
  MEDIASOMBRA: 'Techo móvil',
  SEGUIMIENTO: 'Evaluación posterior a la acción',
};

/**
 * Consumidores de parámetros que no son reglas (hoy el despacho de riego): el backend los lista en
 * `usadoPor` junto a las reglas. Se muestran en su propio grupo de la pestaña Parámetros.
 */
export const CONSUMIDORES: Record<string, { titulo: string; descripcion: string; rama: RamaRegla }> = {
  DespachoRiego: {
    titulo: 'Ejecución del riego',
    descripcion: 'Cómo se reparten los riegos ordenados entre los sectores de una macro-zona',
    rama: 'RIEGO',
  },
};

/** Rama a la que pertenece un consumidor que no es una regla (el despacho riega: RIEGO). */
export const ramaDeConsumidor = (id: string): RamaRegla | undefined => CONSUMIDORES[id]?.rama;

export const nombreConsumidor = (id: string): string => CONSUMIDORES[id]?.titulo ?? id;

export interface GrupoConsumidor {
  id: string;
  titulo: string;
  descripcion: string;
  parametros: ParametroRegla[];
}

/**
 * Parámetros agrupados por el consumidor que no es una regla (los de `usadoPor` que no están en `reglas`).
 * Sólo los que NINGUNA regla usa: el despacho también lee, para revalidar antes de abrir cada válvula, parámetros
 * que ya son de una regla (antigüedad de la lectura, saturación, ventana horaria). Esos se editan bajo su regla
 * (donde figura "Compartido con Ejecución del riego"); listarlos acá los duplicaría.
 */
export function agruparPorConsumidor(catalogo: CatalogoReglas, parametros: ParametroRegla[] = catalogo.parametros): GrupoConsumidor[] {
  const ids = new Set(catalogo.reglas.map((r) => r.id));
  const grupos = new Map<string, ParametroRegla[]>();
  for (const p of parametros) {
    if (p.usadoPor.some((x) => ids.has(x))) continue;
    for (const u of p.usadoPor) grupos.set(u, [...(grupos.get(u) ?? []), p]);
  }
  return [...grupos].map(([id, params]) => ({
    id,
    titulo: nombreConsumidor(id),
    descripcion: CONSUMIDORES[id]?.descripcion ?? 'Lee estos parámetros fuera del motor de reglas',
    parametros: params,
  }));
}

export interface GrupoRama {
  rama: RamaRegla;
  reglas: ReglaCatalogo[];
}

/** Reglas agrupadas por rama (en el orden del motor) y, dentro de cada rama, por prioridad. */
export function agruparPorRama(reglas: ReglaCatalogo[]): GrupoRama[] {
  return ORDEN_RAMAS.map((rama) => ({
    rama,
    reglas: reglas.filter((r) => r.rama === rama).sort((a, b) => a.prioridad - b.prioridad),
  })).filter((g) => g.reglas.length > 0);
}

export function indicePorClave(catalogo: CatalogoReglas): Map<string, ParametroRegla> {
  return new Map(catalogo.parametros.map((p) => [p.clave, p]));
}

export interface ResumenRegla {
  total: number;
  modificados: number;
}

export function resumenRegla(regla: ReglaCatalogo, indice: Map<string, ParametroRegla>): ResumenRegla {
  const params = regla.parametros.map((c) => indice.get(c)).filter((p): p is ParametroRegla => !!p);
  return { total: params.length, modificados: params.filter((p) => p.modificado).length };
}

/** "3 parámetros · 1 modificado". */
export function textoResumen({ total, modificados }: ResumenRegla): string {
  if (total === 0) return 'Sin parámetros configurables';
  const params = `${total} ${total === 1 ? 'parámetro' : 'parámetros'}`;
  const mods = `${modificados} ${modificados === 1 ? 'modificado' : 'modificados'}`;
  return `${params} · ${mods}`;
}

export interface Filtros {
  busqueda: string;
  rama: RamaRegla | 'TODAS';
  soloModificados: boolean;
}

export const SIN_FILTROS: Filtros = { busqueda: '', rama: 'TODAS', soloModificados: false };

/** Minúsculas y sin tildes, para que "condicion" encuentre "Condición". */
const normalizar = (s: string) => s.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();

const textoRegla = (r: ReglaCatalogo) => normalizar(`${r.label} ${r.id}`);
const textoParametro = (p: ParametroRegla) => normalizar(`${p.etiqueta} ${p.clave} ${p.descripcion}`);

/** Reglas que pasan los filtros. Una regla coincide por su nombre o por el de alguno de sus parámetros. */
export function filtrarReglas(catalogo: CatalogoReglas, f: Filtros): ReglaCatalogo[] {
  const q = normalizar(f.busqueda.trim());
  const indice = indicePorClave(catalogo);

  return catalogo.reglas.filter((r) => {
    if (f.rama !== 'TODAS' && r.rama !== f.rama) return false;
    const params = r.parametros.map((c) => indice.get(c)).filter((p): p is ParametroRegla => !!p);
    if (f.soloModificados && !params.some((p) => p.modificado)) return false;
    if (q === '') return true;
    return textoRegla(r).includes(q) || params.some((p) => textoParametro(p).includes(q));
  });
}

/** Parámetros que pasan los filtros, cada uno una sola vez (vista "por parámetro"). */
export function filtrarParametros(catalogo: CatalogoReglas, f: Filtros): ParametroRegla[] {
  const q = normalizar(f.busqueda.trim());
  const reglas = new Map(catalogo.reglas.map((r) => [r.id, r]));

  return catalogo.parametros.filter((p) => {
    const usuarias = p.usadoPor.map((id) => reglas.get(id)).filter((r): r is ReglaCatalogo => !!r);
    // Los consumidores que no son reglas (despacho de riego) cuentan con la rama que declaran.
    const ramas = [
      ...usuarias.map((r) => r.rama),
      ...p.usadoPor.filter((id) => !reglas.has(id)).map((id) => CONSUMIDORES[id]?.rama),
    ];
    if (f.rama !== 'TODAS' && !ramas.includes(f.rama)) return false;
    if (f.soloModificados && !p.modificado) return false;
    if (q === '') return true;
    const consumidores = p.usadoPor.filter((id) => !reglas.has(id)).map((id) => normalizar(nombreConsumidor(id)));
    return (
      textoParametro(p).includes(q) ||
      usuarias.some((r) => textoRegla(r).includes(q)) ||
      consumidores.some((c) => c.includes(q))
    );
  });
}
