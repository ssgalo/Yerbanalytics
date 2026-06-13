/* Modal de visualización de foto cenital de un diagnóstico */
import type { MouseEvent } from 'react';
import type { DiagnosisCard } from '@/types/domain';
import { Badge } from '@/components/ui/Badge';
import { Icon } from '@/components/ui/Icon';
import styles from './PhotoModal.module.css';

interface PhotoModalProps {
  photo: DiagnosisCard;
  onClose: () => void;
}

export function PhotoModal({ photo, onClose }: PhotoModalProps) {
  /* Evita que el click dentro de la tarjeta cierre el modal */
  function handleCardClick(e: MouseEvent<HTMLDivElement>) {
    e.stopPropagation();
  }

  return (
    /* Overlay: click cierra */
    <div className={styles.overlay} onClick={onClose}>
      {/* Tarjeta: click NO cierra */}
      <div className={styles.modal} onClick={handleCardClick}>
        {/* Cabecera con imagen */}
        <div className={styles.header} style={{ background: photo.thumb }}>
          {/* Hoja grande centrada */}
          <Icon
            name="leaf-simple"
            size={90}
            stroke="rgba(255,255,255,.5)"
            strokeWidth={1.2}
            style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)' }}
          />

          {/* Botón cerrar */}
          <button className={styles.closeBtn} onClick={onClose} aria-label="Cerrar modal">
            <Icon name="close" size={18} stroke="currentColor" strokeWidth={2.2} />
          </button>

          {/* Chip de descripción */}
          <span className={styles.chip}>Imagen cenital original sin procesar</span>
        </div>

        {/* Cuerpo */}
        <div className={styles.body}>
          {/* Título + badge de severidad */}
          <div className={styles.titleRow}>
            <h3 className={styles.title}>{photo.estado}</h3>
            <Badge soft={photo.sevSoft} ink={photo.sevInk} style={{ fontSize: '12px', padding: '3px 11px' }}>
              Severidad {photo.sev}
            </Badge>
          </div>

          {/* Grid: Sector · Macro-zona · Confianza */}
          <div className={styles.grid}>
            <div>
              <div className={styles.gridLabel}>Sector</div>
              <div className={styles.gridValue}>{photo.sectorId}</div>
            </div>
            <div>
              <div className={styles.gridLabel}>Macro-zona</div>
              <div className={styles.gridValue}>{photo.zonaName}</div>
            </div>
            <div>
              <div className={styles.gridLabel}>Confianza</div>
              <div className={styles.gridValue} style={{ color: 'var(--brand)' }}>
                {photo.conf}%
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
