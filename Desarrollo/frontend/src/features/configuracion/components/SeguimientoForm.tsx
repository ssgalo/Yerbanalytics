/* Parámetros de seguimiento post-acción (HU-15 CA-07) */
import type { ConfigOperativa } from '@/types/domain';
import { NumberField } from './NumberField';
import styles from './ConfigForms.module.css';

interface SeguimientoFormProps {
  value: ConfigOperativa;
  saved?: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

export function SeguimientoForm({ value, saved, onChange }: SeguimientoFormProps) {
  return (
    <div className={styles.stack}>
      <NumberField
        label="Latencia de seguimiento"
        unit="min"
        hint="Cuánto se espera después de actuar para medir el efecto."
        value={value.seguimientoLatenciaMin}
        invalid={value.seguimientoLatenciaMin <= 0}
        modified={saved !== undefined && saved.seguimientoLatenciaMin !== value.seguimientoLatenciaMin}
        onChange={(v) => onChange({ seguimientoLatenciaMin: v })}
      />
      <NumberField
        label="Delta mín. de recuperación"
        unit="puntos"
        step={0.5}
        hint="Mejora mínima para dar la acción por efectiva."
        value={value.seguimientoDeltaMin}
        invalid={value.seguimientoDeltaMin <= 0}
        modified={saved !== undefined && saved.seguimientoDeltaMin !== value.seguimientoDeltaMin}
        onChange={(v) => onChange({ seguimientoDeltaMin: v })}
      />
    </div>
  );
}
