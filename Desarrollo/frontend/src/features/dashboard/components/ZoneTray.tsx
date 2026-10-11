import type { CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { Status, Zona } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import { TrayCell } from '@/components/ui/SectorCell';
import { cx } from '@/components/ui/estado';
import { WEAK_SIGNAL_DBM, countByStatus, gridColumns } from '../dashboardSummary';
import styles from './ZoneTray.module.css';

interface ZoneTrayProps {
  zona: Zona;
  sectorsPerRow: number;
}

const FOOT: { status: Status; one: string; many: string }[] = [
  { status: 'ok', one: 'saludable', many: 'saludables' },
  { status: 'warning', one: 'en observación', many: 'en observación' },
  { status: 'critical', one: 'crítico', many: 'críticos' },
  { status: 'offline', one: 'sin señal', many: 'sin señal' },
];

/** Estado del nodo testigo en una línea: sólo se destaca lo que hay que mirar. */
function NodeStatus({ zona }: { zona: Zona }) {
  const { nodo, lectura } = zona;
  const ago = lectura.ts == null ? 'sin reportes' : lectura.ago;
  if (lectura.stale) {
    return (
      <span className={styles.nodeWarn}>
        <Icon name="clock" size={13} strokeWidth={2} /> nodo: {ago}
      </span>
    );
  }
  if (nodo.bateriaBaja && nodo.battery != null) {
    return (
      <span className={styles.nodeWarn}>
        <Icon name="battery" size={13} strokeWidth={2} /> batería {nodo.battery} %
      </span>
    );
  }
  if (nodo.signal != null && nodo.signal < WEAK_SIGNAL_DBM) {
    return (
      <span className={styles.nodeWarn}>
        <Icon name="signal" size={13} strokeWidth={2} /> señal débil
      </span>
    );
  }
  return <span>nodo: {ago}</span>;
}

/** Una macro-zona en el plano: la bandeja con sus sectores, en su disposición física. Lleva al detalle. */
export function ZoneTray({ zona, sectorsPerRow }: ZoneTrayProps) {
  const stale = zona.lectura.stale;
  const counts = FOOT.map((f) => ({ ...f, n: countByStatus(zona, f.status) })).filter((f) => f.n > 0);
  const label = [
    `${zona.id}, ${zona.sub}`,
    counts.map((c) => `${c.n} ${c.n === 1 ? c.one : c.many}`).join(', '),
    stale ? 'lectura atrasada' : '',
  ]
    .filter(Boolean)
    .join(': ');

  return (
    <Link
      to={`/mapa?zona=${zona.id}`}
      className={cx(styles.tray, stale && styles.stale)}
      aria-label={`${label}. Ver detalle de la macro-zona`}
    >
      <div className={styles.head}>
        <span className={styles.code}>{zona.id}</span>
        <span className={styles.sub}>{zona.sub}</span>
        <span className={styles.node} title="Nodo testigo: habla por todos los sectores de la macro-zona">
          <NodeStatus zona={zona} />
        </span>
      </div>

      <div
        className={styles.grid}
        style={{ '--columns': gridColumns(zona.sectors.length, sectorsPerRow) } as CSSProperties}
        aria-hidden="true"
      >
        {zona.sectors.map((s) => (
          <TrayCell key={s.id} status={s.status} />
        ))}
      </div>

      {stale && (
        <span className={styles.staleNote}>
          <Icon name="clock" size={14} strokeWidth={2} /> Lectura atrasada: muestra el último estado
        </span>
      )}

      {counts.length > 0 && (
        <div className={styles.foot} aria-hidden="true">
          {counts.map((c) => (
            <span key={c.status} className={styles.footItem}>
              <span className={styles.footGlyph}>
                <TrayCell status={c.status} />
              </span>
              <b>{c.n}</b> {c.n === 1 ? c.one : c.many}
            </span>
          ))}
        </div>
      )}
    </Link>
  );
}
