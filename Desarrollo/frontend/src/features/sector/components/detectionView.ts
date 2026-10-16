/* Qué cajas muestra cada vista del modal y con qué color. Funciones puras: el componente
   (`DetectionOverlay`) sólo las dibuja. */
import type { Detection } from '@/types/domain';

export type BoxView = 'original' | 'diagnosis' | 'all';

/** Color de la caja de cada estado; uno desconocido (etiquetas viejas) va en blanco. */
const DETECTION_COLOR: Record<string, string> = {
  Sano: 'var(--det-sano)',
  Clorosis: 'var(--det-clorosis)',
  'Estrés solar': 'var(--det-estres-solar)',
  'Plaga foliar': 'var(--det-plaga-foliar)',
  'Daño fúngico': 'var(--det-dano-fungico)',
};

export const detectionColor = (label: string) => DETECTION_COLOR[label] ?? 'var(--on-tray)';

/** Orden de la leyenda: el de las clases del modelo; cualquier otra, al final. */
const LEGEND_ORDER = ['Sano', 'Clorosis', 'Estrés solar', 'Plaga foliar', 'Daño fúngico'];

/** Las cajas que muestra cada vista. "Original" no muestra ninguna. */
export function visibleDetections(detections: Detection[], diagnosedState: string, view: BoxView): Detection[] {
  if (view === 'original') return [];
  if (view === 'diagnosis') return detections.filter((d) => d.label === diagnosedState);
  // Las del estado diagnosticado van al final: se dibujan arriba de las demás.
  return [
    ...detections.filter((d) => d.label !== diagnosedState),
    ...detections.filter((d) => d.label === diagnosedState),
  ];
}

/** Cantidad de cajas por clase, en el orden de la leyenda. */
export function countByLabel(detections: Detection[]): { label: string; count: number }[] {
  const counts = new Map<string, number>();
  for (const d of detections) counts.set(d.label, (counts.get(d.label) ?? 0) + 1);
  const rank = (l: string) => (LEGEND_ORDER.includes(l) ? LEGEND_ORDER.indexOf(l) : LEGEND_ORDER.length);
  return [...counts]
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => rank(a.label) - rank(b.label) || a.label.localeCompare(b.label, 'es'));
}
