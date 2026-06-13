/* ============================================================
   SectorGrid — grilla 10×10 de sectores de una macro-zona.
   Cada celda es un botón coloreado por estado de salud.
   Hover: escala 1.12 + sombra (via CSS Module).
   ============================================================ */
import type { Sector, Zona } from '@/types/domain';
import styles from './SectorGrid.module.css';

interface SectorGridProps {
  zona: Zona;
  onSectorClick: (sectorId: string) => void;
}

export function SectorGrid({ zona, onSectorClick }: SectorGridProps) {
  return (
    <div className={styles.wrapper}>
      {/* Encabezado: nombre de zona + leyenda */}
      <div className={styles.header}>
        <div>
          <h2 className={styles.title}>{zona.name}</h2>
          <div className={styles.subtitle}>
            100 sectores · 1 microaspersor por sector · clic para ver el detalle
          </div>
        </div>
        {/* Leyenda de colores */}
        <div className={styles.legend}>
          <LegendItem color="var(--ok)" label="Saludable" />
          <LegendItem color="var(--warn)" label="Observación" />
          <LegendItem color="var(--crit)" label="Crítico" />
          <LegendItem color="var(--off)" label="Sin señal" />
        </div>
      </div>

      {/* Grilla 10×10 */}
      <div className={styles.grid}>
        {zona.sectors.map((s: Sector) => (
          <button
            key={s.id}
            title={s.tip}
            className={styles.cell}
            style={{ background: s.color }}
            onClick={() => onSectorClick(s.id)}
          >
            {s.n}
          </button>
        ))}
      </div>
    </div>
  );
}

/* Ítem de la leyenda de colores */
function LegendItem({ color, label }: { color: string; label: string }) {
  return (
    <span className={styles.legendItem}>
      <span className={styles.legendDot} style={{ background: color }} />
      {label}
    </span>
  );
}
