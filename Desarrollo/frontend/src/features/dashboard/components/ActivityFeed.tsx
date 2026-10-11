import { Link } from 'react-router-dom';
import type { ActionRecord } from '@/types/domain';
import { Glyph } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import { pluralizar } from '../resumenVivero';
import styles from './ActivityFeed.module.css';

interface ActivityFeedProps {
  /** Historial del día en curso, más recientes primero. */
  records: ActionRecord[];
  /** Total de eventos de hoy; si supera a `records`, el resto está en el Historial. */
  total: number;
  loading: boolean;
  error: Error | null;
}

/** Hora del registro ("HH:mm"); `fecha` llega como "dd/MM HH:mm". */
function hora(r: ActionRecord): string {
  return r.fecha.split(' ')[1] ?? r.time;
}

/** Feed de las decisiones autónomas del día, con línea de tiempo. Es el historial recortado a hoy. */
export function ActivityFeed({ records, total, loading, error }: ActivityFeedProps) {
  return (
    <div className={styles.card}>
      <div className={styles.head}>
        <div>
          <h2 className={styles.title}>Actividad del sistema</h2>
          <div className={styles.subtitle}>
            Decisiones autónomas de hoy y sus condiciones desencadenantes
            {!loading && !error && ` · ${pluralizar(total, 'evento', 'eventos')}`}
          </div>
        </div>
        <Link to="/historial" className={styles.verTodo}>
          Ver historial completo
        </Link>
      </div>

      {error ? (
        <div className={styles.empty}>No se pudo cargar la actividad: {error.message}</div>
      ) : loading ? (
        <div className={styles.empty}>Cargando actividad…</div>
      ) : records.length === 0 ? (
        <div className={styles.empty}>
          Sin actividad registrada hoy — el motor aún no ejecutó acciones.
        </div>
      ) : (
        <div className={styles.feed}>
          {records.map((ev, i) => (
            <div key={ev.id} className={styles.entry}>
              {/* Columna izquierda: ícono + línea vertical */}
              <div className={styles.timeline}>
                <span
                  className={styles.iconWrap}
                  style={{ background: ev.tint, color: ev.ink }}
                >
                  <Glyph path={ev.path} stroke="currentColor" size={16} />
                </span>
                {/* Línea solo entre entradas, no la última */}
                {i < records.length - 1 && (
                  <span className={styles.connector} />
                )}
              </div>

              {/* Cuerpo del evento */}
              <div className={styles.body}>
                <div className={styles.metaRow}>
                  <span className={styles.evTitle}>
                    {ev.tipo} · {ev.sectorId}
                  </span>
                  <span className={styles.evTime} title={ev.time}>
                    {hora(ev)}
                  </span>
                  <Badge soft={ev.resSoft} ink={ev.resInk}>
                    {ev.res}
                  </Badge>
                </div>
                <div className={styles.evDetail}>{ev.decision || ev.lectura}</div>
              </div>
            </div>
          ))}
          {total > records.length && (
            <div className={styles.mas}>
              Mostrando los {records.length} más recientes —{' '}
              <Link to="/historial">el resto está en el historial</Link>.
            </div>
          )}
        </div>
      )}
    </div>
  );
}
