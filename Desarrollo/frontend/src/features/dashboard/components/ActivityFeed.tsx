import type { ActionEvent } from '@/types/domain';
import { Glyph } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import styles from './ActivityFeed.module.css';

interface ActivityFeedProps {
  actions: ActionEvent[];
}

/** Feed de decisiones autónomas del sistema con línea de tiempo. */
export function ActivityFeed({ actions }: ActivityFeedProps) {
  return (
    <div className={styles.card}>
      <h2 className={styles.title}>Actividad del sistema</h2>
      <div className={styles.subtitle}>
        Decisiones autónomas y sus condiciones desencadenantes
      </div>

      <div className={styles.feed}>
        {actions.map((ev, i) => (
          <div key={i} className={styles.entry}>
            {/* Columna izquierda: ícono + línea vertical */}
            <div className={styles.timeline}>
              <span
                className={styles.iconWrap}
                style={{ background: ev.tint, color: ev.ink }}
              >
                <Glyph path={ev.path} stroke="currentColor" size={16} />
              </span>
              {/* Línea solo entre entradas, no la última */}
              {i < actions.length - 1 && (
                <span className={styles.connector} />
              )}
            </div>

            {/* Cuerpo del evento */}
            <div className={styles.body}>
              <div className={styles.metaRow}>
                <span className={styles.evTitle}>{ev.title}</span>
                <span className={styles.evTime}>{ev.time}</span>
                <Badge soft={ev.resSoft} ink={ev.resInk}>
                  {ev.result}
                </Badge>
              </div>
              <div className={styles.evDetail}>{ev.detail}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
