/* Fila de KPIs de la flota de hardware (HU-21) */
import { Card } from '@/components/ui/Card';
import type { HardwareData } from '@/types/domain';
import styles from '../Hardware.module.css';

interface HardwareKpisProps {
  data: HardwareData;
}

export function HardwareKpis({ data }: HardwareKpisProps) {
  const items: { label: string; value: number; color?: string }[] = [
    { label: 'Dispositivos', value: data.total },
    { label: 'Operativos', value: data.operativos, color: 'var(--ok)' },
    { label: 'Batería baja', value: data.bateriaBaja, color: 'var(--warn)' },
    { label: 'Fuera de servicio', value: data.fueraDeServicio, color: 'var(--crit)' },
    { label: 'Averiados', value: data.averiados, color: 'var(--crit)' },
  ];

  return (
    <div className={styles.kpis}>
      {items.map((it) => (
        <Card key={it.label} className={styles.kpi}>
          <div className={styles.kpiValue} style={it.color ? { color: it.color } : undefined}>
            {it.value}
          </div>
          <div className={styles.kpiLabel}>{it.label}</div>
        </Card>
      ))}
    </div>
  );
}
