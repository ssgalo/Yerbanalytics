import type { Stats } from '@/types/domain';
import { ProgressBar } from '@/components/ui/ProgressBar';
import styles from './KpiRow.module.css';

interface KpiRowProps {
  stats: Stats;
}

/** Fila de 3 KPIs del panel principal. */
export function KpiRow({ stats }: KpiRowProps) {
  /* `stats.alerta` del snapshot no incluye los sectores sin señal: se cuenta acá. */
  const alerta = stats.warning + stats.critical + stats.offline;

  return (
    <div className={styles.grid}>

      {/* KPI 1 — Sectores saludables */}
      <div className={styles.card}>
        <div className={styles.label}>Sectores saludables</div>
        <div className={styles.baseline}>
          <span
            className={styles.mainNumber}
            style={{ color: 'var(--ok-ink)' }}
          >
            {stats.sano}
          </span>
          <span className={styles.total}>/ {stats.total}</span>
        </div>
        <div style={{ marginTop: 11 }}>
          <ProgressBar value={stats.sanoPct} color="var(--ok)" />
        </div>
        <div className={styles.subText}>
          {stats.sanoPct}% del vivero en parámetros óptimos
        </div>
      </div>

      {/* KPI 2 — Sectores en alerta: con los saludables suman la totalidad. Los sectores de una
          macro-zona con el nodo testigo fuera de servicio también cuentan acá, como "sin señal". */}
      <div className={styles.card}>
        <div className={styles.label}>Sectores en alerta</div>
        <div className={styles.baseline}>
          <span
            className={styles.mainNumber}
            style={{ color: 'var(--warn-ink)' }}
          >
            {alerta}
          </span>
          <span className={styles.total}>/ {stats.total}</span>
        </div>
        <div className={styles.chips}>
          <span
            className={styles.chip}
            style={{ background: 'var(--warn-soft)', color: 'var(--warn-ink)' }}
          >
            <span
              className={styles.chipDot}
              style={{ background: 'var(--warn)' }}
            />
            {stats.warning} observación
          </span>
          <span
            className={styles.chip}
            style={{ background: 'var(--crit-soft)', color: 'var(--crit-ink)' }}
          >
            <span
              className={styles.chipDot}
              style={{ background: 'var(--crit)' }}
            />
            {stats.critical} crítico
          </span>
          <span
            className={styles.chip}
            style={{ background: 'var(--off-soft)', color: 'var(--off-ink)' }}
          >
            <span
              className={styles.chipDot}
              style={{ background: 'var(--off)' }}
            />
            {stats.offline} sin señal
          </span>
        </div>
      </div>

      {/* KPI 3 — Acciones autónomas: en agua, el color de "el sistema actuó" */}
      <div className={styles.cardSys}>
        <div className={styles.labelDim}>Acciones autónomas hoy</div>
        <div className={styles.mainNumber}>{stats.actToday}</div>
        <div className={styles.actRow}>
          <span><b className={styles.actNum}>{stats.actRiego}</b> riego</span>
          <span><b className={styles.actNum}>{stats.actInsumo}</b> insumo</span>
          <span><b className={styles.actNum}>{stats.actSombra}</b> sombra</span>
        </div>
      </div>

    </div>
  );
}
