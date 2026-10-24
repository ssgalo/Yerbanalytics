import type { ReactNode } from 'react';
import styles from './Seleccion.module.css';

interface SegmentedProps<T extends string> {
  /** Nombre del grupo para el lector de pantalla. */
  label: string;
  options: { value: T; label: ReactNode }[];
  value: T;
  onChange: (value: T) => void;
}

/** Control segmentado: una opción de pocas (rango de tiempo, vista). */
export function Segmented<T extends string>({ label, options, value, onChange }: SegmentedProps<T>) {
  return (
    <div className={styles.seg} role="group" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" aria-pressed={o.value === value} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

interface FilterChipProps {
  pressed: boolean;
  onClick: () => void;
  children: ReactNode;
  /** Cantidad de elementos que deja el filtro. */
  count?: number;
}

/** Filtro como chip seleccionable. Varios van dentro de `FilterChips`. */
export function FilterChip({ pressed, onClick, children, count }: FilterChipProps) {
  return (
    <button type="button" className={styles.fchip} aria-pressed={pressed} onClick={onClick}>
      {children}
      {count !== undefined && <span className={styles.n}>{count}</span>}
    </button>
  );
}

export function FilterChips({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className={styles.fchips} role="group" aria-label={label}>
      {children}
    </div>
  );
}

interface SwitchProps {
  /** `'mixed'`: el interruptor de un grupo con elementos prendidos y apagados (se muestra a medio camino). */
  checked: boolean | 'mixed';
  /** Recibe el estado pedido: `false` si estaba prendido; `true` si estaba apagado o a medio camino. */
  onChange: (checked: boolean) => void;
  /** Nombre accesible, si no hay una etiqueta asociada con `aria-labelledby`. */
  label?: string;
  labelledBy?: string;
  disabled?: boolean;
  id?: string;
}

/** Interruptor encendido/apagado de efecto inmediato; `mixed` para el de un grupo. */
export function Switch({ checked, onChange, label, labelledBy, disabled, id }: SwitchProps) {
  return (
    <button
      id={id}
      type="button"
      role="switch"
      className={styles.switch}
      aria-checked={checked}
      aria-label={label}
      aria-labelledby={labelledBy}
      disabled={disabled}
      onClick={() => onChange(checked !== true)}
    />
  );
}
