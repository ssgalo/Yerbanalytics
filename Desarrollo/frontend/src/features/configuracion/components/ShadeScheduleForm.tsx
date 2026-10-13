/* Horario por defecto de la mediasombra: abierta de/hasta, para las macro-zonas sin plan de rustificación. */
import { useId } from 'react';
import type { ConfigOperativa } from '@/types/domain';
import { Field, Input } from '@/components/ui/Field';
import { ShadeWindowStrip } from '@/components/ShadeWindowStrip';
import { formatDuration, horarioError, windowMinutes } from '@/lib/horario';
import styles from './ConfigForms.module.css';

interface ShadeScheduleFormProps {
  value: ConfigOperativa;
  saved?: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

export function ShadeScheduleForm({ value, saved, onChange }: ShadeScheduleFormProps) {
  const id = useId();
  const { mediasombraAbiertaDesde: desde, mediasombraAbiertaHasta: hasta } = value;
  const error = horarioError(desde, hasta);
  return (
    <div className={styles.stack}>
      <div className={styles.timePair}>
        <Field label="Abierta desde" htmlFor={`${id}-desde`}>
          <Input
            id={`${id}-desde`}
            type="time"
            value={desde}
            invalid={!!error}
            modified={saved !== undefined && saved.mediasombraAbiertaDesde !== desde}
            onChange={(e) => onChange({ mediasombraAbiertaDesde: e.target.value })}
          />
        </Field>
        <Field label="Abierta hasta" htmlFor={`${id}-hasta`}>
          <Input
            id={`${id}-hasta`}
            type="time"
            value={hasta}
            invalid={!!error}
            modified={saved !== undefined && saved.mediasombraAbiertaHasta !== hasta}
            onChange={(e) => onChange({ mediasombraAbiertaHasta: e.target.value })}
          />
        </Field>
      </div>
      <ShadeWindowStrip desde={desde} hasta={hasta} scale />
      {error ? (
        <p className={styles.formError} role="alert">
          {error[0].toUpperCase() + error.slice(1)}
        </p>
      ) : (
        <p className={styles.note}>
          Abierta {formatDuration(windowMinutes(desde, hasta))} por día; cerrada el resto. Rige en toda macro-zona
          sin un plan de rustificación, y dentro de ese horario otras reglas pueden cerrarla igual.
        </p>
      )}
    </div>
  );
}
