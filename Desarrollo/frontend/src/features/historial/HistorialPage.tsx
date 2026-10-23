/* Vista principal: Historial y trazabilidad + Inspector de Decisiones (HU-11 / HU-12) */
import { useMemo, useState } from 'react';
import { getRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { useHistory } from '@/hooks/useHistory';
import { useRuleEngineSchema } from '@/hooks/useRuleEngineSchema';
import { Callout } from '@/components/ui/Callout';
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

/**
 * Eventos que se piden por consulta (los más recientes). El historial real acumula cientos de miles de
 * eventos `Info`: bajarlo entero colgaba la pestaña y el backend.
 */
export const LIMITE_HISTORIAL = 1000;

export function HistorialPage() {
  const [filters, setFilters] = useState<HistorialFilterState>(INITIAL);

  // Tipo y fechas se resuelven en el backend; macro-zona, sector y resultado, sobre lo recibido.
  const desdeMs = filters.desde ? new Date(`${filters.desde}T00:00:00`).getTime() : undefined;
  const hastaMs = filters.hasta ? new Date(`${filters.hasta}T23:59:59.999`).getTime() : undefined;
  const { puede } = useAuth();
  const { records, loading, error, reload } = useHistory({
    tipo: filters.tipo === 'Todas' ? undefined : filters.tipo,
    desde: desdeMs,
    hasta: hastaMs,
    limit: LIMITE_HISTORIAL,
  });
  const { schema, loading: schemaLoading } = useRuleEngineSchema();

  // Levantar el bloqueo de una acción sin efectividad (S-06) es de quien tiene `efectividad.revisar`.
  const registrarRevision = puede('efectividad.revisar')
    ? async (eventId: string, detail: string) => {
        await getRepository().registerEffectivenessReview(eventId, detail);
        reload();
      }
    : undefined;

  const patch = (p: Partial<HistorialFilterState>) => setFilters((f) => ({ ...f, ...p }));

  /** Macro-zonas presentes en el historial (data-driven, sirve para mock y http). */
  const zonaOpts = useMemo(
    () => [...new Set(records.map((r) => r.zonaName))].sort(),
    [records],
  );

  /** Filtrado en memoria de lo que el backend no recorta. */
  const filtered = useMemo(() => {
    const sectorQ = filters.sector.trim().toLowerCase();

    return records.filter(
      (r) =>
        (filters.zona === 'Todas' || r.zonaName === filters.zona) &&
        (filters.resultado === 'Todas' || r.res === filters.resultado) &&
        (sectorQ === '' || r.sectorId.toLowerCase().includes(sectorQ)),
    );
  }, [records, filters.zona, filters.resultado, filters.sector]);

  const truncado = records.length >= LIMITE_HISTORIAL;

  usePageTitle(
    'Historial y trazabilidad',
    truncado ? `Últimas ${records.length} acciones registradas` : `${records.length} acciones registradas`,
  );

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit-ink)' }}>
        No se pudo cargar el historial: {error.message}
      </div>
    );
  }

  // Sólo la primera carga reemplaza la vista: al cambiar un filtro se conserva lo anterior hasta que llega lo nuevo.
  if (loading && records.length === 0) {
    return <div className={styles.state}>Cargando historial…</div>;
  }

  return (
    <div className={styles.root}>
      <HistorialFilters value={filters} zonaOpts={zonaOpts} count={filtered.length} onChange={patch} />

      {truncado && (
        <div className={styles.aviso}>
          <Callout>
            Se muestran los {LIMITE_HISTORIAL} eventos más recientes
            {filters.tipo !== 'Todas' || filters.desde || filters.hasta ? ' que cumplen el tipo y las fechas elegidos' : ''}.
            Para ver más atrás, acotá por tipo de acción o por rango de fechas.
          </Callout>
        </div>
      )}

      <div className={styles.singleColumn}>
        <HistorialTimeline
          records={filtered}
          schema={schema}
          schemaLoading={schemaLoading}
          onReview={registrarRevision}
        />
      </div>
    </div>
  );
}
