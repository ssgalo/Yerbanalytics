/* Foto de una captura que muestra sólo su región analizada (`crop`), cubriendo el contenedor.

   Sin recorte, la foto entera cubre el contenedor. Con recorte, el marco toma la relación de
   aspecto de la región y crece hasta cubrir; la imagen se escala y desplaza por porcentajes. El
   contenedor sólo tiene que ser `position: relative`: la foto se mide contra su propia capa, que
   cubre el borde exterior del contenedor (así su padding no deja franjas a los costados). Hasta conocer el tamaño natural la foto no se muestra: se recortaría de golpe. */
import { useState, type CSSProperties } from 'react';
import type { DiagnosisCard } from '@/types/domain';
import styles from './CroppedPhoto.module.css';

type Crop = NonNullable<DiagnosisCard['crop']>;

function coverRegion(crop: Crop, natural: { width: number; height: number }) {
  const [x1, y1, x2, y2] = crop;
  const w = x2 - x1;
  const h = y2 - y1;
  const ratio = (w * natural.width) / (h * natural.height);
  const frame: CSSProperties = {
    aspectRatio: ratio,
    width: `max(100cqw, calc(100cqh * ${ratio}))`,
  };
  const img: CSSProperties = {
    width: `${100 / w}%`,
    height: `${100 / h}%`,
    left: `${(-x1 / w) * 100}%`,
    top: `${(-y1 / h) * 100}%`,
  };
  return { frame, img };
}

interface CroppedPhotoProps {
  src: string;
  crop?: Crop | null;
  /** Clase extra del marco (p. ej. el zoom al pasar el mouse). */
  className?: string;
  loading?: 'lazy' | 'eager';
  onError?: () => void;
}

export function CroppedPhoto({ src, crop, className, loading, onError }: CroppedPhotoProps) {
  const [natural, setNatural] = useState<{ width: number; height: number } | null>(null);
  const region = crop && natural ? coverRegion(crop, natural) : null;

  return (
    <span className={styles.layer}>
      <span className={`${styles.region} ${className ?? ''}`} style={region?.frame}>
        <img
          className={region ? styles.regionPhoto : styles.photo}
          style={region ? region.img : crop ? { visibility: 'hidden' } : undefined}
          src={src}
          alt=""
          loading={loading}
          decoding="async"
          onLoad={(e) =>
            setNatural({
              width: e.currentTarget.naturalWidth,
              height: e.currentTarget.naturalHeight,
            })
          }
          onError={onError}
        />
      </span>
    </span>
  );
}
