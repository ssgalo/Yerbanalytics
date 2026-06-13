/* Historial de acciones del sector — replica exacta del diseño HTML (líneas 532-550) */
import { Card } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import type { HistoryEntry } from '@/types/domain';
import styles from './SectorHistory.module.css';

interface SectorHistoryProps {
  hist: HistoryEntry[];
}

export function SectorHistory({ hist }: SectorHistoryProps) {
  return (
    <Card className={styles.card}>
      <h3 className={styles.title}>Historial de acciones del sector</h3>
      <div className={styles.subtitle}>
        Registro inalterable · lectura → decisión → acción ejecutada
      </div>

      <div className={styles.list}>
        {hist.map((h, i) => (
          <div key={i} className={styles.entry}>
            {/* Columna de tiempo */}
            <div className={styles.time}>{h.t}</div>

            {/* Línea de timeline con punto */}
            <div className={styles.timeline}>
              <span className={styles.timelineDot} />
            </div>

            {/* Cuerpo: tipo + badge + descripción */}
            <div className={styles.body}>
              <div className={styles.typeRow}>
                <span className={styles.tipo}>{h.tipo}</span>
                <Badge soft={h.soft} ink={h.ink} style={{ fontSize: '10.5px', fontWeight: 700, padding: '1px 8px' }}>
                  {h.res}
                </Badge>
              </div>
              <div className={styles.description}>{h.d}</div>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
}
