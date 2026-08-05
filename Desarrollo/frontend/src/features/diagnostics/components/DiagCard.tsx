/* Tarjeta individual de un diagnóstico de IA.

   Cuando el diagnóstico nació de una captura real muestra su fotografía; si no, cae al
   gradiente de siempre. La carga es diferida: una grilla larga no debe disparar la descarga
   de todas las imágenes de golpe. */
import { useState } from 'react';
import type { DiagnosisCard } from '@/types/domain';
import { Badge } from '@/components/ui/Badge';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { Icon } from '@/components/ui/Icon';
import styles from './DiagCard.module.css';

interface DiagCardProps {
  d: DiagnosisCard;
  onClick: (id: string) => void;
}

export function DiagCard({ d, onClick }: DiagCardProps) {
  // Si la imagen no se puede recuperar se vuelve al gradiente, en vez de mostrar el ícono
  // roto del navegador.
  const [imagenRota, setImagenRota] = useState(false);
  const conFoto = !!d.imagenUrl && !imagenRota;

  return (
    <button className={styles.card} onClick={() => onClick(d.id)}>
      {/* Miniatura: fotografía real si la hay, gradiente de respaldo si no */}
      <div className={styles.thumb} style={{ background: d.thumb }}>
        {conFoto ? (
          <img
            className={styles.thumbImg}
            src={d.imagenUrl ?? ''}
            alt={`Captura cenital del sector ${d.sectorId}`}
            loading="lazy"
            decoding="async"
            onError={() => setImagenRota(true)}
          />
        ) : (
          <Icon
            name="leaf-simple"
            size={46}
            stroke="rgba(255,255,255,.5)"
            strokeWidth={1.3}
            style={{ position: 'absolute', top: '50%', left: '50%', transform: 'translate(-50%,-50%)' }}
          />
        )}
        {/* Chip ID */}
        <span className={styles.thumbChip}>{d.id}</span>
      </div>

      {/* Cuerpo */}
      <div className={styles.body}>
        {/* Estado + badge severidad */}
        <div className={styles.headerRow}>
          <span className={styles.estado}>{d.estado}</span>
          <Badge soft={d.sevSoft} ink={d.sevInk} style={{ fontSize: '10.5px', padding: '2px 9px', flexShrink: 0 }}>
            {d.sev}
          </Badge>
        </div>

        {/* Sector · Zona */}
        <div className={styles.sector}>
          {d.sectorId} · {d.zonaName}
        </div>

        {/* Barra de confianza */}
        <div className={styles.confBlock}>
          <div className={styles.confRow}>
            <span>Confianza</span>
            <span className={styles.confVal}>{d.conf}%</span>
          </div>
          <ProgressBar value={d.conf} color="var(--brand)" height={6} />
        </div>

        {/* Timestamp */}
        <div className={styles.time}>{d.time}</div>
      </div>
    </button>
  );
}
