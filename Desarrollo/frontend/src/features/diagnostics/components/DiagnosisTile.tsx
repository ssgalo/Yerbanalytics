/* Tarjeta de un diagnóstico de IA en la grilla de la vista Diagnósticos.

   La miniatura es un botón que abre la captura a pantalla completa, igual que en el detalle de
   sector. Si el diagnóstico no nació de una captura real (o la imagen no carga), cae al gradiente
   de su clase. La carga es diferida: una grilla larga no descarga todas las imágenes de golpe. */
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { DiagnosisCard, Severity } from '@/types/domain';
import { Chip, type ChipTone } from '@/components/ui/Chip';
import { Icon } from '@/components/ui/Icon';
import styles from './DiagnosisTile.module.css';

/** La severidad se pinta con la tinta del estado que implica. */
const SEVERITY_TONE: Record<Severity, ChipTone> = {
  Alta: 'critical',
  Media: 'warning',
  Baja: 'ok',
  '—': 'offline',
};

interface DiagnosisTileProps {
  d: DiagnosisCard;
  onExpand: (id: string) => void;
  /** Sin permiso para ver el sector, el código no es un enlace. */
  canOpenSector: boolean;
}

export function DiagnosisTile({ d, onExpand, canOpenSector }: DiagnosisTileProps) {
  const [brokenImage, setBrokenImage] = useState(false);
  const hasPhoto = !!d.imagenUrl && !brokenImage;
  const conf = Math.max(0, Math.min(100, d.conf));

  return (
    <article className={styles.tile}>
      <button
        type="button"
        className={styles.thumb}
        style={{ background: d.thumb }}
        onClick={() => onExpand(d.id)}
        aria-label={`Ver la captura de ${d.sectorId} a pantalla completa`}
      >
        {hasPhoto ? (
          <img
            className={styles.photo}
            src={d.imagenUrl ?? ''}
            alt=""
            loading="lazy"
            decoding="async"
            onError={() => setBrokenImage(true)}
          />
        ) : (
          <Icon
            name="leaf-simple"
            size={46}
            stroke="rgba(255,255,255,.55)"
            strokeWidth={1.3}
            style={{
              position: 'absolute',
              top: '50%',
              left: '50%',
              transform: 'translate(-50%,-50%)',
            }}
          />
        )}
        <span className={styles.idTag}>{d.id}</span>
        <span className={styles.expand} aria-hidden="true">
          Ampliar
        </span>
      </button>

      <div className={styles.body}>
        <div className={styles.head}>
          <h3 className={styles.title}>{d.estado}</h3>
          <Chip tone={SEVERITY_TONE[d.sev] ?? 'offline'}>
            {d.sev === '—' ? 'Sin severidad' : `Severidad ${d.sev.toLowerCase()}`}
          </Chip>
        </div>

        <div className={styles.where}>
          {canOpenSector ? (
            <Link className={styles.sector} to={`/sector/${encodeURIComponent(d.sectorId)}`}>
              {d.sectorId}
            </Link>
          ) : (
            <span className={styles.sector}>{d.sectorId}</span>
          )}
          <span>{d.zonaName}</span>
        </div>

        <div className={styles.conf}>
          <div className={styles.confHead}>
            <span>Confianza del modelo</span>
            <b>{conf} %</b>
          </div>
          <div className={styles.meter} role="img" aria-label={`Confianza ${conf} %`}>
            <i style={{ width: `${conf}%` }} />
          </div>
        </div>

        <div className={styles.foot}>
          {d.creadoEn != null ? (
            <time
              className={styles.time}
              dateTime={new Date(d.creadoEn).toISOString()}
              title={new Date(d.creadoEn).toLocaleString('es-AR', {
                dateStyle: 'medium',
                timeStyle: 'short',
              })}
            >
              <Icon name="clock" size={13} />
              {d.time}
            </time>
          ) : (
            <span className={styles.time}>
              <Icon name="clock" size={13} />
              {d.time}
            </span>
          )}
          {d.concluyente ? (
            <Chip tone="ok">Concluyente</Chip>
          ) : (
            d.estado !== 'No concluyente' && <Chip tone="line">Bajo el umbral</Chip>
          )}
        </div>
      </div>
    </article>
  );
}
