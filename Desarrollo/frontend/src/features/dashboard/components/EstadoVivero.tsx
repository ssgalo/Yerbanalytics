import type { CSSProperties } from 'react';
import type { DisposicionTopologia, Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { pluralizar } from '../resumenVivero';
import { ZonaBlock, type FocoZona } from './ZonaBlock';
import styles from './EstadoVivero.module.css';

interface EstadoViveroProps {
  zonas: Zona[];
  layout: DisposicionTopologia;
  foco: FocoZona | null;
}

/** Leyenda de colores de las celdas de sector. */
const LEYENDA = [
  { color: 'var(--ok)', label: 'Saludable' },
  { color: 'var(--warn)', label: 'En observación' },
  { color: 'var(--crit)', label: 'Crítico' },
  { color: 'var(--off)', label: 'Sin señal' },
];

/** Estado del vivero: un bloque por macro-zona, según la disposición configurada. */
export function EstadoVivero({ zonas, layout, foco }: EstadoViveroProps) {
  const totalSectores = zonas.reduce((acc, z) => acc + z.total, 0);
  const columnas = Math.max(1, Math.min(layout.macroZonasPorFila, zonas.length));

  return (
    <Card className={styles.card}>
      <h2 className={styles.title}>Estado del vivero</h2>
      <div className={styles.subtitle}>
        {pluralizar(totalSectores, 'sector', 'sectores')} ·{' '}
        {pluralizar(zonas.length, 'macro-zona', 'macro-zonas')} · cada celda es 1 sector (~100
        plantines)
      </div>

      <div className={styles.legend}>
        {LEYENDA.map((l) => (
          <span key={l.label} className={styles.legendItem}>
            <span className={styles.legendSwatch} style={{ background: l.color }} />
            {l.label}
          </span>
        ))}
      </div>

      {/* Macro-zonas por fila según la disposición; colapsa a 1 en pantallas angostas */}
      <div
        className={styles.grid}
        style={{ '--columnas': columnas } as CSSProperties}
      >
        {zonas.map((zona) => (
          <ZonaBlock
            key={zona.id}
            zona={zona}
            sectoresPorFila={layout.sectoresPorFila}
            foco={foco}
          />
        ))}
      </div>
    </Card>
  );
}
