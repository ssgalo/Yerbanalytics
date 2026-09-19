/* Parámetros de frecuencia operativa — Intervalo de sensado e inferencia */
import { useState } from 'react';
import type { ConfigOperativa } from '@/types/domain';
import styles from './ConfigForms.module.css';

type Unidad = 'min' | 'hs';

interface IntervalosFormProps {
  value: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

/** Campo de intervalo con selector de unidad integrado */
function IntervalField({
  label,
  title,
  valueMinutes,
  invalid,
  onChangeMinutes,
}: {
  label: string;
  title: string;
  valueMinutes: number;
  invalid?: boolean;
  onChangeMinutes: (minutes: number) => void;
}) {
  // Inicializar la unidad: si el valor viene en horas exactas, mostrarlo en horas
  const [unit, setUnit] = useState<Unidad>(valueMinutes >= 60 && valueMinutes % 60 === 0 ? 'hs' : 'min');
  const displayValue = unit === 'hs' ? valueMinutes / 60 : valueMinutes;

  const handleValueChange = (raw: string) => {
    const n = parseFloat(raw);
    if (!Number.isNaN(n) && n > 0) {
      onChangeMinutes(Math.round(n * (unit === 'hs' ? 60 : 1)));
    }
  };

  const handleUnitChange = (newUnit: Unidad) => {
    // Al cambiar solo la unidad no cambia el valor en minutos en el padre,
    // solo el display local. Por eso propagamos el valor actual sin modificar.
    setUnit(newUnit);
    onChangeMinutes(valueMinutes);
  };

  return (
    <div className={styles.field} title={title}>
      <label className={styles.label} style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
        {label}
        <span style={{ cursor: 'help', color: 'var(--muted)', fontSize: '12px' }}>ⓘ</span>
      </label>
      <div className={styles.inputWrap}>
        <input
          className={invalid ? `${styles.input} ${styles.inputError}` : styles.input}
          type="number"
          min={1}
          step={1}
          style={{ maxWidth: '90px' }}
          value={displayValue}
          aria-label={label}
          onChange={(e) => handleValueChange(e.target.value)}
        />
        <select
          className={styles.input}
          style={{ maxWidth: '110px', cursor: 'pointer' }}
          value={unit}
          onChange={(e) => handleUnitChange(e.target.value as Unidad)}
        >
          <option value="min">Minutos</option>
          <option value="hs">Horas</option>
        </select>
      </div>
    </div>
  );
}

export function IntervalosForm({ value, onChange }: IntervalosFormProps) {
  return (
    <div className={styles.grid}>
      <IntervalField
        label="Intervalo de Evaluación (IA/Watchdog)"
        title="Cada cuánto tiempo el backend analiza todos los sectores y se procesan las fotos"
        valueMinutes={value.intervaloEvaluacionMinutos}
        invalid={value.intervaloEvaluacionMinutos <= 0}
        onChangeMinutes={(m) => onChange({ intervaloEvaluacionMinutos: m })}
      />
      <IntervalField
        label="Intervalo de Sensado (IoT)"
        title="Cada cuánto tiempo los sensores de los sectores reportan sus lecturas"
        valueMinutes={value.intervaloSensadoMinutos}
        invalid={value.intervaloSensadoMinutos <= 0}
        onChangeMinutes={(m) => onChange({ intervaloSensadoMinutos: m })}
      />
    </div>
  );
}
