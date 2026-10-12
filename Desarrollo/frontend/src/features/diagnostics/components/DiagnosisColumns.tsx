/* Barra de columnas de la grilla de Diagnósticos: cada columna se ordena y se filtra desde su
   propio menú, como el encabezado de una planilla. Las categóricas se filtran por valores; fecha
   y confianza, por rango. */
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
import { ColumnMenu } from './ColumnMenu';
import { RangeFilter, ValuesFilter } from './ColumnFilters';
import styles from './DiagnosisColumns.module.css';

type Column = { label: string; sortLabels: Record<SortDir, string> } & (
  | { key: ValueColumn; searchable?: boolean }
  | { key: RangeColumn; kind: 'percent' | 'datetime' }
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
    kind: 'datetime',
    sortLabels: { asc: 'Más antiguos primero', desc: 'Más recientes primero' },
  },
  {
    key: 'confidence',
    label: 'Confianza',
    kind: 'percent',
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
  sort,
  onSort,
  resultCount,
}: DiagnosisColumnsProps) {
  const anyFilter = Object.values(filters).some((v) => v != null);

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
                'kind' in c ? (
                  <RangeFilter
                    kind={c.kind}
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
        {anyFilter && (
          <Button variant="ghost" size="sm" onClick={onClear}>
            Quitar filtros
          </Button>
        )}
        <span className={styles.count} aria-live="polite">
          <b>{resultCount}</b> {resultCount === 1 ? 'diagnóstico' : 'diagnósticos'}
        </span>
      </div>
    </section>
  );
}
