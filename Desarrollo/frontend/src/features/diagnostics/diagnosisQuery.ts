/* Filtros, orden y conteos de la vista de Diagnósticos de IA.

   Funciones puras sobre `DiagnosisCard`. Las columnas categóricas (macro-zona, sector, severidad)
   se filtran como en una planilla, eligiendo valores; las continuas (fecha, confianza), con un
   rango. La fecha es `creadoEn`, que manda el backend. */
import type { DiagnosisCard } from '@/types/domain';

/** Clases del dashboard, en el orden en que se muestran los KPI. */
export const DIAGNOSIS_CLASSES = [
  'Sano',
  'Clorosis',
  'Estrés solar',
  'Plaga foliar',
  'Daño fúngico',
  'No concluyente',
] as const;

export type ColumnKey = 'zone' | 'sector' | 'date' | 'confidence' | 'severity';
/** Columnas que se filtran eligiendo valores. */
export type ValueColumn = 'zone' | 'sector' | 'severity';
/** Columnas que se filtran por rango. */
export type RangeColumn = 'date' | 'confidence';
export type SortDir = 'asc' | 'desc';
export interface SortState {
  column: ColumnKey;
  dir: SortDir;
}

/** Rango cerrado; un extremo ausente no limita. Fecha en ms epoch; confianza en %. */
export interface Range {
  min?: number;
  max?: number;
}

/** Filtro de cada columna; `null` = sin filtro (todos). */
export type ColumnFilters = Record<ValueColumn, string[] | null> &
  Record<RangeColumn, Range | null>;

export const NO_COLUMN_FILTERS: ColumnFilters = {
  zone: null,
  sector: null,
  date: null,
  confidence: null,
  severity: null,
};

/** De más a menos grave; sin severidad ("—") queda última. */
const SEVERITY_RANK: Record<string, number> = { Alta: 3, Media: 2, Baja: 1, '—': 0 };

const byText = (a: string, b: string) => a.localeCompare(b, 'es', { numeric: true });

/** Valor categórico de la tarjeta en una columna. */
export function columnValue(d: DiagnosisCard, column: ValueColumn): string {
  if (column === 'zone') return d.zonaName;
  if (column === 'sector') return d.sectorId;
  return d.sev;
}

/** Texto visible de un valor de columna. */
export function valueLabel(column: ValueColumn, value: string): string {
  return column === 'severity' && value === '—' ? 'Sin severidad' : value;
}

const SEVERITY_ORDER = ['Alta', 'Media', 'Baja', '—'];

/** Valores presentes en una columna, con cuántas tarjetas tiene cada uno. */
export function columnOptions(
  list: DiagnosisCard[],
  column: ValueColumn,
): { value: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const d of list) {
    const v = columnValue(d, column);
    counts.set(v, (counts.get(v) ?? 0) + 1);
  }
  const values = [...counts.keys()].sort((a, b) =>
    column === 'severity' ? SEVERITY_ORDER.indexOf(a) - SEVERITY_ORDER.indexOf(b) : byText(a, b),
  );
  return values.map((value) => ({ value, count: counts.get(value)! }));
}

/** Valor numérico de la tarjeta en una columna de rango. */
export function rangeValue(d: DiagnosisCard, column: RangeColumn): number | null {
  return column === 'confidence' ? d.conf : d.creadoEn;
}

/** Extremos presentes en una columna de rango; `null` si no hay ningún valor conocido. */
export function columnExtent(list: DiagnosisCard[], column: RangeColumn): Required<Range> | null {
  const values = list.map((d) => rangeValue(d, column)).filter((v): v is number => v != null);
  return values.length ? { min: Math.min(...values), max: Math.max(...values) } : null;
}

const inRange = (v: number | null, r: Range) =>
  v != null && (r.min == null || v >= r.min) && (r.max == null || v <= r.max);

const VALUE_COLUMNS: ValueColumn[] = ['zone', 'sector', 'severity'];
const RANGE_COLUMNS: RangeColumn[] = ['date', 'confidence'];

/** Aplica los filtros de columna; `except` deja una afuera (para armar sus propias opciones). */
export function filterByColumns(
  list: DiagnosisCard[],
  filters: ColumnFilters,
  except?: ColumnKey,
): DiagnosisCard[] {
  const values = VALUE_COLUMNS.filter((k) => k !== except && filters[k] != null);
  const ranges = RANGE_COLUMNS.filter((k) => k !== except && filters[k] != null);
  return list.filter(
    (d) =>
      values.every((k) => filters[k]!.includes(columnValue(d, k))) &&
      ranges.every((k) => inRange(rangeValue(d, k), filters[k]!)),
  );
}

export function filterByClass(
  list: DiagnosisCard[],
  diagnosisClass: string | null,
): DiagnosisCard[] {
  return diagnosisClass ? list.filter((d) => d.estado === diagnosisClass) : list;
}

/**
 * Ordena sin mutar. Por fecha, "descendente" es el más reciente primero; los que no tienen
 * fecha conocida quedan siempre al final. Los empates se resuelven por sector.
 */
export function sortDiagnoses(list: DiagnosisCard[], sort: SortState | null): DiagnosisCard[] {
  // Sin orden: queda el orden en que llegan del vivero
  if (!sort) return list;
  const { column, dir } = sort;
  const sign = dir === 'asc' ? 1 : -1;
  return [...list].sort((a, b) => {
    let cmp: number;
    switch (column) {
      case 'date':
        if (a.creadoEn == null || b.creadoEn == null) {
          if (a.creadoEn !== b.creadoEn) return a.creadoEn == null ? 1 : -1;
          cmp = 0;
        } else {
          cmp = a.creadoEn - b.creadoEn;
        }
        break;
      case 'zone':
        cmp = byText(a.zonaName, b.zonaName);
        break;
      case 'sector':
        cmp = byText(a.sectorId, b.sectorId);
        break;
      case 'confidence':
        cmp = a.conf - b.conf;
        break;
      case 'severity':
        cmp = (SEVERITY_RANK[a.sev] ?? 0) - (SEVERITY_RANK[b.sev] ?? 0);
        break;
    }
    return cmp * sign || byText(a.sectorId, b.sectorId);
  });
}

/** Cantidad por clase: primero las conocidas (aunque sean 0), después cualquier otra que llegue. */
export function countByClass(list: DiagnosisCard[]): { name: string; count: number }[] {
  const counts = new Map<string, number>(DIAGNOSIS_CLASSES.map((c) => [c, 0]));
  for (const d of list) counts.set(d.estado, (counts.get(d.estado) ?? 0) + 1);
  return [...counts].map(([name, count]) => ({ name, count }));
}
