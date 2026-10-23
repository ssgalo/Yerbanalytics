/* ============================================================
   Vistas derivadas del catálogo para la pestaña Reglas: grupos por objetivo, consumidores sin
   interruptor, parámetros compartidos y filtros. Todo en memoria y O(piezas + parámetros): el
   catálogo llega normalizado (cada parámetro una vez), así que escala a decenas de reglas sin cambios.
   Una regla nueva del backend trae su objetivo: no hay un mapa que tocar acá.
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

/** Qué cubre cada grupo, para su encabezado. */
export const DESCRIPCION_OBJETIVO: Record<ObjetivoRegla, string> = {
  IRRIGATION: 'Cuándo y cuánto se riega',
  NUTRITION: 'Fertilizantes y fitosanitarios',
  SANITY: 'Plagas, hongos y otros daños de las plantas',
  SHADE: 'Cuándo se abre y se cierra la malla',
  FOLLOW_UP: 'Si lo que hizo el motor surtió efecto',
  PROTECTION: 'Frenos de seguridad y de calidad de los datos',
};

/**
 * Consumidores de parámetros que no son piezas con interruptor (el despacho de riego, la purga del historial, el
 * pulso de sensado, los servicios de insumos, el seguimiento…): el backend los lista en `usadoPor` junto a las reglas.
 * Se muestran como tarjetas de "Ejecución", sin `Switch`: ejecutan lo que las reglas deciden.
 */
export const CONSUMIDORES: Record<
  string,
  { titulo: string; descripcion: string; objetivo: ObjetivoRegla }
> = {
  DespachoRiego: {
    titulo: 'Ejecución del riego',
    descripcion: 'Cómo se reparten los riegos ordenados entre los sectores de una macro-zona',
    objetivo: 'IRRIGATION',
  },
  ReadingPurge: {
    titulo: 'Ejecución: historial de lecturas',
    descripcion:
      'Cuánto tiempo se conservan las lecturas de cada macro-zona que consultan las reglas',
    objetivo: 'PROTECTION',
  },
  PulsoSensado: {
    titulo: 'Ejecución: lectura de los nodos',
    descripcion:
      'Cuánto se espera la lectura de cada macro-zona y cuántas veces se reintenta en cada pulso',
    objetivo: 'PROTECTION',
  },
  ApplicationService: {
    titulo: 'Ejecución de la aplicación de insumos',
    descripcion:
      'Dosis, reingreso tras un fitosanitario y cuánto espera una aplicación diferida antes de descartarse',
    objetivo: 'NUTRITION',
  },
  TankService: {
    titulo: 'Ejecución: tanques de insumo',
    descripcion: 'Con qué nivel se avisa que hay que reponer el insumo de un tanque',
    objetivo: 'NUTRITION',
  },
  NutritionPlanService: {
    titulo: 'Ejecución del plan de nutrición',
    descripcion: 'Cada cuánto se aplica el plan de nutrición de una macro-zona',
    objetivo: 'NUTRITION',
  },
};

export const nombreConsumidor = (id: string): string => CONSUMIDORES[id]?.titulo ?? id;

export interface GrupoConsumidor {
  id: string;
  titulo: string;
  descripcion: string;
  parametros: ParametroRegla[];
}

export interface GrupoObjetivo {
  objetivo: ObjetivoRegla;
  titulo: string;
  descripcion: string;
  reglas: ReglaCatalogo[];
  consumidores: GrupoConsumidor[];
  /** Parámetros que usan varias piezas y se editan acá, una sola vez. */
  compartidos: ParametroRegla[];
  /** Piezas apagadas del grupo (sin contar los filtros). */
  apagadas: number;
}

export interface Filtros {
  busqueda: string;
  soloApagadas: boolean;
  soloProtecciones: boolean;
  soloConCambios: boolean;
}

export const SIN_FILTROS: Filtros = {
  busqueda: '',
  soloApagadas: false,
  soloProtecciones: false,
  soloConCambios: false,
};

export const hayFiltros = (f: Filtros): boolean =>
  f.busqueda.trim() !== '' || f.soloApagadas || f.soloProtecciones || f.soloConCambios;

export function indicePorClave(catalogo: CatalogoReglas): Map<string, ParametroRegla> {
  return new Map(catalogo.parametros.map((p) => [p.clave, p]));
}

/** Minúsculas y sin tildes, para que "condicion" encuentre "Condición". */
const normalizar = (s: string) =>
  s
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();

const textoRegla = (r: ReglaCatalogo) => normalizar(`${r.label} ${r.codigo} ${r.id}`);
const textoParametro = (p: ParametroRegla) =>
  normalizar(`${p.etiqueta} ${p.clave} ${p.descripcion}`);

/** Objetivo de un consumidor: el de la pieza si es una regla, el declarado si es un servicio sin interruptor. */
function objetivoDe(id: string, piezas: Map<string, ReglaCatalogo>): ObjetivoRegla | null {
  return piezas.get(id)?.objetivo ?? CONSUMIDORES[id]?.objetivo ?? null;
}

/**
 * Dónde se edita un parámetro compartido: el primer grupo, en el orden de la pantalla, donde aparece uno de sus
 * consumidores. Se decide con el catálogo entero (no con lo filtrado) para que no salte de lugar al buscar.
 */
function objetivoDeCompartido(
  p: ParametroRegla,
  piezas: Map<string, ReglaCatalogo>,
): ObjetivoRegla | null {
  const objetivos = p.usadoPor
    .map((id) => objetivoDe(id, piezas))
    .filter((o): o is ObjetivoRegla => o !== null);
  return ORDEN_OBJETIVOS.find((o) => objetivos.includes(o)) ?? null;
}

export type OrdenPiezas = 'codigo' | 'prioridad';

/** Primer código de una pieza ("S-01, S-04" → "S-01"); "" si no tiene. */
const primerCodigo = (r: ReglaCatalogo): string => r.codigo.split(',')[0].trim();

/** Por código en orden natural (R-02 antes que R-10), sin código al final por label; o por prioridad del motor. */
export function comparadorPiezas(
  orden: OrdenPiezas,
): (a: ReglaCatalogo, b: ReglaCatalogo) => number {
  const porPrioridad = (a: ReglaCatalogo, b: ReglaCatalogo) =>
    a.prioridad - b.prioridad || a.id.localeCompare(b.id);
  if (orden === 'prioridad') return porPrioridad;
  return (a, b) => {
    const ca = primerCodigo(a);
    const cb = primerCodigo(b);
    if (ca && cb) {
      return ca.localeCompare(cb, 'es', { numeric: true }) || porPrioridad(a, b);
    }
    if (ca) return -1;
    if (cb) return 1;
    return a.label.localeCompare(b.label, 'es') || porPrioridad(a, b);
  };
}

/**
 * Las piezas del catálogo en sus grupos, con sus consumidores sin interruptor y los parámetros compartidos del grupo.
 * Un grupo sin nada que mostrar (tras los filtros o de fábrica) no sale. `editadas` son las claves con cambios sin guardar.
 */
export function agruparPorObjetivo(
  catalogo: CatalogoReglas,
  filtros: Filtros = SIN_FILTROS,
  editadas: ReadonlySet<string> = new Set(),
  orden: OrdenPiezas = 'codigo',
): GrupoObjetivo[] {
  const indice = indicePorClave(catalogo);
  const piezas = new Map(catalogo.reglas.map((r) => [r.id, r]));
  const q = normalizar(filtros.busqueda.trim());
  const filtraPiezas = filtros.soloApagadas || filtros.soloProtecciones;

  const paramsDe = (r: ReglaCatalogo) =>
    r.parametros.map((c) => indice.get(c)).filter((p): p is ParametroRegla => !!p);

  const pasaRegla = (r: ReglaCatalogo): boolean => {
    if (filtros.soloApagadas && r.activa) return false;
    if (filtros.soloProtecciones && r.efecto !== 'PROTECTS') return false;
    const params = paramsDe(r);
    if (filtros.soloConCambios && !params.some((p) => editadas.has(p.clave))) return false;
    if (q === '') return true;
    return textoRegla(r).includes(q) || params.some((p) => textoParametro(p).includes(q));
  };

  /** Parámetros sueltos (compartidos o de un consumidor): sin interruptor, no entran en Apagadas ni Protecciones. */
  const pasaParametro = (p: ParametroRegla, extra = ''): boolean => {
    if (filtraPiezas) return false;
    if (filtros.soloConCambios && !editadas.has(p.clave)) return false;
    if (q === '') return true;
    const consumidores = p.usadoPor
      .map((id) => normalizar(piezas.get(id)?.label ?? nombreConsumidor(id)))
      .join(' ');
    return (
      textoParametro(p).includes(q) || consumidores.includes(q) || normalizar(extra).includes(q)
    );
  };

  const compartidos = catalogo.parametros.filter((p) => p.usadoPor.length > 1);
  // Parámetros que lee un servicio sin interruptor y que ninguna pieza ni otro consumidor comparte.
  const propiosDeConsumidor = catalogo.parametros.filter(
    (p) => p.usadoPor.length === 1 && !piezas.has(p.usadoPor[0]),
  );

  return ORDEN_OBJETIVOS.map((objetivo): GrupoObjetivo => {
    const delGrupo = catalogo.reglas.filter((r) => r.objetivo === objetivo);
    const consumidores = new Map<string, ParametroRegla[]>();
    for (const p of propiosDeConsumidor) {
      const id = p.usadoPor[0];
      if (CONSUMIDORES[id]?.objetivo !== objetivo) continue;
      consumidores.set(id, [...(consumidores.get(id) ?? []), p]);
    }
    return {
      objetivo,
      titulo: NOMBRE_OBJETIVO[objetivo],
      descripcion: DESCRIPCION_OBJETIVO[objetivo],
      reglas: delGrupo.filter(pasaRegla).sort(comparadorPiezas(orden)),
      consumidores: [...consumidores]
        .map(([id, parametros]) => ({
          id,
          titulo: nombreConsumidor(id),
          descripcion: CONSUMIDORES[id]?.descripcion ?? '',
          parametros: parametros.filter((p) => pasaParametro(p, nombreConsumidor(id))),
        }))
        .filter((c) => c.parametros.length > 0),
      compartidos: compartidos
        .filter((p) => objetivoDeCompartido(p, piezas) === objetivo)
        .filter((p) => pasaParametro(p)),
      apagadas: delGrupo.filter((r) => !r.activa).length,
    };
  }).filter((g) => g.reglas.length + g.consumidores.length + g.compartidos.length > 0);
}
