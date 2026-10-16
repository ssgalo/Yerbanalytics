/* Orden de criticidad de los estados anómalos: desempata dos estados con la misma relevancia
   (y las cajas superpuestas de igual confianza). Se reordena con botones "Subir" y "Bajar",
   accesibles por teclado; sin arrastrar y soltar. */
import { useEffect, useRef, useState } from 'react';
import { Icon } from '@/components/ui/Icon';
import { cx } from '@/components/ui/estado';
import styles from './DiagnosisCriteria.module.css';

interface CriticalityOrderProps {
  /** El primero es el más crítico. */
  value: string[];
  /** Orden guardado: marca los estados que cambiaron de lugar. */
  saved?: string[];
  disabled?: boolean;
  onMove: (state: string, delta: -1 | 1) => void;
}

type Direction = 'up' | 'down';

export function CriticalityOrder({ value, saved, disabled, onMove }: CriticalityOrderProps) {
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const [announcement, setAnnouncement] = useState('');
  /** Botón que conserva el foco después de mover (si llegó al extremo, el otro). */
  const pendingFocus = useRef<{ state: string; dir: Direction } | null>(null);

  useEffect(() => {
    const p = pendingFocus.current;
    if (!p) return;
    pendingFocus.current = null;
    const i = value.indexOf(p.state);
    const atEdge = p.dir === 'up' ? i === 0 : i === value.length - 1;
    const dir: Direction = atEdge ? (p.dir === 'up' ? 'down' : 'up') : p.dir;
    buttons.current.get(`${p.state}:${dir}`)?.focus();
  }, [value]);

  const move = (state: string, dir: Direction) => {
    const i = value.indexOf(state);
    const j = dir === 'up' ? i - 1 : i + 1;
    if (j < 0 || j >= value.length) return;
    pendingFocus.current = { state, dir };
    setAnnouncement(`${state} queda en el puesto ${j + 1} de ${value.length}.`);
    onMove(state, dir === 'up' ? -1 : 1);
  };

  const ref = (state: string, dir: Direction) => (el: HTMLButtonElement | null) => {
    if (el) buttons.current.set(`${state}:${dir}`, el);
    else buttons.current.delete(`${state}:${dir}`);
  };

  return (
    <>
      <ol className={styles.order} aria-label="Orden de criticidad, del más crítico al menos crítico">
        {value.map((state, i) => {
          const moved = saved !== undefined && saved[i] !== state;
          return (
            <li key={state} className={cx(styles.orderItem, moved && styles.orderMoved)}>
              <span className={styles.rank} aria-hidden="true">
                {i + 1}
              </span>
              <span className={styles.orderName}>
                {state}
                {i === 0 && <span className={styles.orderTag}>más crítico</span>}
                {i === value.length - 1 && <span className={styles.orderTag}>menos crítico</span>}
              </span>
              <span className={styles.orderActions}>
                <button
                  ref={ref(state, 'up')}
                  type="button"
                  className={styles.orderBtn}
                  aria-label={`Subir ${state}`}
                  title="Subir (más crítico)"
                  disabled={disabled || i === 0}
                  onClick={() => move(state, 'up')}
                >
                  <Icon name="chevron-up" size={16} />
                </button>
                <button
                  ref={ref(state, 'down')}
                  type="button"
                  className={styles.orderBtn}
                  aria-label={`Bajar ${state}`}
                  title="Bajar (menos crítico)"
                  disabled={disabled || i === value.length - 1}
                  onClick={() => move(state, 'down')}
                >
                  <Icon name="chevron-down" size={16} />
                </button>
              </span>
            </li>
          );
        })}
      </ol>
      <p className="sr" aria-live="polite">
        {announcement}
      </p>
    </>
  );
}
