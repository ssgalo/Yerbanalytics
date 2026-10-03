/* Campo de edición de un parámetro según su tipo: número, hora o ventana horaria. */
import { NumberField } from '@/features/configuracion/components/NumberField';
import { unidadSegunValor } from '@/lib/plural';
import type { ParametroRegla } from '@/types/domain';
import styles from '../Reglas.module.css';

interface ParametroFieldProps {
  parametro: ParametroRegla;
  /** Texto canónico del valor a mostrar (el del borrador si lo hay). */
  valor: string;
  invalid: boolean;
  disabled?: boolean;
  onChange: (texto: string) => void;
}

export function ParametroField({ parametro: p, valor, invalid, disabled, onChange }: ParametroFieldProps) {
  if (p.tipo === 'HORA') {
    return (
      <input
        className={invalid ? `${styles.timeInput} ${styles.timeInputError}` : styles.timeInput}
        type="time"
        value={valor}
        disabled={disabled}
        aria-label={p.etiqueta}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }

  if (p.tipo === 'VENTANA_HORARIA') {
    const [desde = '', hasta = ''] = valor.split('-');
    const clase = invalid ? `${styles.timeInput} ${styles.timeInputError}` : styles.timeInput;
    return (
      <span className={styles.ventana}>
        <input
          className={clase}
          type="time"
          value={desde}
          disabled={disabled}
          aria-label={`${p.etiqueta} (desde)`}
          onChange={(e) => onChange(`${e.target.value}-${hasta}`)}
        />
        <span className={styles.ventanaSep}>a</span>
        <input
          className={clase}
          type="time"
          value={hasta}
          disabled={disabled}
          aria-label={`${p.etiqueta} (hasta)`}
          onChange={(e) => onChange(`${desde}-${e.target.value}`)}
        />
      </span>
    );
  }

  const n = Number(valor);
  return (
    <NumberField
      label={p.etiqueta}
      hideLabel
      unit={unidadSegunValor(p.unidad, valor)}
      value={Number.isFinite(n) ? n : 0}
      step={p.decimales > 0 ? 10 ** -p.decimales : 1}
      min={p.min ?? 0}
      invalid={invalid}
      disabled={disabled}
      onChange={(v) => onChange(String(v))}
    />
  );
}
