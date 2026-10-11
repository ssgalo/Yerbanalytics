import {
  forwardRef,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react';
import { cx } from './estado';
import { ayudaDe } from './clases';
import styles from './Field.module.css';

interface FieldProps {
  label: ReactNode;
  /** id del control: une la etiqueta, la ayuda y el error. */
  htmlFor: string;
  hint?: ReactNode;
  /** Mensaje de error: reemplaza a la ayuda y se anuncia. */
  error?: ReactNode;
  optional?: boolean;
  children: ReactNode;
}

/** Etiqueta + control + ayuda (o error). El control se pasa como hijo con el mismo `id`. */
export function Field({ label, htmlFor, hint, error, optional, children }: FieldProps) {
  return (
    <div className={styles.field}>
      <label className={styles.label} htmlFor={htmlFor}>
        {label} {optional && <span className={styles.optional}>(opcional)</span>}
      </label>
      {children}
      {error ? (
        <span id={ayudaDe(htmlFor)} className={styles.error} role="alert">
          {error}
        </span>
      ) : (
        hint && (
          <span id={ayudaDe(htmlFor)} className={styles.hint}>
            {hint}
          </span>
        )
      )}
    </div>
  );
}

interface EstadoCampo {
  /** Valor con error: borde crítico y `aria-invalid`. */
  invalid?: boolean;
  /** Valor modificado y sin guardar: borde agua. */
  modified?: boolean;
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & EstadoCampo;

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid, modified, className, ...rest },
  ref,
) {
  return (
    <input
      ref={ref}
      className={cx(styles.input, modified && styles.mod, invalid && styles.err, className)}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  );
});

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & EstadoCampo;

export function Select({ invalid, modified, className, ...rest }: SelectProps) {
  return (
    <select
      className={cx(styles.select, modified && styles.mod, invalid && styles.err, className)}
      aria-invalid={invalid || undefined}
      {...rest}
    />
  );
}

interface InputUnitProps extends InputProps {
  /** Unidad o prefijo visible ("%", "litros", "MZ-"). */
  unit: ReactNode;
  /** La unidad va antes del valor. */
  unitBefore?: boolean;
}

/** Campo numérico con su unidad pegada. */
export const InputUnit = forwardRef<HTMLInputElement, InputUnitProps>(function InputUnit(
  { unit, unitBefore, invalid, modified, className, style, ...rest },
  ref,
) {
  const u = <span className={cx(styles.unitText, unitBefore && styles.prefix)}>{unit}</span>;
  return (
    <span className={cx(styles.unit, modified && styles.mod, invalid && styles.err, className)} style={style}>
      {unitBefore && u}
      <input ref={ref} aria-invalid={invalid || undefined} {...rest} />
      {!unitBefore && u}
    </span>
  );
});
