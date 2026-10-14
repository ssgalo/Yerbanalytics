/* Intervalo del pulso de sensado y evaluación: el único que marca cuándo se lee y cuándo decide el motor. */
import { useId, useState, type ReactNode } from 'react';
import type { ConfigOperativa } from '@/types/domain';
import { PULSE_MAX_MINUTES, PULSE_MIN_MINUTES, pulseIntervalValid } from '@/lib/configValidation';
import { formatDuration } from '@/lib/horario';
import { Field, Input } from '@/components/ui/Field';
import { Segmented } from '@/components/ui/Seleccion';
import { ayudaDe } from '@/components/ui/clases';
import styles from './ConfigForms.module.css';

type Unidad = 'min' | 'hs';

const UNIDADES: { value: Unidad; label: string }[] = [
  { value: 'min', label: 'Minutos' },
  { value: 'hs', label: 'Horas' },
];

interface IntervalosFormProps {
  value: ConfigOperativa;
  saved?: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

/** Campo de intervalo con selector de unidad integrado */
function IntervalField({
  label,
  hint,
  valueMinutes,
  invalid,
  modified,
  onChangeMinutes,
}: {
  label: string;
  hint: ReactNode;
  valueMinutes: number;
  invalid?: boolean;
  modified?: boolean;
  onChangeMinutes: (minutes: number) => void;
}) {
  const id = useId();
  // Inicializar la unidad: si el valor viene en horas exactas, mostrarlo en horas
  const [unit, setUnit] = useState<Unidad>(valueMinutes >= 60 && valueMinutes % 60 === 0 ? 'hs' : 'min');
  const displayValue = unit === 'hs' ? valueMinutes / 60 : valueMinutes;

  const handleValueChange = (raw: string) => {
    const n = parseFloat(raw);
    if (!Number.isNaN(n) && n > 0) {
      onChangeMinutes(Math.round(n * (unit === 'hs' ? 60 : 1)));
    }
  };

  return (
    <Field label={label} htmlFor={id} hint={hint}>
      <div className={styles.intervalRow}>
        <Input
          id={id}
          className={styles.intervalInput}
          type="number"
          inputMode="decimal"
          min={1}
          step={1}
          value={displayValue}
          invalid={invalid}
          modified={modified}
          aria-describedby={ayudaDe(id)}
          onChange={(e) => handleValueChange(e.target.value)}
        />
        {/* Cambiar la unidad sólo cambia cómo se muestra: el valor en minutos queda igual. */}
        <Segmented label="Unidad" options={UNIDADES} value={unit} onChange={setUnit} />
      </div>
    </Field>
  );
}

export function IntervalosForm({ value, saved, onChange }: IntervalosFormProps) {
  const stale = value.effectiveStaleThresholdSeconds;
  return (
    <div className={styles.grid}>
      <IntervalField
        label="Intervalo de sensado y evaluación"
        hint={
          <>
            En cada franja, anclada a las 02:00, se leen los sensores de todas las macro-zonas y se evalúa
            el motor de reglas. Rango {PULSE_MIN_MINUTES} a {PULSE_MAX_MINUTES} min.
            {/* El umbral lo calcula el backend al guardar; el navegador no repite la fórmula. */}
            {stale != null &&
              ` Umbral de lectura vieja (calculado por el backend): ${stale} s (${formatDuration(Math.round(stale / 60))}).`}
          </>
        }
        valueMinutes={value.intervaloSensadoMinutos}
        invalid={!pulseIntervalValid(value.intervaloSensadoMinutos)}
        modified={saved !== undefined && saved.intervaloSensadoMinutos !== value.intervaloSensadoMinutos}
        onChangeMinutes={(m) => onChange({ intervaloSensadoMinutos: m })}
      />
    </div>
  );
}
