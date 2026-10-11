import type { CSSProperties } from 'react';
import type { DisposicionTopologia, Zona } from '@/types/domain';
import { gridColumns } from '../dashboardSummary';
import { ZoneTray } from './ZoneTray';
import styles from './NurseryMap.module.css';

/** Tamaño máximo de un punto (sector): con pocos sectores no crece más que esto. */
const CELL_MAX_PX = 34;
const CELL_GAP_PX = 4;
/** Relleno horizontal de la bandeja (12 px por lado). */
const TRAY_PADDING_PX = 24;
/** Ancho mínimo de la bandeja: lo que ocupa su encabezado (código, ubicación y nodo). */
const TRAY_MIN_PX = 280;

interface NurseryMapProps {
  zonas: Zona[];
  layout: DisposicionTopologia;
}

/**
 * Plano del vivero, en una card: cada macro-zona es una bandeja con sus sectores, en su disposición física
 * (macro-zonas por fila y sectores por fila de la topología configurada). Sin leyenda propia:
 * los estados ya se nombran en el resumen y al pie de cada bandeja.
 */
export function NurseryMap({ zonas, layout }: NurseryMapProps) {
  const columns = Math.max(1, Math.min(layout.macroZonasPorFila, zonas.length));
  /* Con pocos sectores, la bandeja se achica a su contenido en vez de agrandar los puntos. */
  const widestRow = Math.max(1, ...zonas.map((z) => gridColumns(z.sectors.length, layout.sectoresPorFila)));
  const trayMax = Math.max(TRAY_MIN_PX, widestRow * (CELL_MAX_PX + CELL_GAP_PX) - CELL_GAP_PX + TRAY_PADDING_PX);
  const sizes = {
    '--columns': columns,
    '--cell-max': `${CELL_MAX_PX}px`,
    '--cell-gap': `${CELL_GAP_PX}px`,
    '--tray-max': `${trayMax}px`,
  } as CSSProperties;

  return (
    <section className={styles.map} aria-labelledby="titulo-plano">
      <h2 className={styles.title} id="titulo-plano">
        Plano del vivero
      </h2>

      <div className={styles.trays} style={sizes}>
        {zonas.map((zona) => (
          <ZoneTray key={zona.id} zona={zona} sectorsPerRow={layout.sectoresPorFila} />
        ))}
      </div>
    </section>
  );
}
