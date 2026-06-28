/* Parámetros de seguimiento post-acción (HU-15 CA-07) */
import type { ConfigOperativa } from '@/types/domain';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface SeguimientoFormProps {
  value: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

export function SeguimientoForm({ value, onChange }: SeguimientoFormProps) {
  return (
    <div className={styles.grid}>
      <NumberField
        label="Latencia de seguimiento"
        unit="min"
        value={value.seguimientoLatenciaMin}
        invalid={value.seguimientoLatenciaMin <= 0}
        onChange={(v) => onChange({ seguimientoLatenciaMin: v })}
      />
      <NumberField
        label="Delta mín. de recuperación"
        unit="puntos"
        step={0.5}
        value={value.seguimientoDeltaMin}
        invalid={value.seguimientoDeltaMin <= 0}
        onChange={(v) => onChange({ seguimientoDeltaMin: v })}
      />
    </div>
  );
}
