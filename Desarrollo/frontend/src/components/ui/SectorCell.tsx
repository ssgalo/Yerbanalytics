import type { HTMLAttributes, ReactNode } from 'react';
import type { Status } from '@/types/domain';
import { ETIQUETA_ESTADO, TONO, cx } from './estado';
import styles from './SectorCell.module.css';

interface TrayCellProps {
  status: Status;
  selected?: boolean;
}

/** Un sector en el plano: su bandeja de plantines, con las hojas del color de su estado. Va dentro de una `Bandeja`. */
export function TrayCell({ status, selected }: TrayCellProps) {
  return <i className={cx(styles.tray, styles[TONO[status]], selected && styles.sel)} aria-hidden="true" />;
}

interface GridCellProps {
  status: Status;
  /** Nombre accesible completo, p. ej. "Sector MZ-4-005". */
  label: string;
  /** Diagnóstico de IA sin revisar. */
  diag?: boolean;
  selected?: boolean;
  onClick?: () => void;
}

/** Un sector en la grilla de la macro-zona: su bandeja, tocable. El número queda en el nombre accesible. */
export function GridCell({ status, label, diag, selected, onClick }: GridCellProps) {
  return (
    <button
      type="button"
      className={cx(styles.grid, diag && styles.diag, selected && styles.sel)}
      aria-label={`${label}: ${ETIQUETA_ESTADO[status]}${diag ? ', diagnóstico sin revisar' : ''}`}
      aria-pressed={selected}
      onClick={onClick}
    >
      <i className={cx(styles.tray, styles[TONO[status]])} aria-hidden="true" />
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
