/* Encabezado de una columna, como en una planilla: un desplegable con el filtro propio de la
   columna (casillas o rango, ver ColumnFilters) y, a su derecha, un botón chico de orden que
   rota con cada clic entre descendente, ascendente y sin orden. */
import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Icon } from '@/components/ui/Icon';
import { cx } from '@/components/ui/estado';
import type { SortDir } from '../diagnosisQuery';
import styles from './ColumnMenu.module.css';

/** Cada clic avanza: sin orden, descendente, ascendente y de vuelta a sin orden. */
const NEXT_DIR: Record<SortDir | 'none', SortDir | 'none'> = {
  none: 'desc',
  desc: 'asc',
  asc: 'none',
};

interface ColumnMenuProps {
  label: string;
  /** Cómo se lee cada sentido en esta columna ("De A a Z", "Más recientes primero"). */
  sortLabels: Record<SortDir, string>;
  /** Sentido si la grilla está ordenada por esta columna; `null` si no. */
  sortDir: SortDir | null;
  /** `null` = sin orden por esta columna. */
  onSort: (dir: SortDir | null) => void;
  /** La columna tiene un filtro aplicado. */
  filtered: boolean;
  /** Cuerpo del filtro; recibe con qué cerrar el menú. Se monta al abrir: arranca de lo aplicado. */
  children: (close: () => void) => ReactNode;
  /** Abre el menú hacia la izquierda (columnas del borde derecho). */
  alignEnd?: boolean;
}

export function ColumnMenu({
  label,
  sortLabels,
  sortDir,
  onSort,
  filtered,
  children,
  alignEnd,
}: ColumnMenuProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const panelId = useId();

  function close(returnFocus = true) {
    setOpen(false);
    if (returnFocus) triggerRef.current?.focus();
  }

  useEffect(() => {
    if (!open) return;
    panelRef.current?.querySelector<HTMLElement>('button, input')?.focus();
    function onPointer(e: MouseEvent) {
      if (!rootRef.current?.contains(e.target as Node)) close(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') close();
    }
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const nextDir = NEXT_DIR[sortDir ?? 'none'];
  const sortState = sortDir ? sortLabels[sortDir].toLowerCase() : 'sin orden';

  return (
    <div className={styles.root} ref={rootRef}>
      <button
        ref={triggerRef}
        type="button"
        className={cx(styles.trigger, filtered && styles.active)}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-label={
          filtered
            ? `Filtrar por ${label.toLowerCase()} (filtrada)`
            : `Filtrar por ${label.toLowerCase()}`
        }
        onClick={() => (open ? close() : setOpen(true))}
      >
        <span className={styles.label}>{label}</span>
        {filtered && <Icon name="filter" size={14} />}
        <Icon name="chevron-down" size={14} style={{ marginLeft: 'auto' }} />
      </button>

      <button
        type="button"
        className={cx(styles.sort, sortDir && styles.sorted)}
        onClick={() => onSort(nextDir === 'none' ? null : nextDir)}
        aria-label={`Ordenar por ${label.toLowerCase()}: ${sortState}`}
        title={`Orden: ${sortState}. Clic: ${nextDir === 'none' ? 'sin orden' : sortLabels[nextDir].toLowerCase()}`}
      >
        <Icon
          name={sortDir === 'asc' ? 'sort-asc' : sortDir === 'desc' ? 'sort-desc' : 'sort'}
          size={16}
        />
      </button>

      {open && (
        <div
          ref={panelRef}
          id={panelId}
          className={cx(styles.panel, alignEnd && styles.end)}
          role="dialog"
          aria-label={`Filtrar por ${label.toLowerCase()}`}
        >
          {children(() => close())}
        </div>
      )}
    </div>
  );
}
