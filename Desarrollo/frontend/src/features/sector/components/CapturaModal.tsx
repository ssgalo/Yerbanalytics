/* ============================================================
   Captura cenital a pantalla completa. Se abre desde la miniatura del
   diagnóstico de IA y se cierra con Escape, con la X o con un clic
   fuera de la imagen.

   Si el diagnóstico nació de una captura real muestra la fotografía
   entera (sin recortar); si no hay foto —o no se puede cargar—, cae al
   gradiente que representa el estado detectado.
   ============================================================ */
import { useEffect, useRef, useState } from 'react';
import { Icon } from '@/components/ui/Icon';
import styles from './CapturaModal.module.css';

interface CapturaModalProps {
  /** Fondo de respaldo de la captura (gradiente por estado de diagnóstico). */
  thumb: string;
  imagenUrl?: string;
  estado: string;
  ago: string;
  sectorId: string;
  onClose: () => void;
}

export function CapturaModal({ thumb, imagenUrl, estado, ago, sectorId, onClose }: CapturaModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [imagenRota, setImagenRota] = useState(false);
  const conFoto = !!imagenUrl && !imagenRota;

  useEffect(() => {
    closeRef.current?.focus();

    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    // Sin scroll de la página de fondo mientras la captura ocupa la pantalla
    const overflowPrevio = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.style.overflow = overflowPrevio;
    };
  }, [onClose]);

  return (
    <div
      className={styles.backdrop}
      onClick={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={`Captura cenital del sector ${sectorId}`}
    >
      <div className={styles.head}>
        <div className={styles.meta}>
          <span className={styles.sectorId}>{sectorId}</span>
          <span className={styles.sub}>
            {estado} · imagen cenital {ago}
          </span>
        </div>
        <button ref={closeRef} className={styles.close} onClick={onClose} aria-label="Cerrar">
          <Icon name="close" size={20} />
        </button>
      </div>

      {/* Frena la propagación: un clic sobre la imagen no debe cerrar */}
      <div className={styles.stage}>
        {conFoto ? (
          <img
            className={styles.foto}
            src={imagenUrl}
            alt={`Captura cenital del sector ${sectorId}`}
            onClick={(e) => e.stopPropagation()}
            onError={() => setImagenRota(true)}
          />
        ) : (
          <div className={styles.respaldo} style={{ background: thumb }} onClick={(e) => e.stopPropagation()}>
            <Icon name="leaf-simple" size={220} stroke="rgba(255,255,255,.5)" strokeWidth={1} />
          </div>
        )}
      </div>
    </div>
  );
}
