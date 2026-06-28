/* Vista principal: Historial y trazabilidad (HU-11 / HU-12) */
import { useMemo, useState } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useHistory } from '@/hooks/useHistory';
import { HistorialFilters, type HistorialFilterState } from './components/HistorialFilters';
import { HistorialTimeline } from './components/HistorialTimeline';
import styles from './HistorialPage.module.css';

const INITIAL: HistorialFilterState = {
  tipo: 'Todas',
  zona: 'Todas',
  sector: '',
  resultado: 'Todas',
  desde: '',
  hasta: '',
};

export function HistorialPage() {
  const { records, loading, error } = useHistory();
  const [filters, setFilters] = useState<HistorialFilterState>(INITIAL);

  const patch = (p: Partial<HistorialFilterState>) => setFilters((f) => ({ ...f, ...p }));

  /** Macro-zonas presentes en el historial (data-driven, sirve para mock y http). */
  const zonaOpts = useMemo(
    () => [...new Set(records.map((r) => r.zonaName))].sort(),
    [records],
  );

  /** Filtrado en memoria, replicando el patrón de Diagnósticos. */
  const filtered = useMemo(() => {
    const desdeMs = filters.desde ? new Date(`${filters.desde}T00:00:00`).getTime() : null;
    const hastaMs = filters.hasta ? new Date(`${filters.hasta}T23:59:59.999`).getTime() : null;
    const sectorQ = filters.sector.trim().toLowerCase();

    return records.filter(
      (r) =>
        (filters.tipo === 'Todas' || r.tipo === filters.tipo) &&
        (filters.zona === 'Todas' || r.zonaName === filters.zona) &&
        (filters.resultado === 'Todas' || r.res === filters.resultado) &&
        (sectorQ === '' || r.sectorId.toLowerCase().includes(sectorQ)) &&
        (desdeMs === null || r.ts >= desdeMs) &&
        (hastaMs === null || r.ts <= hastaMs),
    );
  }, [records, filters]);

  usePageTitle('Historial y trazabilidad', `${records.length} acciones registradas`);

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit)' }}>
        No se pudo cargar el historial: {error.message}
      </div>
    );
  }

  if (loading) {
    return <div className={styles.state}>Cargando historial…</div>;
  }

  return (
    <div>
      <HistorialFilters value={filters} zonaOpts={zonaOpts} count={filtered.length} onChange={patch} />
      <HistorialTimeline records={filtered} />
    </div>
  );
}
