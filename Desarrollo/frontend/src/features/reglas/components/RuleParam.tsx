/* La copia de un parámetro dentro de la fila desplegada de su pieza: campo con unidad y el rango debajo. */
import { Field, Input, InputUnit } from '@/components/ui/Field';
import { formatearValor } from '@/lib/parametrosValidation';
import { unidadSegunValor } from '@/lib/plural';
import type { ParametroRegla } from '@/types/domain';
import { idCampo } from './anclas';
import styles from './RuleCatalog.module.css';

interface RuleParamProps {
  parametro: ParametroRegla;
  /** Texto canónico a mostrar (el del borrador si lo hay). */
  valor: string;
  /** Tiene una edición sin guardar: borde de acento. */
  editado: boolean;
  error: string | null;
  /** Hay un guardado en vuelo. */
  disabled?: boolean;
  /** Sin `reglas.editar`. */
  soloLectura?: boolean;
  onChange: (texto: string) => void;
}

const num = (n: number) => String(n).replace('.', ',');

/** "35–60 %"; si la copia no está en fábrica, también cuánto vale de fábrica. */
function ayuda(p: ParametroRegla): string {
  const partes: string[] = [];
  if (p.min !== null && p.max !== null) {
    partes.push(`${num(p.min)}–${num(p.max)}${p.unidad ? ` ${unidadSegunValor(p.unidad, p.max)}` : ''}`);
  }
  if (p.modificado) partes.push(`fábrica ${formatearValor(p, p.fabrica).replace('.', ',')}`);
  return partes.join(' · ');
}

export function RuleParam({ parametro: p, valor, editado, error, disabled, soloLectura, onChange }: RuleParamProps) {
  const id = idCampo(p);
  const comun = {
    disabled,
    readOnly: soloLectura,
    invalid: error !== null,
    className: editado && error === null ? styles.editado : undefined,
  };

  let control: React.ReactNode;
  if (p.tipo === 'HORA') {
    control = <Input id={id} type="time" value={valor} onChange={(e) => onChange(e.target.value)} {...comun} />;
  } else if (p.tipo === 'VENTANA_HORARIA') {
    const [desde = '', hasta = ''] = valor.split('-');
    control = (
      <span className={styles.ventana}>
        <Input
          id={id}
          type="time"
          aria-label={`${p.etiqueta} (desde)`}
          value={desde}
          onChange={(e) => onChange(`${e.target.value}-${hasta}`)}
          {...comun}
        />
        <span aria-hidden="true">a</span>
        <Input
          type="time"
          aria-label={`${p.etiqueta} (hasta)`}
          value={hasta}
          onChange={(e) => onChange(`${desde}-${e.target.value}`)}
          {...comun}
        />
      </span>
    );
  } else {
    control = (
      <InputUnit
        id={id}
        type="number"
        inputMode="decimal"
        step={p.decimales > 0 ? 10 ** -p.decimales : 1}
        unit={unidadSegunValor(p.unidad, valor)}
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        {...comun}
      />
    );
  }

  return (
    <div className={styles.param} data-clave={p.clave} title={p.descripcion}>
      <Field label={p.etiqueta} htmlFor={id} hint={ayuda(p) || undefined} error={error}>
        {control}
      </Field>
    </div>
  );
}
