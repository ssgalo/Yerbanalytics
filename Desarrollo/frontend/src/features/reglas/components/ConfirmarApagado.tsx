/* Confirmación de apagar una regla. Una protección muestra su consecuencia y exige el motivo; el resto pide confirmar y deja el motivo opcional. */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Popover } from '@/components/ui/Popover';
import type { ReglaCatalogo } from '@/types/domain';
import styles from './ReglasTab.module.css';

/** Tope del motivo, el mismo que valida el backend. */
export const MOTIVO_MAX = 500;

interface ConfirmarApagadoProps {
  regla: ReglaCatalogo;
  /** El interruptor: ancla del diálogo y a quien vuelve el foco al cancelar. */
  anchor: HTMLElement;
  /** Error del último intento (el backend puede rechazar). */
  error: string | null;
  ocupado: boolean;
  onCancelar: () => void;
  onConfirmar: (motivo: string) => void;
}

export function ConfirmarApagado({
  regla,
  anchor,
  error,
  ocupado,
  onCancelar,
  onConfirmar,
}: ConfirmarApagadoProps) {
  const [motivo, setMotivo] = useState('');
  const id = useId();
  const esProteccion = regla.proteccion !== null;
  const falta = esProteccion && motivo.trim() === '';

  const cancelar = () => {
    // Con el pedido en vuelo no hay a qué volver: el resultado decide cómo se cierra.
    if (ocupado) return;
    onCancelar();
    anchor.focus();
  };

  return (
    <Popover
      anchor={anchor}
      onClose={cancelar}
      label={esProteccion ? 'Confirmar apagado de la protección' : 'Confirmar apagado de la regla'}
      role="alertdialog"
      modal
      width={380}
      alignEnd
    >
      <h2 className={styles.dialogoTitulo}>
        {esProteccion ? '¿Apagar la protección' : '¿Apagar la regla'} «{regla.label}»?
      </h2>
      {esProteccion ? (
        <Callout tone="crit">
          <b>Si la apagás:</b> {regla.proteccion}
        </Callout>
      ) : (
        <p className={styles.dialogoTexto}>
          El motor deja de evaluarla desde el próximo pulso. Podés prenderla de nuevo cuando
          quieras.
        </p>
      )}
      <div className={styles.dialogoCampo}>
        <label htmlFor={id} className={styles.dialogoEtiqueta}>
          Motivo {!esProteccion && <span className={styles.opcional}>(opcional)</span>}
        </label>
        <textarea
          id={id}
          className={styles.textarea}
          rows={3}
          maxLength={MOTIVO_MAX}
          required={esProteccion}
          aria-required={esProteccion}
          value={motivo}
          disabled={ocupado}
          onChange={(e) => setMotivo(e.target.value)}
        />
      </div>
      {error && (
        <Callout tone="crit" role="alert">
          {error}
        </Callout>
      )}
      <div className={styles.dialogoAcciones}>
        <Button variant="ghost" onClick={cancelar} disabled={ocupado}>
          Cancelar
        </Button>
        <Button
          variant="danger"
          disabled={falta || ocupado}
          onClick={() => onConfirmar(motivo.trim())}
        >
          {esProteccion ? 'Apagar protección' : 'Apagar regla'}
        </Button>
      </div>
    </Popover>
  );
}
