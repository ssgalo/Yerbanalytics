/* Orden de criticidad de los estados anómalos: desempata dos estados con la misma relevancia
   (y las cajas superpuestas de igual confianza). Se reordena arrastrando cada fila (mouse o
   touch), como una pregunta de ordenar de Google Forms; los botones "Subir" y "Bajar" quedan
   como alternativa accesible por teclado. */
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent } from 'react';
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
  /** Orden completo nuevo, al arrastrar. */
  onReorder: (order: string[]) => void;
}

type Direction = 'up' | 'down';

/** Fila que se arrastra: dónde está el puntero y de qué altura de la fila la tomó. */
interface Drag {
  state: string;
  pointerId: number;
  pointerY: number;
  grabOffset: number;
}

export function CriticalityOrder({ value, saved, disabled, onMove, onReorder }: CriticalityOrderProps) {
  const buttons = useRef(new Map<string, HTMLButtonElement>());
  const items = useRef(new Map<string, HTMLLIElement>());
  const [announcement, setAnnouncement] = useState('');
  const [drag, setDrag] = useState<Drag | null>(null);
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

  // La fila arrastrada sigue al puntero: se desplaza desde su lugar en el layout (que cambia
  // cada vez que pasa por encima de otra) hasta donde está el puntero.
  useLayoutEffect(() => {
    if (!drag) return;
    const el = items.current.get(drag.state);
    if (!el) return;
    el.style.transform = '';
    const top = el.getBoundingClientRect().top;
    el.style.transform = `translateY(${drag.pointerY - drag.grabOffset - top}px)`;
  }, [drag, value]);

  const move = (state: string, dir: Direction) => {
    const i = value.indexOf(state);
    const j = dir === 'up' ? i - 1 : i + 1;
    if (j < 0 || j >= value.length) return;
    pendingFocus.current = { state, dir };
    setAnnouncement(`${state} queda en el puesto ${j + 1} de ${value.length}.`);
    onMove(state, dir === 'up' ? -1 : 1);
  };

  const startDrag = (state: string) => (e: ReactPointerEvent<HTMLLIElement>) => {
    if (disabled || e.button !== 0 || (e.target as HTMLElement).closest('button')) return;
    const el = e.currentTarget;
    el.setPointerCapture(e.pointerId);
    e.preventDefault();
    setDrag({
      state,
      pointerId: e.pointerId,
      pointerY: e.clientY,
      grabOffset: e.clientY - el.getBoundingClientRect().top,
    });
  };

  const dragMove = (e: ReactPointerEvent<HTMLLIElement>) => {
    if (!drag || e.pointerId !== drag.pointerId) return;
    const el = items.current.get(drag.state);
    if (!el) return;
    // Nuevo puesto: cuántas de las otras filas tienen su mitad por encima del centro de la arrastrada.
    const center = e.clientY - drag.grabOffset + el.offsetHeight / 2;
    const others = value.filter((s) => s !== drag.state);
    const target = others.filter((s) => {
      const r = items.current.get(s)?.getBoundingClientRect();
      return r !== undefined && r.top + r.height / 2 < center;
    }).length;
    if (target !== value.indexOf(drag.state)) {
      const next = [...others];
      next.splice(target, 0, drag.state);
      onReorder(next);
    }
    setDrag({ ...drag, pointerY: e.clientY });
  };

  const endDrag = (e: ReactPointerEvent<HTMLLIElement>) => {
    if (!drag || e.pointerId !== drag.pointerId) return;
    const el = items.current.get(drag.state);
    if (el) el.style.transform = '';
    setAnnouncement(`${drag.state} queda en el puesto ${value.indexOf(drag.state) + 1} de ${value.length}.`);
    setDrag(null);
  };

  const ref = (state: string, dir: Direction) => (el: HTMLButtonElement | null) => {
    if (el) buttons.current.set(`${state}:${dir}`, el);
    else buttons.current.delete(`${state}:${dir}`);
  };

  const itemRef = (state: string) => (el: HTMLLIElement | null) => {
    if (el) items.current.set(state, el);
    else items.current.delete(state);
  };

  return (
    <>
      <ol className={styles.order} aria-label="Orden de criticidad, del más crítico al menos crítico">
        {value.map((state, i) => {
          const moved = saved !== undefined && saved[i] !== state;
          return (
            <li
              key={state}
              ref={itemRef(state)}
              className={cx(
                styles.orderItem,
                !disabled && styles.orderDraggable,
                moved && styles.orderMoved,
                drag?.state === state && styles.orderDragging,
              )}
              onPointerDown={startDrag(state)}
              onPointerMove={dragMove}
              onPointerUp={endDrag}
              onPointerCancel={endDrag}
            >
              {!disabled && (
                <span className={styles.grip} aria-hidden="true" title="Arrastrar para reordenar">
                  <Icon name="grip" size={16} />
                </span>
              )}
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
