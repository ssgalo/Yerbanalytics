/* Encabezado de columna de tabla, como en una planilla: clic en el nombre para filtrar, botón al
   lado para ordenar. Une `ColumnMenu` y sus filtros con las funciones puras de `lib/columnQuery`.

   Uso: `useColumnQuery(filas, columnas)` (hooks/) guarda filtros y orden y devuelve las filas visibles;
   cada `<th>` lleva un `<ColumnHeader>` con la columna y ese mismo `query`. */
import type { ColumnQuery } from '@/hooks/useColumnQuery';
import { columnExtent, columnOptions, filterRows, type Column, type Range } from '@/lib/columnQuery';
import { ColumnMenu } from './ColumnMenu';
import { RangeFilter, ValuesFilter } from './ColumnFilters';

interface ColumnHeaderProps<T> {
  column: Column<T>;
  query: ColumnQuery<T>;
  /** Abre el menú hacia la izquierda (columnas del borde derecho). */
  alignEnd?: boolean;
}

export function ColumnHeader<T>({ column: c, query, alignEnd }: ColumnHeaderProps<T>) {
  const { rows, columns, filters, setFilter, sort, setSort } = query;
  const value = filters[c.key] ?? null;
  return (
    <ColumnMenu
      label={c.label}
      sortLabels={c.sortLabels}
      sortDir={sort?.column === c.key ? sort.dir : null}
      onSort={(dir) => setSort(dir ? { column: c.key, dir } : null)}
      filtered={value != null}
      alignEnd={alignEnd}
    >
      {(close) => {
        // Como en una planilla: lo que ofrece una columna sale de lo que dejan las demás
        const pool = filterRows(rows, columns, filters, c.key);
        return c.kind === 'date' ? (
          <RangeFilter
            value={value as Range | null}
            extent={columnExtent(pool, c)}
            onApply={(range) => setFilter(c.key, range)}
            close={close}
          />
        ) : (
          <ValuesFilter
            label={c.label}
            options={columnOptions(pool, c)}
            selected={value as string[] | null}
            onApply={(values) => setFilter(c.key, values)}
            close={close}
            searchable={c.searchable}
          />
        );
      }}
    </ColumnMenu>
  );
}
