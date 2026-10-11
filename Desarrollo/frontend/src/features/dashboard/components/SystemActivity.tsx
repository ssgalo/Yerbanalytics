import { forwardRef } from 'react';
import { Link } from 'react-router-dom';
import type { ActionRecord } from '@/types/domain';
import { Chip } from '@/components/ui/Chip';
import { Glyph } from '@/components/ui/Icon';
import { EmptyState, Skeleton } from '@/components/ui/EstadosPantalla';
import { ACTIVITY_ANCHOR, activityTime, activityTitle, resultTone } from '../dashboardSummary';
import styles from './SystemActivity.module.css';

interface SystemActivityProps {
  /** Historial del día en curso, más recientes primero. */
  records: ActionRecord[];
  /** Total de eventos de hoy; si supera a `records`, el resto está en el Historial. */
  total: number;
  loading: boolean;
  error: Error | null;
}

/** Línea de tiempo de lo que hizo el sistema hoy: hora, qué hizo, dónde, por qué y cómo terminó. */
export const SystemActivity = forwardRef<HTMLElement, SystemActivityProps>(function SystemActivity(
  { records, total, loading, error },
  ref,
) {
  return (
    <section
      ref={ref}
      id={ACTIVITY_ANCHOR}
      tabIndex={-1}
      className={styles.card}
      aria-labelledby="titulo-actividad"
    >
      <div className={styles.head}>
        <h2 className={styles.title} id="titulo-actividad">
          Lo que hizo el sistema hoy
        </h2>
        <Link to="/historial" className={styles.link}>
          Ver historial completo
        </Link>
      </div>

      {error ? (
        <EmptyState title="No se pudo cargar la actividad">{error.message}</EmptyState>
      ) : loading ? (
        <div className={styles.loading}>
          <Skeleton height={28} />
          <Skeleton height={28} />
          <Skeleton height={28} />
        </div>
      ) : records.length === 0 ? (
        <EmptyState title="Sin actividad hoy">El motor todavía no ejecutó acciones.</EmptyState>
      ) : (
        <>
          <ol className={styles.timeline}>
            {records.map((r) => (
              <li key={r.id} className={styles.item}>
                <span className={styles.time} title={r.time}>
                  {activityTime(r)}
                </span>
                <span className={styles.icon} data-kind={r.tipo === 'Alerta' ? 'alert' : 'action'}>
                  <Glyph path={r.path} size={16} />
                </span>
                <div className={styles.body}>
                  <div className={styles.titleRow}>
                    <span className={styles.itemTitle}>{activityTitle(r)}</span>
                    <Chip tone={resultTone(r.res)}>{r.res}</Chip>
                  </div>
                  <p className={styles.detail}>{r.decision || r.lectura}</p>
                </div>
              </li>
            ))}
          </ol>
          {total > records.length && (
            <p className={styles.more}>
              Se muestran los {records.length} más recientes de {total}.{' '}
              <Link to="/historial" className={styles.link}>
                El resto está en el historial
              </Link>
              .
            </p>
          )}
        </>
      )}
    </section>
  );
});
