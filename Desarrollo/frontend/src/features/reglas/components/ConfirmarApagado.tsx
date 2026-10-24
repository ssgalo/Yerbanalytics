/*
 * Confirmación de apagar protecciones: sólo se abre si lo que se apaga incluye alguna (una regla común se apaga sin
 * preguntar). Muestra la consecuencia de cada protección y exige el motivo. Sirve para una pieza o para el lote del
 * interruptor de un grupo.
 */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Popover } from '@/components/ui/Popover';
import type { ReglaCatalogo } from '@/types/domain';
import styles from './RuleCatalog.module.css';

/** Tope del motivo, el mismo que valida el backend. */
export const MOTIVO_MAX = 500;

interface ConfirmarApagadoProps {
  /** Lo que se apaga: una pieza o las de un grupo. */
  reglas: ReglaCatalogo[];
  /** Nombre del grupo, si se apaga un grupo entero. */
  grupo?: string;
  /** El interruptor: ancla del diálogo y a quien vuelve el foco al cancelar. */
  anchor: HTMLElement;
  /** Error del último intento (el backend puede rechazar). */
  error: string | null;
  ocupado: boolean;
  onCancelar: () => void;
  onConfirmar: (motivo: string) => void;
}

export function ConfirmarApagado({
  reglas,
  grupo,
  anchor,
  error,
  ocupado,
  onCancelar,
  onConfirmar,
}: ConfirmarApagadoProps) {
  const [motivo, setMotivo] = useState('');
  const id = useId();
  const protecciones = reglas.filter((r) => r.proteccion !== null);
  const unaSola = grupo === undefined && reglas.length === 1;
  const falta = motivo.trim() === '';

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
      label={unaSola ? 'Confirmar apagado de la protección' : 'Confirmar apagado del grupo'}
      role="alertdialog"
      modal
      width={380}
      alignEnd
    >
      <h2 className={styles.dialogoTitulo}>
        {unaSola ? `¿Apagar «${reglas[0].label}»?` : `¿Apagar ${grupo ?? 'las reglas'}?`}
      </h2>
      <Callout tone="crit">
        {unaSola ? (
          <>
            <b>Si la apagás:</b> {reglas[0].proteccion}
          </>
        ) : (
          <ul className={styles.consecuencias}>
            {protecciones.map((r) => (
              <li key={r.id}>
                <b>{r.label}:</b> {r.proteccion}
              </li>
            ))}
          </ul>
        )}
      </Callout>
      <div className={styles.dialogoCampo}>
        <label htmlFor={id} className={styles.dialogoEtiqueta}>
          Motivo
        </label>
        <textarea
          id={id}
          className={styles.textarea}
          rows={3}
          maxLength={MOTIVO_MAX}
          required
          aria-required
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
        <Button variant="danger" disabled={falta || ocupado} onClick={() => onConfirmar(motivo.trim())}>
          {unaSola ? 'Apagar protección' : `Apagar ${reglas.length}`}
        </Button>
      </div>
    </Popover>
  );
}
