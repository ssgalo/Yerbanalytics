import { useNavigate } from 'react-router-dom';
import type { PriorityItem, Stats } from '@/types/domain';
import { StatusDot } from '@/components/ui/StatusDot';
import { Badge } from '@/components/ui/Badge';
import styles from './PriorityCard.module.css';

interface PriorityCardProps {
  priority: PriorityItem[];
  alerta: Stats['alerta'];
}

/** Tarjeta de sectores que requieren atención prioritaria. */
export function PriorityCard({ priority, alerta }: PriorityCardProps) {
  const navigate = useNavigate();

  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <h2 className={styles.title}>Atención prioritaria</h2>
        {/* Badge de conteo con severidad crítica */}
        <Badge soft="var(--crit-soft)" ink="var(--crit)">
          {alerta}
        </Badge>
      </div>

      <div className={styles.list}>
        {priority.map((p) => (
          <button
            key={p.id}
            className={styles.item}
            onClick={() => navigate('/sector/' + p.id)}
            type="button"
          >
            {/* Punto de color con animación de pulse si corresponde */}
            <StatusDot
              color={p.color}
              size={9}
              animation={p.pulse !== 'none' ? p.pulse : undefined}
            />
            <div className={styles.itemMain}>
              <div className={styles.itemId}>{p.id}</div>
              <div className={styles.itemReason}>{p.reason}</div>
            </div>
            <Badge soft={p.sevSoft} ink={p.sevInk}>
              {p.sev}
            </Badge>
          </button>
        ))}
      </div>
    </div>
  );
}
