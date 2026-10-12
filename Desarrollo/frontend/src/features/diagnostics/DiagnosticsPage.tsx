/* Vista principal: Diagnósticos de IA */
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useNurseryData } from '@/hooks/NurseryContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { useAuth } from '@/hooks/AuthContext';
import { puedeVer, VISTAS } from '@/lib/vistas';
import { EmptyState } from '@/components/ui/EstadosPantalla';
import { CapturaModal } from '@/features/sector/components/CapturaModal';
import { DiagnosisKpis } from './components/DiagnosisKpis';
import { DiagnosisColumns } from './components/DiagnosisColumns';
import { DiagnosisTile } from './components/DiagnosisTile';
import {
  NO_COLUMN_FILTERS,
  countByClass,
  filterByClass,
  filterByColumns,
  sortDiagnoses,
  type ColumnFilters,
  type ColumnKey,
  type Range,
  type SortState,
} from './diagnosisQuery';
import styles from './DiagnosticsPage.module.css';

export function DiagnosticsPage() {
  const { diagnoses, diagById } = useNurseryData();
  const { puede } = useAuth();
  const canOpenSector = puedeVer(VISTAS.sector, puede);

  usePageTitle('Diagnósticos de IA', `${diagnoses.length} diagnósticos registrados`);

  /* "Ver anteriores" del detalle de sector llega con ?sector=: arranca filtrada por ese sector */
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState<ColumnFilters>(() => {
    const sector = params.get('sector');
    return { ...NO_COLUMN_FILTERS, sector: sector ? [sector] : null };
  });
  const [diagnosisClass, setDiagnosisClass] = useState<string | null>(null);
  /* Sin orden por defecto: se ven como llegan del vivero (los de captura real, primero) */
  const [sort, setSort] = useState<SortState | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  function changeFilters(next: ColumnFilters) {
    setFilters(next);
    // Una vez que se toca el filtro, la URL ya no lo describe
    if (params.has('sector'))
      setParams(
        (p) => {
          p.delete('sector');
          return p;
        },
        { replace: true },
      );
  }

  /* Los KPI cuentan con los filtros de columna; la grilla, además, con la clase elegida */
  const byColumns = filterByColumns(diagnoses, filters);
  const visible = sortDiagnoses(filterByClass(byColumns, diagnosisClass), sort);

  const expanded = expandedId ? diagById[expandedId] : null;

  return (
    <div className={styles.page}>
      <DiagnosisKpis
        classes={countByClass(byColumns)}
        total={byColumns.length}
        selected={diagnosisClass}
        onSelect={setDiagnosisClass}
      />

      <DiagnosisColumns
        source={filterByClass(diagnoses, diagnosisClass)}
        filters={filters}
        onFilter={(column: ColumnKey, value: string[] | Range | null) =>
          changeFilters({ ...filters, [column]: value })
        }
        onClear={() => changeFilters(NO_COLUMN_FILTERS)}
        sort={sort}
        onSort={setSort}
        resultCount={visible.length}
      />

      {visible.length > 0 ? (
        <div className={styles.grid}>
          {visible.map((d) => (
            <DiagnosisTile
              key={d.id}
              d={d}
              onExpand={setExpandedId}
              canOpenSector={canOpenSector}
            />
          ))}
        </div>
      ) : (
        <EmptyState title="Sin diagnósticos">
          Ningún diagnóstico coincide con los filtros.
        </EmptyState>
      )}

      {expanded && (
        <CapturaModal
          thumb={expanded.thumb}
          imagenUrl={expanded.imagenUrl ?? undefined}
          estado={expanded.estado}
          ago={expanded.time}
          sectorId={expanded.sectorId}
          onClose={() => setExpandedId(null)}
        />
      )}
    </div>
  );
}
