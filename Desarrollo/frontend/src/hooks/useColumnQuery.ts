/* Estado de una tabla filtrable y ordenable por columna (ver `components/ui/ColumnHeader`). */
import { useMemo, useState } from 'react';
import { filterRows, sortRows, type Column, type Filters, type Range, type SortState } from '@/lib/columnQuery';

export interface ColumnQuery<T> {
  rows: T[];
  columns: Column<T>[];
  filters: Filters;
  setFilter: (key: string, value: string[] | Range | null) => void;
  sort: SortState | null;
  setSort: (sort: SortState | null) => void;
  /** Filas filtradas y ordenadas. */
  visible: T[];
  anyFilter: boolean;
  clearFilters: () => void;
}

/** Filtros y orden de una tabla. `columns` tiene que ser estable (constante o memorizada). */
export function useColumnQuery<T>(rows: T[], columns: Column<T>[], initialSort: SortState | null = null): ColumnQuery<T> {
  const [filters, setFilters] = useState<Filters>({});
  const [sort, setSort] = useState<SortState | null>(initialSort);
  const visible = useMemo(
    () => sortRows(filterRows(rows, columns, filters), columns, sort),
    [rows, columns, filters, sort],
  );
  return {
    rows,
    columns,
    filters,
    setFilter: (key, value) => setFilters((f) => ({ ...f, [key]: value })),
    sort,
    setSort,
    visible,
    anyFilter: Object.values(filters).some((v) => v != null),
    clearFilters: () => setFilters({}),
  };
}
