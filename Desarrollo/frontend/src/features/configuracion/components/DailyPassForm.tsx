/* Pasada diaria del riel: si corre y a qué hora. La hora se edita aunque la pasada esté apagada. */
import { useId } from 'react';
import type { ConfigOperativa } from '@/types/domain';
import { dailyPassTimeValid } from '@/lib/configValidation';
import { Field, Input } from '@/components/ui/Field';
import { Switch } from '@/components/ui/Seleccion';
import { ayudaDe } from '@/components/ui/clases';
import styles from './ConfigForms.module.css';

interface DailyPassFormProps {
  value: ConfigOperativa;
  saved?: ConfigOperativa;
  onChange: (patch: Partial<ConfigOperativa>) => void;
}

export function DailyPassForm({ value, saved, onChange }: DailyPassFormProps) {
  const id = useId();
  const { pasadaDiariaActiva: activa, pasadaDiariaHora: hora } = value;
  const horaValida = dailyPassTimeValid(hora);
  return (
    <div className={styles.stack}>
      <div className={styles.switchRow}>
        <span className={styles.switchTexts}>
          <span id={`${id}-label`} className={styles.switchLabel}>
            Pasada diaria activa
          </span>
          <span className={styles.note}>
            {activa ? `Todos los días a las ${horaValida ? hora : '—'}` : 'Desactivada: el riel sólo pasa a pedido'}
          </span>
        </span>
        <Switch checked={activa} labelledBy={`${id}-label`} onChange={(v) => onChange({ pasadaDiariaActiva: v })} />
      </div>
      <Field
        label="Hora de la pasada"
        htmlFor={`${id}-hora`}
        hint="Hora local del vivero."
        error={horaValida ? undefined : 'Ingresá una hora entre 00:00 y 23:59.'}
      >
        <Input
          id={`${id}-hora`}
          className={styles.timeInput}
          type="time"
          value={hora}
          invalid={!horaValida}
          modified={saved !== undefined && saved.pasadaDiariaHora !== hora}
          aria-describedby={ayudaDe(`${id}-hora`)}
          onChange={(e) => onChange({ pasadaDiariaHora: e.target.value })}
        />
      </Field>
      <p className={styles.note}>
        La pasada fotografía los sectores del recorrido una vez al día, y la IA los diagnostica. También se puede
        pedir a mano desde Demo Expo.
      </p>
    </div>
  );
}
