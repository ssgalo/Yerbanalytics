/* Gráfico principal de humedad de sustrato — replica exacta del diseño HTML (líneas 486-503) */
import type { Range } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import styles from './MainChart.module.css';

const RANGES: Range[] = ['24h', '7d', '30d'];

interface MainChartProps {
  mainLine: string;
  mainArea: string;
  mainMin: string;
  mainMax: string;
  range: Range;
  onRangeChange: (r: Range) => void;
}

export function MainChart({ mainLine, mainArea, mainMin, mainMax, range, onRangeChange }: MainChartProps) {
  return (
    <Card className={styles.card}>
      <div className={styles.header}>
        <h3 className={styles.title}>Humedad de sustrato</h3>

        {/* Selector de rango */}
        <div className={styles.rangeSelector}>
          {RANGES.map((r) => (
            <button
              key={r}
              className={r === range ? `${styles.rangeBtn} ${styles.rangeBtnActive}` : styles.rangeBtn}
              onClick={() => onRangeChange(r)}
            >
              {r}
            </button>
          ))}
        </div>
      </div>

      {/* SVG del gráfico — NO usa el átomo Sparkline, renderizado directo */}
      <div className={styles.chartWrap}>
        <svg
          width="100%"
          height="170"
          viewBox="0 0 660 170"
          preserveAspectRatio="none"
          className={styles.svg}
        >
          <defs>
            <linearGradient id="ybArea" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#1E6A4E" stopOpacity="0.22" />
              <stop offset="100%" stopColor="#1E6A4E" stopOpacity="0" />
            </linearGradient>
          </defs>
          {/* Área con gradiente */}
          <path d={mainArea} fill="url(#ybArea)" />
          {/* Línea principal */}
          <path
            d={mainLine}
            fill="none"
            stroke="var(--brand)"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </div>

      {/* Labels de referencia */}
      <div className={styles.labels}>
        <span>mín {mainMin}%</span>
        <span>banda óptima 42–68%</span>
        <span>máx {mainMax}%</span>
      </div>
    </Card>
  );
}
