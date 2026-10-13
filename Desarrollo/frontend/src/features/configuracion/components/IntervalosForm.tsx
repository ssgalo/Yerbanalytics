/* Parámetros de frecuencia operativa — Intervalo de sensado e inferencia */
import { useId, useState } from 'react';
import type { ConfigOperativa } from '@/types/domain';
import { SENSING_PULL_MAX_SECONDS, SENSING_PULL_MIN_SECONDS } from '@/lib/configValidation';
import { Field, Input, InputUnit } from '@/components/ui/Field';
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
  hint: string;
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

/** Seconds field: how often the backend asks each node for a reading. */
function PullIntervalField({
  seconds,
  staleThresholdSeconds,
  modified,
  onChangeSeconds,
}: {
  seconds: number;
  staleThresholdSeconds?: number;
  modified?: boolean;
  onChangeSeconds: (seconds: number) => void;
}) {
  const id = 'sensing-pull-seconds';
  const invalid = !(
    Number.isInteger(seconds) &&
    seconds >= SENSING_PULL_MIN_SECONDS &&
    seconds <= SENSING_PULL_MAX_SECONDS
  );
  return (
    <Field
      label="Frecuencia de pedido de lectura a los nodos (segundos)"
      htmlFor={id}
      hint={
        <>
          Cada cuánto el backend le pide una lectura a cada nodo; no es el intervalo de sensado de riego.
          Rango {SENSING_PULL_MIN_SECONDS} a {SENSING_PULL_MAX_SECONDS} s.
          {staleThresholdSeconds != null &&
            ` Umbral de lectura vieja (calculado por el backend): ${staleThresholdSeconds} s.`}
        </>
      }
    >
      <InputUnit
        id={id}
        unit="s"
        className={styles.pullInput}
        type="number"
        inputMode="numeric"
        min={SENSING_PULL_MIN_SECONDS}
        max={SENSING_PULL_MAX_SECONDS}
        step={1}
        value={Number.isNaN(seconds) ? '' : seconds}
        invalid={invalid}
        modified={modified}
        aria-describedby={ayudaDe(id)}
        onChange={(e) => onChangeSeconds(e.target.valueAsNumber)}
      />
    </Field>
  );
}

export function IntervalosForm({ value, saved, onChange }: IntervalosFormProps) {
  const cambio = (k: keyof ConfigOperativa) => saved !== undefined && saved[k] !== value[k];
  return (
    <div className={styles.grid}>
      <IntervalField
        label="Intervalo de Evaluación (IA/Watchdog)"
        hint="Cada cuánto el backend analiza todos los sectores y procesa las fotos."
        valueMinutes={value.intervaloEvaluacionMinutos}
        invalid={value.intervaloEvaluacionMinutos <= 0}
        modified={cambio('intervaloEvaluacionMinutos')}
        onChangeMinutes={(m) => onChange({ intervaloEvaluacionMinutos: m })}
      />
      <IntervalField
        label="Intervalo de Sensado (IoT)"
        hint="Cada cuánto los nodos reportan sus lecturas."
        valueMinutes={value.intervaloSensadoMinutos}
        invalid={value.intervaloSensadoMinutos <= 0}
        modified={cambio('intervaloSensadoMinutos')}
        onChangeMinutes={(m) => onChange({ intervaloSensadoMinutos: m })}
      />
      <PullIntervalField
        seconds={value.sensingPullIntervalSeconds}
        staleThresholdSeconds={value.effectiveStaleThresholdSeconds}
        modified={cambio('sensingPullIntervalSeconds')}
        onChangeSeconds={(n) => onChange({ sensingPullIntervalSeconds: n })}
      />
    </div>
  );
}
