/* Una fila de parámetro dentro de una regla (o de la vista por parámetro). */
import { unidadSegunValor } from '@/lib/plural';
import { formatearValor } from '@/lib/parametrosValidation';
import type { ParametroRegla } from '@/types/domain';
import { ParametroField } from './ParametroField';
import styles from './Parametros.module.css';

interface ParametroRowProps {
  parametro: ParametroRegla;
  /** Texto canónico a mostrar (el del borrador si lo hay). */
  valor: string;
  /** Tiene una edición pendiente: se resaltan todas sus apariciones. */
  editado: boolean;
  errorCliente: string | null;
  errorServidor: string | null;
  /** id → nombre de cada regla, para rotular quién más lo usa. */
  nombresReglas: Record<string, string>;
  /** Regla bajo la que se muestra; sus "otras reglas" excluyen a ésta. En la vista por parámetro es undefined. */
  reglaActual?: string;
  /** Hay un guardado en vuelo: no se edita hasta que vuelva la respuesta. */
  disabled?: boolean;
  /** Sin permiso de edición: el campo se ve deshabilitado y no se ofrece "Restablecer fábrica". */
  soloLectura?: boolean;
  onChange: (texto: string) => void;
  onRestablecer: () => void;
}

function fechaCorta(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function ParametroRow({
  parametro: p,
  valor,
  editado,
  errorCliente,
  errorServidor,
  nombresReglas,
  reglaActual,
  disabled,
  soloLectura,
  onChange,
  onRestablecer,
}: ParametroRowProps) {
  const otras = p.usadoPor.filter((id) => id !== reglaActual);
  const nombresOtras = otras.map((id) => nombresReglas[id] ?? id);
  // Compartido = lo declara más de una regla. En la vista por parámetro se listan todas.
  const compartido = p.usadoPor.length > 1;
  const error = errorCliente ?? errorServidor;

  const clases = [styles.fila];
  if (editado) clases.push(styles.filaEditada);
  if (error) clases.push(styles.filaError);

  return (
    <div className={clases.join(' ')} data-clave={p.clave}>
      <div className={styles.filaTexto}>
        <div className={styles.filaTitulo}>
          <span className={styles.etiqueta}>{p.etiqueta}</span>
          {p.modificado && <span className={styles.chipModificado}>Modificado</span>}
          {compartido && (
            <span
              className={styles.chipCompartido}
              title={`Es un solo valor: editarlo acá lo cambia en ${nombresOtras.join(', ') || 'todas las reglas que lo usan'}.`}
            >
              {reglaActual
                ? `Compartido con ${otras.length} ${otras.length === 1 ? 'regla' : 'reglas'}: ${nombresOtras.join(', ')}`
                : `Usado por ${p.usadoPor.length} reglas`}
            </span>
          )}
        </div>
        <div className={styles.descripcion}>{p.descripcion}</div>
        {!reglaActual && (
          <div className={styles.usadoPor}>
            Usado por: {p.usadoPor.map((id) => nombresReglas[id] ?? id).join(' · ')}
          </div>
        )}
        <div className={styles.meta}>
          {p.min !== null && p.max !== null && (
            <span>
              Rango {p.min}–{p.max} {unidadSegunValor(p.unidad, p.max ?? 0)}
            </span>
          )}
          <span>Fábrica {formatearValor(p, p.fabrica)}</span>
          <span className={styles.ref}>{p.refSpec}</span>
        </div>
        {p.modificado && p.updatedBy && (
          <div className={styles.auditoria}>
            Modificado por {p.updatedBy}
            {p.updatedTs ? ` · ${fechaCorta(p.updatedTs)}` : ''}
          </div>
        )}
        {error && (
          <div className={styles.error} role="alert">
            {error}
          </div>
        )}
      </div>

      <div className={styles.filaCampo}>
        <ParametroField parametro={p} valor={valor} invalid={error !== null} disabled={disabled} onChange={onChange} />
        {(p.modificado || editado) && !soloLectura && (
          <button type="button" className={styles.btnLink} disabled={disabled} onClick={onRestablecer}>
            Restablecer fábrica
          </button>
        )}
      </div>
    </div>
  );
}
