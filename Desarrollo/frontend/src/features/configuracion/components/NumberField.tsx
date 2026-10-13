/* Campo numérico etiquetado reutilizable por los formularios de configuración */
import { useId, type ChangeEvent } from 'react';
import { Field, Input, InputUnit } from '@/components/ui/Field';
import { ayudaDe } from '@/components/ui/clases';

interface NumberFieldProps {
  label: string;
  unit?: string;
  value: number;
  step?: number;
  min?: number;
  invalid?: boolean;
  /** Distinto del valor guardado: borde de "modificado". */
  modified?: boolean;
  disabled?: boolean;
  /** Aclaración debajo del campo (p. ej. que no interviene en decisiones del motor). */
  hint?: string;
  /** Sin rótulo visible cuando otro texto ya nombra el campo (queda como `aria-label`). */
  hideLabel?: boolean;
  onChange: (value: number) => void;
}

export function NumberField({ label, unit, value, step = 1, min = 0, invalid, modified, disabled, hint, hideLabel, onChange }: NumberFieldProps) {
  const id = useId();
  const props = {
    id,
    type: 'number',
    inputMode: 'decimal' as const,
    step,
    min,
    value,
    disabled,
    invalid,
    modified,
    'aria-describedby': hint ? ayudaDe(id) : undefined,
    onChange: (e: ChangeEvent<HTMLInputElement>) => {
      const n = Number(e.target.value);
      if (!Number.isNaN(n)) onChange(n);
    },
  };
  const control = unit ? <InputUnit unit={unit} {...props} /> : <Input {...props} />;
  if (hideLabel) return unit ? <InputUnit unit={unit} {...props} aria-label={label} /> : <Input {...props} aria-label={label} />;
  return (
    <Field label={label} htmlFor={id} hint={hint}>
      {control}
    </Field>
  );
}
