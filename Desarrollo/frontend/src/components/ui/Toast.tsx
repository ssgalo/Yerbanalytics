import { useEffect, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from './Icon';
import { cx } from './estado';
import styles from './Toast.module.css';

/** Cuánto queda a la vista un resultado exitoso. Un error queda hasta que se cierra. */
const DURACION_OK_MS = 6000;

interface ToastProps {
  tone: 'ok' | 'crit';
  children: ReactNode;
  onClose: () => void;
}

/**
 * Resultado de una acción ("quedó suspendido", "no se pudo guardar"): flota en la esquina inferior
 * derecha, encima del contenido, así no corre nada de lugar. El éxito se va solo; el error, no.
 */
export function Toast({ tone, children, onClose }: ToastProps) {
  // El plazo corre de nuevo con cada aviso nuevo, no con cada render de quien lo muestra.
  const closeRef = useRef(onClose);
  useEffect(() => {
    closeRef.current = onClose;
  });
  useEffect(() => {
    if (tone !== 'ok') return;
    const t = window.setTimeout(() => closeRef.current(), DURACION_OK_MS);
    return () => window.clearTimeout(t);
  }, [tone, children]);

  return createPortal(
    <div className={cx(styles.toast, styles[tone])} role={tone === 'crit' ? 'alert' : 'status'}>
      <Icon name={tone === 'ok' ? 'check' : 'alert'} size={18} />
      <div className={styles.body}>{children}</div>
      <button type="button" className={styles.close} aria-label="Cerrar aviso" onClick={onClose}>
        <Icon name="close" size={16} />
      </button>
    </div>,
    document.body,
  );
}
