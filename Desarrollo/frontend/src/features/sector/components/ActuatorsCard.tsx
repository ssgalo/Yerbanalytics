/* Tarjeta de actuadores físicos — replica exacta del diseño HTML (líneas 450-466) */
import { Card } from '@/components/ui/Card';
import { Glyph } from '@/components/ui/Icon';
import type { ActuatorRow } from '@/types/domain';
import styles from './ActuatorsCard.module.css';

interface ActuatorsCardProps {
  rows: ActuatorRow[];
}

export function ActuatorsCard({ rows }: ActuatorsCardProps) {
  return (
    <Card className={styles.card}>
      <h3 className={styles.title}>Actuadores</h3>

      <div className={styles.list}>
        {rows.map((a, i) => (
          <div key={i} className={styles.row}>
            {/* Ícono con fondo dinámico */}
            <span
              className={styles.iconWrap}
              style={{ background: a.soft, color: a.ink }}
            >
              <Glyph path={a.path} size={19} strokeWidth={1.8} />
            </span>

            {/* Nombre y estado */}
            <div className={styles.meta}>
              <div className={styles.name}>{a.name}</div>
              <div className={styles.state}>{a.state}</div>
            </div>

            {/* Punto de estado con color dinámico */}
            <span className={styles.dot} style={{ background: a.dot }} />
          </div>
        ))}
      </div>
    </Card>
  );
}
