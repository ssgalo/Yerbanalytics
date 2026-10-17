/* Filtro y orden por columna, como en una planilla: funciones puras sobre cualquier lista de filas.

   Cada columna se filtra eligiendo valores (casillas) o, si es una fecha, con un rango. Lo que
   ofrece una columna sale de lo que dejan las demás. El menú que las usa es `ColumnMenu` con
   `ValuesFilter` / `RangeFilter` (components/ui). Diagnósticos tiene su propia versión
   (`diagnosisQuery`), con sus niveles y clases; ésta es la genérica para tablas. */

export type SortDir = 'asc' | 'desc';

/** Rango cerrado; un extremo ausente no limita. Fecha en ms epoch. */
export interface Range {
  min?: number;
  max?: number;
}

export interface SortState {
  column: string;
  dir: SortDir;
}

interface BaseColumn {
  key: string;
  label: string;
  /** Cómo se lee cada sentido en esta columna ("De A a Z", "Más recientes primero"). */
  sortLabels: Record<SortDir, string>;
}

/** Columna categórica: se filtra eligiendo valores. */
export interface ValueColumn<T> extends BaseColumn {
  kind: 'values';
  value: (row: T) => string;
  /** Texto de un valor en el filtro; por defecto, el valor. */
  optionLabel?: (value: string, row: T) => string;
  /** Orden de los valores y de las filas; por defecto, alfabético. */
  compare?: (a: string, b: string) => number;
  /** Buscador sobre la lista de valores (columnas con muchos valores). */
  searchable?: boolean;
}

/** Columna de fecha: se filtra con un rango. Sin fecha (`null`) queda fuera de cualquier rango. */
export interface DateColumn<T> extends BaseColumn {
  kind: 'date';
  date: (row: T) => number | null;
}

export type Column<T> = ValueColumn<T> | DateColumn<T>;

/** Filtro de cada columna por su `key`; ausente o `null` = sin filtro. */
export type Filters = Record<string, string[] | Range | null | undefined>;

export const byText = (a: string, b: string) => a.localeCompare(b, 'es', { numeric: true });

const inRange = (v: number | null, r: Range) =>
  v != null && (r.min == null || v >= r.min) && (r.max == null || v <= r.max);

/** Aplica los filtros; `except` deja una columna afuera (para armar sus propias opciones). */
export function filterRows<T>(rows: T[], columns: Column<T>[], filters: Filters, except?: string): T[] {
  const active = columns.filter((c) => c.key !== except && filters[c.key] != null);
  if (active.length === 0) return rows;
  return rows.filter((row) =>
    active.every((c) =>
      c.kind === 'values'
        ? (filters[c.key] as string[]).includes(c.value(row))
        : inRange(c.date(row), filters[c.key] as Range),
    ),
  );
}

/** Valores presentes en una columna, con su texto y cuántas filas tiene cada uno. */
export function columnOptions<T>(
  rows: T[],
  column: ValueColumn<T>,
): { value: string; label: string; count: number }[] {
  const seen = new Map<string, { label: string; count: number }>();
  for (const row of rows) {
    const v = column.value(row);
    const prev = seen.get(v);
    if (prev) prev.count++;
    else seen.set(v, { label: column.optionLabel?.(v, row) ?? v, count: 1 });
  }
  return [...seen.keys()]
    .sort(column.compare ?? byText)
    .map((value) => ({ value, ...seen.get(value)! }));
}

/** Extremos de una columna de fecha; `null` si no hay ninguna fecha conocida. */
export function columnExtent<T>(rows: T[], column: DateColumn<T>): Required<Range> | null {
  const values = rows.map(column.date).filter((v): v is number => v != null);
  return values.length ? { min: Math.min(...values), max: Math.max(...values) } : null;
}

/**
 * Ordena sin mutar; sin orden, quedan como llegan. Por fecha, "descendente" es el más reciente
 * primero, y las filas sin fecha quedan siempre al final. Los empates conservan el orden de llegada.
 */
export function sortRows<T>(rows: T[], columns: Column<T>[], sort: SortState | null): T[] {
  const column = sort && columns.find((c) => c.key === sort.column);
  if (!sort || !column) return rows;
  const sign = sort.dir === 'asc' ? 1 : -1;
  return rows
    .map((row, i) => ({ row, i }))
    .sort((a, b) => {
      let cmp: number;
      if (column.kind === 'date') {
        const x = column.date(a.row);
        const y = column.date(b.row);
        if (x == null || y == null) {
          if (x !== y) return x == null ? 1 : -1;
          cmp = 0;
        } else {
          cmp = x - y;
        }
      } else {
        cmp = (column.compare ?? byText)(column.value(a.row), column.value(b.row));
      }
      return cmp * sign || a.i - b.i;
    })
    .map(({ row }) => row);
}
