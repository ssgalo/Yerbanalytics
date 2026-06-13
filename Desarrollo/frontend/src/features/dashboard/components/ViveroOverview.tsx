import { useNavigate } from 'react-router-dom';
import type { Zona } from '@/types/domain';
import styles from './ViveroOverview.module.css';

interface ViveroOverviewProps {
  zonas: Zona[];
}

/** Mapa de estado del vivero: 6 macro-zonas con heatmap de 100 sectores c/u. */
export function ViveroOverview({ zonas }: ViveroOverviewProps) {
  const navigate = useNavigate();

  return (
    <div className={styles.card}>
      {/* Encabezado + leyenda */}
      <div className={styles.header}>
        <div>
          <h2 className={styles.title}>Estado del vivero</h2>
          <div className={styles.subtitle}>
            600 sectores · 6 macro-zonas · cada celda es 1 sector (~100 tubetes)
          </div>
        </div>
        <div className={styles.legend}>
          <span className={styles.legendItem}>
            <span className={styles.legendSwatch} style={{ background: 'var(--ok)' }} />
            Saludable
          </span>
          <span className={styles.legendItem}>
            <span className={styles.legendSwatch} style={{ background: 'var(--warn)' }} />
            Observación
          </span>
          <span className={styles.legendItem}>
            <span className={styles.legendSwatch} style={{ background: 'var(--crit)' }} />
            Crítico
          </span>
          <span className={styles.legendItem}>
            <span className={styles.legendSwatch} style={{ background: 'var(--off)' }} />
            Sin señal
          </span>
        </div>
      </div>

      {/* Grilla de macro-zonas */}
      <div className={styles.zonaGrid}>
        {zonas.map((zona) => (
          <button
            key={zona.id}
            className={styles.zonaBtn}
            onClick={() => navigate('/mapa?zona=' + zona.id)}
            type="button"
          >
            <div className={styles.zonaHeader}>
              <span className={styles.zonaName}>{zona.name}</span>
              <span className={styles.zonaSub}>{zona.sub}</span>
            </div>
            <div className={styles.zonaStats}>
              <span style={{ color: 'var(--ok)' }}>{zona.sano} ok</span>
              <span style={{ color: 'var(--warn)' }}>{zona.alerta} alerta</span>
              <span style={{ color: 'var(--off)' }}>{zona.off} s/s</span>
            </div>
            {/* Mini-heatmap: 100 sectores en grilla 10x10 */}
            <div className={styles.heatmap}>
              {zona.sectors.map((s) => (
                <span
                  key={s.id}
                  className={styles.cell}
                  style={{ background: s.color }}
                  title={s.tip}
                  onClick={(e) => {
                    e.stopPropagation();
                    navigate('/sector/' + s.id);
                  }}
                />
              ))}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}
