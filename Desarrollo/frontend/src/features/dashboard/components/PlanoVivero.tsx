import type { CSSProperties } from 'react';
import type { DisposicionTopologia, Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { pluralizar, resumenVivero } from '../resumenVivero';
import { ComoLeer } from './ComoLeer';
import { ZonaBlock } from './ZonaBlock';
import styles from './PlanoVivero.module.css';

interface PlanoViveroProps {
  zonas: Zona[];
  layout: DisposicionTopologia;
}

/** Leyenda de colores de las celdas de sector. */
const LEYENDA = [
  { color: 'var(--ok)', label: 'Saludable' },
  { color: 'var(--warn)', label: 'En observación' },
  { color: 'var(--crit)', label: 'Crítico' },
  { color: 'var(--off)', label: 'Sin señal' },
];

/**
 * Plano del vivero: cada macro-zona es una bandeja con sus sectores, en su disposición física
 * (macro-zonas por fila y sectores por fila de la topología configurada).
 */
export function PlanoVivero({ zonas, layout }: PlanoViveroProps) {
  const resumen = resumenVivero(zonas);
  const totalSectores = zonas.reduce((acc, z) => acc + z.total, 0);
  const columnas = Math.max(1, Math.min(layout.macroZonasPorFila, zonas.length));

  return (
    <Card className={styles.card}>
      <div className={styles.head}>
        <div>
          <h2 className={styles.title}>Plano del vivero</h2>
          <p className={styles.resumen} data-status={resumen.status}>
            <span className={styles.resumenDot} />
            {resumen.text}
          </p>
          <div className={styles.sub}>
            {pluralizar(totalSectores, 'sector', 'sectores')} ·{' '}
            {pluralizar(zonas.length, 'macro-zona', 'macro-zonas')} · cada celda es 1 sector (~100
            plantines). Tocá una celda para ver su sector.
          </div>
        </div>

        <div className={styles.legend}>
          {LEYENDA.map((l) => (
            <span key={l.label} className={styles.legendItem}>
              <span className={styles.legendSwatch} style={{ background: l.color }} />
              {l.label}
            </span>
          ))}
        </div>
      </div>

      {/* Macro-zonas por fila según la disposición; colapsa a 1 en pantallas angostas */}
      <div className={styles.grid} style={{ '--columnas': columnas } as CSSProperties}>
        {zonas.map((zona) => (
          <ZonaBlock key={zona.id} zona={zona} sectoresPorFila={layout.sectoresPorFila} />
        ))}
      </div>

      <ComoLeer />
    </Card>
  );
}
