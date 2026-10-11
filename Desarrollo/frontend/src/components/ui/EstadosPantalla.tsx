import type { CSSProperties, ReactNode } from 'react';
import { Icon } from './Icon';
import styles from './EstadosPantalla.module.css';

interface SaveBarProps {
  /** Cantidad de cambios sin guardar. Con 0 la barra no se muestra. */
  count: number;
  /** Resumen de lo que cambió ("Umbral de riego 45 → 55 %, lo usan 4 reglas"). */
  detalle?: ReactNode;
  onDiscard: () => void;
  onSave: () => void;
  /** Texto del botón de guardar: se recomienda que lleve a una confirmación con motivo. */
  saveLabel?: string;
  saving?: boolean;
}

/** Barra de guardado común: la misma en toda vista que edita. */
export function SaveBar({ count, detalle, onDiscard, onSave, saveLabel = 'Revisar y guardar', saving }: SaveBarProps) {
  if (count <= 0) return null;
  return (
    <div className={styles.savebar} role="region" aria-label="Cambios sin guardar">
      <Icon name="config" size={18} />
      <span>
        <b>
          {count} {count === 1 ? 'cambio' : 'cambios'} sin guardar.
        </b>{' '}
        {detalle && <span className={styles.saveDetalle}>{detalle}</span>}
      </span>
      <span className={styles.spacer} />
      <button type="button" className={styles.saveGhost} onClick={onDiscard} disabled={saving}>
        Descartar
      </button>
      <button type="button" className={styles.saveLight} onClick={onSave} disabled={saving}>
        {saving ? 'Guardando…' : saveLabel}
      </button>
    </div>
  );
}

/** Bloque de carga. Se usa con el shell visible: nunca reemplaza la app entera. */
export function Skeleton({ width = '100%', height = 14, style }: { width?: number | string; height?: number | string; style?: CSSProperties }) {
  return <div className={styles.skel} style={{ width, height, ...style }} aria-hidden="true" />;
}

interface EmptyStateProps {
  title: string;
  children?: ReactNode;
  /** La acción que resuelve el vacío ("Registrar el primero"). */
  action?: ReactNode;
}

/** Vacío con acción: dice por qué no hay nada y qué hacer. */
export function EmptyState({ title, children, action }: EmptyStateProps) {
  return (
    <div className={styles.empty}>
      <p className={styles.emptyTitulo}>{title}</p>
      {children && <p className={styles.emptyTexto}>{children}</p>}
      {action}
    </div>
  );
}

/** Banner de sin conexión. Mientras se muestra, las acciones manuales se deshabilitan. */
export function OfflineBanner({ children }: { children: ReactNode }) {
  return (
    <div className={styles.offline} role="status">
      <Icon name="wifi-off" size={18} />
      <span>{children}</span>
    </div>
  );
}
