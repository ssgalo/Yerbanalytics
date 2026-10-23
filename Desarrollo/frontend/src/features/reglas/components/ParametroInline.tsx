/* Un parámetro editable dentro de la tarjeta de su regla (o del bloque de compartidos): Field + InputUnit del sistema de diseño. */
import { Field, Input, InputUnit } from '@/components/ui/Field';
import { Chip } from '@/components/ui/Chip';
import { formatearValor } from '@/lib/parametrosValidation';
import { unidadSegunValor } from '@/lib/plural';
import type { ParametroRegla } from '@/types/domain';
import { fechaCorta, idParametro } from './anclas';
import styles from './ReglasTab.module.css';

interface ParametroInlineProps {
  parametro: ParametroRegla;
  /** Texto canónico a mostrar (el del borrador si lo hay). */
  valor: string;
  /** Tiene una edición pendiente. */
  editado: boolean;
  errorCliente: string | null;
  errorServidor: string | null;
  /** Hay un guardado en vuelo: no se edita hasta que vuelva la respuesta. */
  disabled?: boolean;
  /** Sin `reglas.editar`: el campo se ve de sólo lectura y no se ofrece "Restablecer fábrica". */
  soloLectura?: boolean;
  /** Pie extra (en el bloque de compartidos: quiénes lo usan). */
  pie?: React.ReactNode;
  onChange: (texto: string) => void;
  onRestablecer: () => void;
}

export function ParametroInline({
  parametro: p,
  valor,
  editado,
  errorCliente,
  errorServidor,
  disabled,
  soloLectura,
  pie,
  onChange,
  onRestablecer,
}: ParametroInlineProps) {
  const id = idParametro(p.clave);
  const error = errorCliente ?? errorServidor;
  const rango =
    p.min !== null && p.max !== null
      ? `${p.min}–${p.max} ${unidadSegunValor(p.unidad, p.max)} · `
      : '';
  const hint = (
    <>
      {rango}fábrica {formatearValor(p, p.fabrica)}
    </>
  );
  const comun = { disabled, readOnly: soloLectura, invalid: error !== null, modified: editado };

  let control: React.ReactNode;
  if (p.tipo === 'HORA') {
    control = (
      <Input
        id={id}
        type="time"
        value={valor}
        onChange={(e) => onChange(e.target.value)}
        {...comun}
      />
    );
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
      <Field
        label={
          <>
            {p.etiqueta}
            {(p.modificado || editado) && (
              <>
                {' '}
                <Chip tone="info">{editado ? 'Sin guardar' : 'Modificado'}</Chip>
              </>
            )}
          </>
        }
        htmlFor={id}
        hint={hint}
        error={error}
      >
        {control}
      </Field>
      {p.modificado && p.updatedBy && !editado && (
        <div className={styles.auditoria}>
          Modificado por {p.updatedBy}
          {p.updatedTs ? ` · ${fechaCorta(p.updatedTs)}` : ''}
        </div>
      )}
      {pie}
      {(p.modificado || editado) && !soloLectura && (
        <button type="button" className={styles.link} disabled={disabled} onClick={onRestablecer}>
          Restablecer fábrica
        </button>
      )}
    </div>
  );
}
