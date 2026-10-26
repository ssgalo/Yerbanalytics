/* Tarjeta de un diagnóstico de IA en la grilla de la vista Diagnósticos.

   La miniatura es un botón que abre la captura a pantalla completa, igual que en el detalle de
   sector. Si el diagnóstico no nació de una captura real (o la imagen no carga), cae al gradiente
   de su clase. La carga es diferida: una grilla larga no descarga todas las imágenes de golpe.
   Si el diagnóstico tiene recorte, la miniatura muestra sólo esa región (lo mismo que el visor),
   cubriendo el marco.

   Muestra el nivel de confianza (y el score promedio como dato secundario) y la severidad sólo si
   el estado es anómalo. No dice si el motor puede actuar: eso lo decide el motor. No concluyente
   no lleva confianza, severidad ni score. */
import { useState, type CSSProperties } from 'react';
import { Link } from 'react-router-dom';
import type { DiagnosisCard, Severity } from '@/types/domain';
import { Chip, type ChipTone } from '@/components/ui/Chip';
import { Icon } from '@/components/ui/Icon';
import { hasConfidence, isAnomalous } from '@/lib/diagnosis';
import { ConfidenceChip } from './ConfidenceChip';
import styles from './DiagnosisTile.module.css';

/** La severidad se pinta con la tinta del estado que implica. */
const SEVERITY_TONE: Record<Severity, ChipTone> = {
  Alta: 'critical',
  Media: 'warning',
  Baja: 'ok',
  '—': 'offline',
};

/** "MZ-6-003" → 3. Si el código no tiene ese formato, se muestra tal cual. */
function sectorNumber(sectorId: string): string {
  const last = sectorId.split('-').pop() ?? '';
  return /^\d+$/.test(last) ? String(Number(last)) : sectorId;
}

/** Hace cuánto: en minutos, horas o días según corresponda. */
function elapsed(createdAt: number): string {
  const min = Math.max(0, Math.floor((Date.now() - new Date(createdAt).getTime()) / 60000));
  if (min < 1) return 'recién';
  if (min < 60) return `hace ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `hace ${h} ${h === 1 ? 'hora' : 'horas'}`;
  const days = Math.floor(h / 24);
  return `hace ${days} ${days === 1 ? 'día' : 'días'}`;
}

/**
 * Estilos para que la región `crop` cubra la miniatura: el marco toma la relación de aspecto de la
 * región y crece hasta cubrir el contenedor; la imagen se escala y se desplaza por porcentajes.
 */
function coverRegion(
  crop: [number, number, number, number],
  natural: { width: number; height: number },
) {
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

interface DiagnosisTileProps {
  d: DiagnosisCard;
  onExpand: (id: string) => void;
  /** Sin permiso para ver el sector, el código no es un enlace. */
  canOpenSector: boolean;
}

export function DiagnosisTile({ d, onExpand, canOpenSector }: DiagnosisTileProps) {
  const [brokenImage, setBrokenImage] = useState(false);
  const [natural, setNatural] = useState<{ width: number; height: number } | null>(null);
  const hasPhoto = !!d.imagenUrl && !brokenImage;
  const region = d.crop && natural ? coverRegion(d.crop, natural) : null;
  const anomalous = isAnomalous(d.estado);
  const withConfidence = hasConfidence(d.estado);

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
          <span className={styles.region} style={region?.frame}>
            <img
              className={region ? styles.regionPhoto : styles.photo}
              // Hasta conocer el tamaño, la foto entera no se muestra: se recortaría de golpe.
              style={region ? region.img : d.crop ? { visibility: 'hidden' } : undefined}
              src={d.imagenUrl ?? ''}
              alt=""
              loading="lazy"
              decoding="async"
              onLoad={(e) =>
                setNatural({
                  width: e.currentTarget.naturalWidth,
                  height: e.currentTarget.naturalHeight,
                })
              }
              onError={() => setBrokenImage(true)}
            />
          </span>
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
        <span className={styles.expand} aria-hidden="true">
          Ampliar
        </span>
      </button>

      <div className={styles.body}>
        <h3 className={styles.title} title={d.estado}>
          {d.estado}
        </h3>

        <div className={styles.where}>
          {canOpenSector ? (
            <Link className={styles.sector} to={`/sector/${encodeURIComponent(d.sectorId)}`}>
              {d.zonaName}
            </Link>
          ) : (
            <span className={styles.sector}>{d.zonaName}</span>
          )}
          <span>Sector {sectorNumber(d.sectorId)}</span>
        </div>

        {withConfidence ? (
          <div className={styles.conf}>
            {anomalous && d.sev !== '—' && (
              <Chip tone={SEVERITY_TONE[d.sev] ?? 'offline'}>Severidad {d.sev.toLowerCase()}</Chip>
            )}
            <ConfidenceChip level={d.confidence} />
          </div>
        ) : (
          <p className={styles.inconclusive}>El modelo no llegó a un estado concluyente.</p>
        )}

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
              {elapsed(d.creadoEn)}
            </time>
          ) : (
            <span className={styles.time}>
              <Icon name="clock" size={13} />
              {d.time}
            </span>
          )}
        </div>
      </div>
    </article>
  );
}
