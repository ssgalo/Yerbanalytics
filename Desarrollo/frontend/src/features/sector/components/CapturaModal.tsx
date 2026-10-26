/* ============================================================
   Captura cenital a pantalla completa. Se abre desde la miniatura del
   diagnóstico de IA y se cierra con Escape, con la X o con un clic
   fuera de la imagen.

   Si el diagnóstico nació de una captura real muestra la fotografía; si
   no hay foto —o no se puede cargar—, cae al gradiente que representa el
   estado detectado.

   Región analizada: el servicio de inferencia recorta la captura (saca el
   riel y el piso) y manda el recorte con las detecciones. Por eso las
   detecciones se piden al abrir: con recorte, las tres vistas muestran
   sólo esa región del JPEG original, recortada con CSS (design D6 de
   add-tiling-inferencia); sin recorte, o si el pedido falla, la imagen
   entera. Mientras llegan, la foto no se muestra, para que no aparezca
   entera y se recorte de golpe.

   Si el diagnóstico tiene detecciones, ofrece Original · Diagnóstico ·
   Todas. Arranca en Original (la foto sin cajas). Si el pedido falla, la
   foto sigue a la vista y, al elegir una vista con cajas, un aviso
   permite reintentar.
   ============================================================ */
import { useEffect, useRef, useState, type CSSProperties, type MouseEvent } from 'react';
import { getRepository } from '@/data';
import { Icon } from '@/components/ui/Icon';
import { Segmented } from '@/components/ui/Seleccion';
import type { DiagnosisDetections } from '@/types/domain';
import { DetectionLegend, DetectionOverlay } from './DetectionOverlay';
import type { BoxView } from './detectionView';
import styles from './CapturaModal.module.css';

interface CapturaModalProps {
  /** Fondo de respaldo de la captura (gradiente por estado de diagnóstico). */
  thumb: string;
  imagenUrl?: string;
  estado: string;
  ago: string;
  sectorId: string;
  /** Diagnóstico cuyas cajas se pueden pedir; sin él, no hay selector. */
  diagnosisId?: string | null;
  /** Detecciones guardadas con el diagnóstico; con 0, no hay selector. */
  detectionCount?: number;
  onClose: () => void;
}

type BoxesState =
  | { status: 'idle' }
  | { status: 'loading' }
  | ({ status: 'ok' } & DiagnosisDetections)
  | { status: 'error' };

interface NaturalSize {
  width: number;
  height: number;
}

/**
 * Estilos del marco y de la imagen para mostrar sólo la región `crop`: el marco toma la relación de
 * aspecto de la región y cabe en el escenario; la imagen se escala y se desplaza por porcentajes.
 */
function regionLayout(crop: NonNullable<DiagnosisDetections['crop']>, natural: NaturalSize) {
  const [x1, y1, x2, y2] = crop;
  const w = x2 - x1;
  const h = y2 - y1;
  const ratio = (w * natural.width) / (h * natural.height);
  const frame: CSSProperties = { aspectRatio: ratio, width: `min(100cqw, calc(100cqh * ${ratio}))` };
  const img: CSSProperties = {
    width: `${100 / w}%`,
    height: `${100 / h}%`,
    left: `${(-x1 / w) * 100}%`,
    top: `${(-y1 / h) * 100}%`,
  };
  return { frame, img };
}

const VIEWS: { value: BoxView; label: string }[] = [
  { value: 'original', label: 'Original' },
  { value: 'diagnosis', label: 'Diagnóstico' },
  { value: 'all', label: 'Todas' },
];

/** Un clic sobre la foto o los controles no cierra el modal. */
const keepOpen = (e: MouseEvent) => e.stopPropagation();

export function CapturaModal({
  thumb,
  imagenUrl,
  estado,
  ago,
  sectorId,
  diagnosisId,
  detectionCount = 0,
  onClose,
}: CapturaModalProps) {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [imagenRota, setImagenRota] = useState(false);
  const [view, setView] = useState<BoxView>('original');
  const [boxes, setBoxes] = useState<BoxesState>({ status: 'idle' });
  const [natural, setNatural] = useState<NaturalSize | null>(null);
  const mounted = useRef(true);
  const conFoto = !!imagenUrl && !imagenRota;
  // Las cajas sólo tienen sentido sobre la fotografía
  const offersBoxes = conFoto && !!diagnosisId && detectionCount > 0;

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  // El recorte viaja con las detecciones: se piden al abrir, aunque se arranque en Original.
  const fetchesOnOpen = !!imagenUrl && !!diagnosisId;
  useEffect(() => {
    if (fetchesOnOpen) loadBoxes();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- una sola vez, al abrir
  }, []);

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

  function loadBoxes() {
    if (!diagnosisId) return;
    setBoxes({ status: 'loading' });
    getRepository()
      .getDiagnosisDetections(diagnosisId)
      .then((result) => mounted.current && setBoxes({ status: 'ok', ...result }))
      .catch(() => mounted.current && setBoxes({ status: 'error' }));
  }

  function choose(next: BoxView) {
    setView(next);
    // Se piden una sola vez, la primera vez que hacen falta (o de nuevo si fallaron).
    if (next !== 'original' && (boxes.status === 'idle' || boxes.status === 'error')) loadBoxes();
  }

  const showBoxes = offersBoxes && view !== 'original';
  const crop = boxes.status === 'ok' ? boxes.crop : null;
  const region = crop && natural ? regionLayout(crop, natural) : null;
  // Sin ocultarla, la foto aparecería entera y se recortaría de golpe al llegar el recorte.
  const pending = boxes.status === 'loading' || (!!crop && !natural);

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

      {offersBoxes && (
        <div className={styles.tools} onClick={keepOpen}>
          <Segmented label="Cajas sobre la foto" options={VIEWS} value={view} onChange={choose} />
          <div className={styles.toolsInfo} aria-live="polite">
            {showBoxes && boxes.status === 'loading' && <p className={styles.note}>Cargando las cajas…</p>}
            {showBoxes && boxes.status === 'error' && (
              <p className={styles.note} role="alert">
                <Icon name="alert" size={15} />
                No se pudieron cargar las cajas: se muestra la foto original.
                <button type="button" className={styles.retry} onClick={loadBoxes}>
                  Reintentar
                </button>
              </p>
            )}
            {showBoxes && boxes.status === 'ok' && (
              <DetectionLegend detections={boxes.detections} diagnosedState={estado} view={view} />
            )}
          </div>
        </div>
      )}

      <div className={styles.stage}>
        {conFoto ? (
          // El marco toma el tamaño exacto de lo que se ve de la foto (la imagen entera o la
          // región recortada): las cajas, en coordenadas de eso, quedan alineadas.
          <div
            className={`${styles.frame} ${region ? styles.region : ''} ${pending ? styles.pending : ''}`}
            style={region?.frame}
            onClick={keepOpen}
            data-testid="captura-frame"
          >
            <img
              className={styles.foto}
              style={region?.img}
              src={imagenUrl}
              alt={`Captura cenital del sector ${sectorId}`}
              onLoad={(e) =>
                setNatural({ width: e.currentTarget.naturalWidth, height: e.currentTarget.naturalHeight })
              }
              onError={() => setImagenRota(true)}
            />
            {showBoxes && boxes.status === 'ok' && (
              <DetectionOverlay detections={boxes.detections} diagnosedState={estado} view={view} />
            )}
          </div>
        ) : (
          <div className={styles.respaldo} style={{ background: thumb }} onClick={keepOpen}>
            <Icon name="leaf-simple" size={220} stroke="rgba(255,255,255,.5)" strokeWidth={1} />
          </div>
        )}
      </div>
    </div>
  );
}
