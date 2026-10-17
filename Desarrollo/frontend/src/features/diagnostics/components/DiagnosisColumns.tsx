/* Barra de columnas de la grilla de Diagnósticos: cada columna se ordena y se filtra desde su
   propio menú, como el encabezado de una planilla. Las categóricas (incluidos los niveles de
   confianza y severidad) se filtran por valores; la fecha, por rango. */
import type { DiagnosisCard } from '@/types/domain';
import { Button } from '@/components/ui/Button';
import {
  columnExtent,
  columnOptions,
  filterByColumns,
  valueLabel,
  type ColumnFilters,
  type ColumnKey,
  type Range,
  type RangeColumn,
  type SortDir,
  type SortState,
  type ValueColumn,
} from '../diagnosisQuery';
import { ColumnMenu } from '@/components/ui/ColumnMenu';
import { RangeFilter, ValuesFilter } from '@/components/ui/ColumnFilters';
import styles from './DiagnosisColumns.module.css';

type Column = { label: string; sortLabels: Record<SortDir, string> } & (
  | { key: ValueColumn; searchable?: boolean }
  | { key: RangeColumn; range: true }
);

const COLUMNS: Column[] = [
  { key: 'zone', label: 'Macro-zona', sortLabels: { asc: 'De A a Z', desc: 'De Z a A' } },
  {
    key: 'sector',
    label: 'Sector',
    sortLabels: { asc: 'De A a Z', desc: 'De Z a A' },
    searchable: true,
  },
  {
    key: 'date',
    label: 'Fecha',
    range: true,
    sortLabels: { asc: 'Más antiguos primero', desc: 'Más recientes primero' },
  },
  {
    key: 'confidence',
    label: 'Confianza',
    sortLabels: { asc: 'De menor a mayor', desc: 'De mayor a menor' },
  },
  {
    key: 'severity',
    label: 'Severidad',
    sortLabels: { asc: 'De más leve a más grave', desc: 'De más grave a más leve' },
  },
];

interface DiagnosisColumnsProps {
  /** Diagnósticos de los que salen las opciones de cada columna (ya filtrados por clase). */
  source: DiagnosisCard[];
  filters: ColumnFilters;
  onFilter: (column: ColumnKey, value: string[] | Range | null) => void;
  onClear: () => void;
  /** Hay otro filtro activo fuera de las columnas (la clase elegida en los KPI). */
  otherFilter?: boolean;
  /** `null` = sin orden. */
  sort: SortState | null;
  onSort: (sort: SortState | null) => void;
  resultCount: number;
}

export function DiagnosisColumns({
  source,
  filters,
  onFilter,
  onClear,
  otherFilter = false,
  sort,
  onSort,
  resultCount,
}: DiagnosisColumnsProps) {
  const anyFilter = otherFilter || Object.values(filters).some((v) => v != null);

  return (
    <section className={styles.bar} aria-label="Ordenar y filtrar diagnósticos">
      <div className={styles.columns}>
        {COLUMNS.map((c, i) => {
          // Como en una planilla: lo que ofrece una columna sale de lo que dejan las demás
          const pool = filterByColumns(source, filters, c.key);
          return (
            <ColumnMenu
              key={c.key}
              label={c.label}
              sortLabels={c.sortLabels}
              sortDir={sort?.column === c.key ? sort.dir : null}
              onSort={(dir) => onSort(dir ? { column: c.key, dir } : null)}
              filtered={filters[c.key] != null}
              alignEnd={i >= COLUMNS.length - 2}
            >
              {(close) =>
                'range' in c ? (
                  <RangeFilter
                    value={filters[c.key]}
                    extent={columnExtent(pool, c.key)}
                    onApply={(range) => onFilter(c.key, range)}
                    close={close}
                  />
                ) : (
                  <ValuesFilter
                    label={c.label}
                    options={columnOptions(pool, c.key).map((o) => ({
                      ...o,
                      label: valueLabel(c.key, o.value),
                    }))}
                    selected={filters[c.key]}
                    onApply={(values) => onFilter(c.key, values)}
                    close={close}
                    searchable={c.searchable}
                  />
                )
              }
            </ColumnMenu>
          );
        })}
      </div>

      <div className={styles.result}>
        {/* Siempre ocupa su lugar: que aparezca no achica las columnas */}
        <Button
          variant="ghost"
          size="sm"
          onClick={onClear}
          className={anyFilter ? undefined : styles.hidden}
          aria-hidden={!anyFilter}
          tabIndex={anyFilter ? undefined : -1}
        >
          Quitar filtros
        </Button>
        <span className={styles.count} aria-live="polite">
          <b>{resultCount}</b> {resultCount === 1 ? 'diagnóstico' : 'diagnósticos'}
        </span>
      </div>
    </section>
  );
}
