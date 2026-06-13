import type { Stats } from '@/types/domain';
import { ProgressBar } from '@/components/ui/ProgressBar';
import styles from './KpiRow.module.css';

interface KpiRowProps {
  stats: Stats;
}

/** Fila de 4 KPIs del panel principal. */
export function KpiRow({ stats }: KpiRowProps) {
  return (
    <div className={styles.grid}>

      {/* KPI 1 — Sectores saludables */}
      <div className={styles.card}>
        <div className={styles.label}>Sectores saludables</div>
        <div className={styles.baseline}>
          <span
            className={styles.mainNumber}
            style={{ color: 'var(--ok)' }}
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

      {/* KPI 2 — Sectores en alerta */}
      <div className={styles.card}>
        <div className={styles.label}>Sectores en alerta</div>
        <div
          className={styles.mainNumber}
          style={{ color: 'var(--warn)' }}
        >
          {stats.alerta}
        </div>
        <div className={styles.chips}>
          <span
            className={styles.chip}
            style={{ background: 'var(--warn-soft)', color: '#A66A12' }}
          >
            <span
              className={styles.chipDot}
              style={{ background: 'var(--warn)' }}
            />
            {stats.warning} observación
          </span>
          <span
            className={styles.chip}
            style={{ background: 'var(--crit-soft)', color: '#A8331C' }}
          >
            <span
              className={styles.chipDot}
              style={{ background: 'var(--crit)' }}
            />
            {stats.critical} crítico
          </span>
        </div>
      </div>

      {/* KPI 3 — Hardware fuera de servicio */}
      <div className={styles.card}>
        <div className={styles.label}>Hardware fuera de servicio</div>
        <div
          className={styles.mainNumber}
          style={{ color: 'var(--off)' }}
        >
          {stats.offline}
        </div>
        <div className={styles.subTextHardware}>Nodos testigo sin reportar</div>
      </div>

      {/* KPI 4 — Acciones autónomas (fondo oscuro) */}
      <div
        className={styles.cardDark}
        style={{ background: 'linear-gradient(150deg,var(--g800),var(--g700))', border: '1px solid var(--g700)' }}
      >
        <div className={styles.labelDim}>Acciones autónomas hoy</div>
        <div
          className={styles.mainNumber}
          style={{ color: 'var(--cream)' }}
        >
          {stats.actToday}
        </div>
        <div className={styles.actRow}>
          <span><b className={styles.actNum}>{stats.actRiego}</b> riego</span>
          <span><b className={styles.actNum}>{stats.actInsumo}</b> insumo</span>
          <span><b className={styles.actNum}>{stats.actSombra}</b> sombra</span>
        </div>
      </div>

    </div>
  );
}
