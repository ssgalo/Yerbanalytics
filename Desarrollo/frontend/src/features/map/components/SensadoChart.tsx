/* ============================================================
   Inspector de la métrica seleccionada: su histórico. Se desliza
   desde el borde derecho de la columna del mapa —o sea, desde el
   borde izquierdo del panel de sensado— y tapa la grilla, nunca los
   botones de las métricas.

   Sirve para CUALQUIERA de las 10 métricas, no sólo humedad de sustrato.
   ============================================================ */
import type { MetricTile, Range, SerieMetrica } from '@/types/domain';
import { Icon } from '@/components/ui/Icon';
import styles from './SensadoChart.module.css';

const RANGES: Range[] = ['24h', '7d', '30d'];

interface SensadoChartProps {
  /** Métrica seleccionada en el panel; aporta título, color y banda óptima. */
  tile: MetricTile;
  /** Serie de esa métrica en el rango elegido; `null` si el nodo no la reportó. */
  serie: SerieMetrica | null;
  range: Range;
  onRangeChange: (r: Range) => void;
  onClose: () => void;
}

export function SensadoChart({ tile, serie, range, onRangeChange, onClose }: SensadoChartProps) {
  return (
    <div className={styles.card}>
      <div className={styles.header}>
        <div className={styles.titleWrap}>
          <span className={styles.dot} style={{ background: tile.color }} />
          <h3 className={styles.title}>{tile.label}</h3>
          {tile.provisional && <span className={styles.provisional}>rango provisional</span>}
        </div>

        {/* Selector de rango */}
        <div className={styles.rangeSelector}>
          {RANGES.map((r) => (
            <button
              key={r}
              className={
                r === range ? `${styles.rangeBtn} ${styles.rangeBtnActive}` : styles.rangeBtn
              }
              onClick={() => onRangeChange(r)}
            >
              {r}
            </button>
          ))}
        </div>

        <button
          type="button"
          className={styles.close}
          onClick={onClose}
          aria-label="Cerrar histórico"
        >
          <Icon name="close" size={16} />
        </button>
      </div>

      <div className={styles.actual}>
        <span className={styles.actualValue} style={{ color: tile.color }}>
          {tile.value}
        </span>
        <span className={styles.actualUnit}>{tile.unit}</span>
        <span className={styles.actualLabel}>lectura actual · óptimo {tile.ideal}</span>
      </div>

      {serie ? (
        <div className={styles.body}>
          {/* SVG del gráfico — renderizado directo, no usa el átomo Sparkline */}
          <div className={styles.chartWrap}>
            <svg
              width="100%"
              height="100%"
              viewBox="0 0 660 170"
              preserveAspectRatio="none"
              className={styles.svg}
            >
              <defs>
                <linearGradient id="ybArea" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={tile.color} stopOpacity="0.22" />
                  <stop offset="100%" stopColor={tile.color} stopOpacity="0" />
                </linearGradient>
              </defs>
              {/* Área con gradiente */}
              <path d={serie.area} fill="url(#ybArea)" />
              {/* Línea principal */}
              <path
                d={serie.line}
                fill="none"
                stroke={tile.color}
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </div>

          {/* Labels de referencia */}
          <div className={styles.labels}>
            <span>mín {serie.min}</span>
            <span>banda óptima {tile.ideal}</span>
            <span>máx {serie.max}</span>
          </div>
        </div>
      ) : (
        <p className={styles.empty}>
          El nodo testigo no reportó {tile.label.toLowerCase()} — no hay serie para mostrar.
        </p>
      )}
    </div>
  );
}
