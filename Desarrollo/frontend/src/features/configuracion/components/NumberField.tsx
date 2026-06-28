/* Campo numérico etiquetado reutilizable por los formularios de configuración */
import styles from './ConfigForms.module.css';

interface NumberFieldProps {
  label: string;
  unit?: string;
  value: number;
  step?: number;
  min?: number;
  invalid?: boolean;
  onChange: (value: number) => void;
}

export function NumberField({ label, unit, value, step = 1, min = 0, invalid, onChange }: NumberFieldProps) {
  return (
    <div className={styles.field}>
      <label className={styles.label}>{label}</label>
      <div className={styles.inputWrap}>
        <input
          className={invalid ? `${styles.input} ${styles.inputError}` : styles.input}
          type="number"
          step={step}
          min={min}
          value={value}
          aria-label={label}
          onChange={(e) => {
            const n = Number(e.target.value);
            if (!Number.isNaN(n)) onChange(n);
          }}
        />
        {unit && <span className={styles.unit}>{unit}</span>}
      </div>
    </div>
  );
}
