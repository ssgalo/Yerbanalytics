/* ============================================================
   SectorGrid — grilla de sectores de una macro-zona, con tantos
   sectores por fila como indique la disposición configurada.
   Cada celda es un botón coloreado por estado de salud.

   El tamaño de celda lo calcula MapPage (necesita el ancho resultante
   para dimensionar la columna) y acá sólo se dibuja. La tarjeta calza
   exactamente con la grilla: sin franjas vacías a los costados.

   No lleva encabezado ni leyenda aparte: el resumen de zona que va
   arriba de la grilla cumple ese rol, con el color de cada estado.
   ============================================================ */
import type { Sector, Zona } from '@/types/domain';
import { GRID_GAP, GRID_PADDING } from '../gridLayout';
import { ZoneSummary } from './ZoneSummary';
import styles from './SectorGrid.module.css';

/** Por debajo de este tamaño el número no se lee: se omite. */
const MIN_CELL_CON_NUMERO = 20;

interface SectorGridProps {
  zona: Zona;
  sectoresPorFila: number;
  /** Lado de celda ya calculado por MapPage. */
  cell: number;
  onSectorClick: (sectorId: string) => void;
}

export function SectorGrid({ zona, sectoresPorFila, cell, onSectorClick }: SectorGridProps) {
  const cols = Math.max(1, sectoresPorFila);
  if (cell <= 0) return null;

  return (
    <div className={styles.wrapper} style={{ padding: GRID_PADDING }}>
      {/* Conteo por estado: resume la zona y hace de leyenda de los colores de abajo */}
      <div className={styles.resumen}>
        <ZoneSummary zona={zona} />
      </div>

      <div
        className={styles.grid}
        style={{ gridTemplateColumns: `repeat(${cols}, ${cell}px)`, gap: GRID_GAP }}
      >
        {zona.sectors.map((s: Sector) => (
          <button
            key={s.id}
            title={s.tip}
            className={styles.cell}
            style={{
              background: s.color,
              width: cell,
              height: cell,
              fontSize: Math.max(7, Math.round(cell * 0.25)),
              borderRadius: Math.max(3, Math.round(cell * 0.16)),
            }}
            onClick={() => onSectorClick(s.id)}
          >
            {cell >= MIN_CELL_CON_NUMERO ? s.n : ''}
          </button>
        ))}
      </div>
    </div>
  );
}
