/* Encabezado de una columna, como en una planilla: un desplegable con el filtro propio de la
   columna (casillas o rango, ver ColumnFilters) y, a su derecha, un botón chico de orden que
   rota con cada clic entre descendente, ascendente y sin orden.

   El panel es un `Popover`: flota encima del contenido sin recortarse. Lo usan Diagnósticos y las
   tablas de Usuarios. */
import { useRef, useState, type ReactNode } from 'react';
import { Icon } from './Icon';
import { Popover } from './Popover';
import { cx } from './estado';
import type { SortDir } from '@/lib/columnQuery';
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
  /** Abre el menú hacia la izquierda (columnas del borde derecho). Si no entra, lo hace solo. */
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
  const triggerRef = useRef<HTMLButtonElement>(null);

  function close() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  const nextDir = NEXT_DIR[sortDir ?? 'none'];
  const sortState = sortDir ? sortLabels[sortDir].toLowerCase() : 'sin orden';

  return (
    <div className={styles.root}>
      <button
        ref={triggerRef}
        type="button"
        className={cx(styles.trigger, filtered && styles.active)}
        aria-expanded={open}
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
        <Popover
          anchor={triggerRef.current}
          onClose={() => setOpen(false)}
          label={`Filtrar por ${label.toLowerCase()}`}
          width={280}
          alignEnd={alignEnd}
          flush
          className={styles.panel}
        >
          {children(close)}
        </Popover>
      )}
    </div>
  );
}
