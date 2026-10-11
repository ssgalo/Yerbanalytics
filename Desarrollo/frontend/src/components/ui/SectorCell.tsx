import type { HTMLAttributes, ReactNode } from 'react';
import type { Status } from '@/types/domain';
import { ETIQUETA_ESTADO, TONO, cx } from './estado';
import styles from './SectorCell.module.css';

interface TrayCellProps {
  status: Status;
  selected?: boolean;
}

/** Un sector en el plano: un punto con la forma de su estado. Va dentro de una `Bandeja`. */
export function TrayCell({ status, selected }: TrayCellProps) {
  return <i className={cx(styles.tray, styles[TONO[status]], selected && styles.sel)} aria-hidden="true" />;
}

interface GridCellProps {
  status: Status;
  /** Número del sector dentro de la macro-zona. */
  numero: number;
  /** Nombre accesible completo, p. ej. "Sector MZ-4-005". */
  label: string;
  /** Diagnóstico de IA sin revisar. */
  diag?: boolean;
  selected?: boolean;
  onClick?: () => void;
}

/** Un sector en la grilla de la macro-zona: celda numerada y tocable. */
export function GridCell({ status, numero, label, diag, selected, onClick }: GridCellProps) {
  return (
    <button
      type="button"
      className={cx(styles.grid, styles[TONO[status]], diag && styles.diag, selected && styles.sel)}
      aria-label={`${label}: ${ETIQUETA_ESTADO[status]}${diag ? ', diagnóstico sin revisar' : ''}`}
      aria-pressed={selected}
      onClick={onClick}
    >
      <span className={styles.num}>{numero}</span>
    </button>
  );
}

/** Superficie oscura de la bandeja: el único oscuro del sistema. */
export function Bandeja({ children, className, ...rest }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <div className={cx(styles.bandeja, className)} {...rest}>
      {children}
    </div>
  );
}
