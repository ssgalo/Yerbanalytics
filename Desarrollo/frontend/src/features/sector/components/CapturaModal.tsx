/* ============================================================
   Captura cenital a tamaño completo. Se abre desde la miniatura del
   diagnóstico de IA.

   Hoy la "imagen" es el gradiente que representa el estado detectado
   —el modelo todavía no entrega capturas reales—, así que el modal
   muestra ese mismo bloque en grande. Cuando lleguen las fotos, sólo
   cambia lo que se pinta adentro.
   ============================================================ */
import { useEffect, useRef } from 'react';
import { Icon } from '@/components/ui/Icon';
import styles from './CapturaModal.module.css';

interface CapturaModalProps {
  /** Fondo de la captura (gradiente por estado de diagnóstico). */
  thumb: string;
  estado: string;
  ago: string;
  sectorId: string;
  onClose: () => void;
}

export function CapturaModal({ thumb, estado, ago, sectorId, onClose }: CapturaModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    closeRef.current?.focus();

    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className={styles.backdrop}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={`Captura cenital del sector ${sectorId}`}
    >
      {/* Frena la propagación: un clic sobre la imagen no debe cerrar */}
      <div className={styles.dialog} onClick={(e) => e.stopPropagation()}>
        <div className={styles.head}>
          <div className={styles.meta}>
            <span className={styles.sectorId}>{sectorId}</span>
            <span className={styles.sub}>
              {estado} · imagen cenital {ago}
            </span>
          </div>
          <button ref={closeRef} className={styles.close} onClick={onClose} aria-label="Cerrar">
            <Icon name="close" size={18} />
          </button>
        </div>

        <div className={styles.imagen} style={{ background: thumb }}>
          <Icon name="leaf-simple" size={220} stroke="rgba(255,255,255,.5)" strokeWidth={1} />
        </div>
      </div>
    </div>
  );
}
