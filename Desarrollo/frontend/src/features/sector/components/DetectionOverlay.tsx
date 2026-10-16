/* ============================================================
   Cajas del diagnóstico sobre la fotografía.

   El SVG usa `viewBox="0 0 1 1"` con `preserveAspectRatio="none"` y ocupa exactamente el
   tamaño de la imagen renderizada (su contenedor lo da la imagen): así las coordenadas
   normalizadas `xyxyn` de cada detección caen sobre la hoja sin importar la resolución ni el
   tamaño de la pantalla. `vector-effect: non-scaling-stroke` mantiene el trazo en píxeles aunque
   el SVG se estire distinto en cada eje.

   - Diagnóstico: sólo las cajas del estado diagnosticado, trazo continuo grueso.
   - Todas: cada clase con su color de detección (`detectionView.ts`); el estado diagnosticado con
     trazo continuo grueso y el resto discontinuo fino (forma además de color).
   Esos colores son tinta para superponer a una fotografía y nada más (ver /sistema-diseno).
   ============================================================ */
import type { Detection } from '@/types/domain';
import { countByLabel, detectionColor, visibleDetections, type BoxView } from './detectionView';
import styles from './DetectionOverlay.module.css';

interface DetectionOverlayProps {
  detections: Detection[];
  diagnosedState: string;
  view: BoxView;
}

export function DetectionOverlay({ detections, diagnosedState, view }: DetectionOverlayProps) {
  const visible = visibleDetections(detections, diagnosedState, view);
  return (
    <svg
      className={styles.overlay}
      viewBox="0 0 1 1"
      preserveAspectRatio="none"
      aria-hidden="true"
      data-testid="detection-overlay"
    >
      {visible.map((d, i) => {
        const [x1, y1, x2, y2] = d.box;
        const main = d.label === diagnosedState;
        const rect = { x: x1, y: y1, width: Math.max(0, x2 - x1), height: Math.max(0, y2 - y1) };
        return (
          <g key={i} data-label={d.label} data-main={main || undefined}>
            {/* Halo oscuro debajo: la caja se lee sobre hojas claras y oscuras */}
            <rect {...rect} className={main ? styles.haloMain : styles.halo} />
            <rect {...rect} className={main ? styles.boxMain : styles.box} style={{ stroke: detectionColor(d.label) }} />
          </g>
        );
      })}
    </svg>
  );
}

interface DetectionLegendProps {
  detections: Detection[];
  diagnosedState: string;
  view: Exclude<BoxView, 'original'>;
}

/** Clase y estilo de trazo de las cajas que se ven en la foto. */
export function DetectionLegend({ detections, diagnosedState, view }: DetectionLegendProps) {
  const rows = countByLabel(visibleDetections(detections, diagnosedState, view));
  if (rows.length === 0) {
    return (
      <p className={styles.empty}>
        {diagnosedState === 'No concluyente'
          ? 'Un diagnóstico No concluyente no tiene cajas propias: elegí «Todas» para ver lo que detectó el modelo.'
          : `Ninguna caja de ${diagnosedState}.`}
      </p>
    );
  }
  return (
    <ul className={styles.legend} aria-label="Cajas sobre la foto">
      {rows.map(({ label }) => {
        const main = label === diagnosedState;
        return (
          <li key={label}>
            <svg className={styles.swatch} viewBox="0 0 24 12" aria-hidden="true">
              <rect x="2" y="2" width="20" height="8" rx="1" className={main ? styles.haloMain : styles.halo} />
              <rect
                x="2"
                y="2"
                width="20"
                height="8"
                rx="1"
                className={main ? styles.boxMain : styles.box}
                style={{ stroke: detectionColor(label) }}
              />
            </svg>
            <span className={main ? styles.labelMain : undefined}>{label}</span>
            {main && <span className={styles.count}>diagnosticado</span>}
          </li>
        );
      })}
    </ul>
  );
}
