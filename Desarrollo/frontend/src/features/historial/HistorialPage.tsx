/* Vista principal: Historial y trazabilidad (HU-11 / HU-12).
   Tabla de acciones a la izquierda, detalle de la elegida a la derecha y, desde el detalle, el
   razonamiento del motor. En celular, la tabla es una lista y el detalle se abre encima. */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { getRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import { usePageTitle } from '@/hooks/PageMeta';
import { useColumnQuery } from '@/hooks/useColumnQuery';
import { useHistory } from '@/hooks/useHistory';
import { useRuleEngineSchema } from '@/hooks/useRuleEngineSchema';
import { decidingRule } from '@/components/DAGViewer/historyGraphModel';
import type { Range } from '@/lib/columnQuery';
import { ActionDetail } from './components/ActionDetail';
import { HISTORY_COLUMNS } from './actionPresentation';
import { HistoryTable } from './components/HistoryTable';
import { ReasoningModal } from './components/ReasoningModal';
import styles from './HistorialPage.module.css';

/**
 * Acciones que se piden por consulta (las más recientes). El historial real acumula cientos de miles de
 * eventos `Info`: bajarlo entero colgaba la pestaña y el backend.
 */
export const LIMITE_HISTORIAL = 1000;

const NEWEST_FIRST = { column: 'fecha', dir: 'desc' } as const;

export function HistorialPage() {
  // El rango de fechas también se pide al backend: así se puede ir más atrás que el tope.
  const [range, setRange] = useState<Range | null>(null);
  const { puede } = useAuth();
  const { records, loading, error, reload } = useHistory({
    desde: range?.min,
    hasta: range?.max,
    limit: LIMITE_HISTORIAL,
  });
  const { schema } = useRuleEngineSchema();

  // Levantar el bloqueo de una acción sin efectividad (S-06) es de quien tiene `efectividad.revisar`.
  const registerReview = puede('efectividad.revisar')
    ? async (eventId: string, detail: string) => {
        await getRepository().registerEffectivenessReview(eventId, detail);
        reload();
      }
    : undefined;

  const query = useColumnQuery(records, HISTORY_COLUMNS, NEWEST_FIRST);
  const dateFilter = (query.filters.fecha as Range | null | undefined) ?? null;
  useEffect(() => setRange(dateFilter), [dateFilter]);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [reasoningOpen, setReasoningOpen] = useState(false);

  // Sin elección (o si los filtros la dejaron afuera), se ve la primera de la tabla.
  const selected = query.visible.find((r) => r.id === selectedId) ?? query.visible[0] ?? null;

  const ruleLabel = useCallback(
    (id: string) => {
      const node = schema?.nodes.find((n) => n.id === id);
      return node ? node.label : id;
    },
    [schema],
  );
  const hasReasoning = useMemo(
    () => !!schema && !!selected && decidingRule(schema, selected) != null,
    [schema, selected],
  );

  const truncated = records.length >= LIMITE_HISTORIAL;
  usePageTitle(
    'Historial y trazabilidad',
    truncated
      ? `Últimas ${records.length} acciones registradas`
      : `${records.length} acciones registradas`,
  );

  if (error) {
    return (
      <div className={styles.state} style={{ color: 'var(--crit-ink)' }}>
        No se pudo cargar el historial: {error.message}
      </div>
    );
  }

  // Sólo la primera carga reemplaza la vista: al cambiar las fechas se conserva lo anterior hasta que llega lo nuevo.
  if (loading && records.length === 0) {
    return <div className={styles.state}>Cargando historial…</div>;
  }

  return (
    <div className={styles.page}>
      <HistoryTable
        query={query}
        selectedId={selected?.id ?? null}
        onSelect={(id) => {
          setSelectedId(id);
          setDetailOpen(true);
        }}
        footnote={
          truncated
            ? `Se muestran las ${LIMITE_HISTORIAL} acciones más recientes. Para ver más atrás, filtrá por fecha.`
            : undefined
        }
      />
      <ActionDetail
        record={selected}
        ruleLabel={ruleLabel}
        hasReasoning={hasReasoning}
        onOpenReasoning={() => setReasoningOpen(true)}
        open={detailOpen}
        onBack={() => setDetailOpen(false)}
        onReview={registerReview}
      />
      {reasoningOpen && selected && schema && (
        <ReasoningModal record={selected} schema={schema} onClose={() => setReasoningOpen(false)} />
      )}
    </div>
  );
}
