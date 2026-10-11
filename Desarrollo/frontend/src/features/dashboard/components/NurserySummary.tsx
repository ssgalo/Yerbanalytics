import { Link } from 'react-router-dom';
import type { Stats, Status, Zona } from '@/types/domain';
import { CompositionBar } from '@/components/ui/Medidas';
import { StateGlyph } from '@/components/ui/StateGlyph';
import { cx } from '@/components/ui/estado';
import {
  actionsBreakdown,
  clockTime,
  equipmentText,
  latestReading,
  type EquipmentSummary,
} from '../dashboardSummary';
import styles from './NurserySummary.module.css';

interface NurserySummaryProps {
  stats: Stats;
  zonas: Zona[];
  /** `null` mientras carga; `undefined` si el rol no puede ver los equipos. */
  equipment: EquipmentSummary | null | undefined;
  /** Lleva a la actividad del día; sin él (rol sin historial), la cifra no es tocable. */
  onShowActivity?: () => void;
}

/** Cifra de un estado: número en la tinta del estado y su glifo al lado del texto. */
function StatusFigure({ status, value, label }: { status: Status; value: number; label: string }) {
  return (
    <div className={styles.figure}>
      <span className={styles.number} data-status={status}>
        {value}
      </span>
      <span className={styles.figureLabel}>
        <StateGlyph status={status} />
        {label}
      </span>
    </div>
  );
}

/**
 * Resumen del vivero: la composición real de los sectores, lo que hizo el sistema hoy y los
 * equipos con problemas. Responde de un vistazo cuánto del vivero hay que atender.
 */
export function NurserySummary({ stats, zonas, equipment, onShowActivity }: NurserySummaryProps) {
  const lastTs = latestReading(zonas);
  const pct = stats.total > 0 ? Math.round((stats.sano / stats.total) * 100) : 0;

  const actions = (
    <>
      <span className={styles.heading}>Acciones del sistema hoy</span>
      <span className={styles.number}>{stats.actToday}</span>
      <span className={styles.sideText}>{actionsBreakdown(stats)}</span>
    </>
  );

  return (
    <section className={styles.summary} aria-label="Resumen del vivero">
      <div className={styles.main}>
        <div className={styles.mainHead}>
          <h2 className={styles.heading}>Estado de los {stats.total} sectores</h2>
          {lastTs != null && <span className={styles.meta}>Última lectura {clockTime(lastTs)}</span>}
        </div>
        <div className={styles.figures}>
          <StatusFigure status="ok" value={stats.sano} label={`saludables, ${pct} %`} />
          <StatusFigure status="warning" value={stats.warning} label="en observación" />
          <StatusFigure status="critical" value={stats.critical} label={stats.critical === 1 ? 'crítico' : 'críticos'} />
          <StatusFigure status="offline" value={stats.offline} label="sin señal" />
        </div>
        <CompositionBar
          counts={{ ok: stats.sano, warning: stats.warning, critical: stats.critical, offline: stats.offline }}
        />
      </div>

      {onShowActivity ? (
        <button type="button" className={cx(styles.side, styles.sideAction)} onClick={onShowActivity}>
          {actions}
        </button>
      ) : (
        <div className={styles.side}>{actions}</div>
      )}

      {equipment !== undefined && (
        <Link to="/hardware" className={cx(styles.side, styles.sideAction)}>
          <span className={styles.heading}>Equipos con problemas</span>
          <span className={styles.number} data-status={equipment && equipment.total > 0 ? 'critical' : 'ok'}>
            {equipment ? equipment.total : '—'}
          </span>
          <span className={styles.sideText}>{equipment ? equipmentText(equipment) : 'Consultando los equipos…'}</span>
        </Link>
      )}
    </section>
  );
}
