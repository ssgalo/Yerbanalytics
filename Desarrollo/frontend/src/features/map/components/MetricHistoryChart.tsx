/* ============================================================
   Histórico de una métrica del nodo testigo, dibujado sobre sus
   bandas: crítico de fondo, observación e ideal. Se lee de un vistazo
   cuánto tiempo pasó la zona fuera del ideal.

   El SVG se estira al ancho disponible (`preserveAspectRatio="none"`)
   y sólo lleva bandas y línea; los textos, el punto actual y el cursor
   van en HTML encima, para no deformarse.
   ============================================================ */
import { useMemo, useState, type PointerEvent } from 'react';
import type { Metric, Range, Zona } from '@/types/domain';
import { selectMetricHistory } from '@/data';
import { Segmented } from '@/components/ui/Seleccion';
import { escalaDeRango, fmtNum } from '@/components/ui/rango';
import styles from './MetricHistoryChart.module.css';

const RANGES: { value: Range; label: string }[] = [
  { value: '24h', label: '24 h' },
  { value: '7d', label: '7 días' },
  { value: '30d', label: '30 días' },
];

/** Índices de los puntos que llevan fecha en el eje: el último siempre, que es "ahora". */
const TICK_EVERY: Record<Range, number> = { '24h': 6, '7d': 12, '30d': 7 };

const fmtHour = new Intl.DateTimeFormat('es-AR', { hour: '2-digit', minute: '2-digit' });
const fmtWeekday = new Intl.DateTimeFormat('es-AR', { weekday: 'short', day: 'numeric' });
const fmtDay = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short' });
const fmtFull = new Intl.DateTimeFormat('es-AR', {
  weekday: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function tickLabel(ts: number, range: Range, last: boolean): string {
  if (last) return range === '24h' ? 'ahora' : 'hoy';
  if (range === '24h') return fmtHour.format(ts);
  return (range === '7d' ? fmtWeekday : fmtDay).format(ts).replace('.', '');
}

interface MetricHistoryChartProps {
  zona: Zona;
  metric: Metric;
  range: Range;
  onRangeChange: (r: Range) => void;
}

export function MetricHistoryChart({
  zona,
  metric,
  range,
  onRangeChange,
}: MetricHistoryChartProps) {
  const history = useMemo(
    () => selectMetricHistory(zona, metric.key, range),
    [zona, metric.key, range],
  );
  const [hover, setHover] = useState<number | null>(null);
  const { spec, unit } = metric;
  const f = (v: number) => fmtNum(v, spec.dec);
  const withUnit = (v: number) => `${f(v)} ${unit}`;

  const header = (
    <div className={styles.head}>
      <span className={styles.headTitle}>Histórico del nodo testigo</span>
      <Segmented
        label="Ventana del histórico"
        options={RANGES}
        value={range}
        onChange={onRangeChange}
      />
    </div>
  );

  if (!history) {
    return (
      <div>
        {header}
        <p className={styles.empty}>
          El nodo testigo no reportó {metric.label.toLowerCase()}: no hay serie para mostrar.
        </p>
      </div>
    );
  }

  /* Escala vertical: la de la barra de rango, ampliada si la serie se sale de ella. */
  const scale = escalaDeRango(spec);
  const pad = (scale.hi - scale.lo) * 0.04;
  const lo = Math.min(scale.lo, history.min - pad);
  const hi = Math.max(scale.hi, history.max + pad);
  const y = (v: number) => ((hi - v) / (hi - lo)) * 100;

  const { points } = history;
  const x = (i: number) => (i / (points.length - 1)) * 100;
  const line = points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(2)},${y(p.value).toFixed(2)}`)
    .join(' ');
  const band = (a: number, b: number) => ({ y: y(b), height: y(a) - y(b) });
  const edges = [spec.warn[0], spec.ideal[0], spec.ideal[1], spec.warn[1]];

  const ticks = points
    .map((p, i) => ({ i, ts: p.ts }))
    .filter(
      ({ i }) =>
        i === points.length - 1 ||
        (i % TICK_EVERY[range] === 0 && points.length - 1 - i >= TICK_EVERY[range] / 2),
    );

  const current = hover ?? points.length - 1;
  const currentPoint = points[current];

  function handleMove(e: PointerEvent<HTMLDivElement>) {
    const r = e.currentTarget.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - r.left) / r.width));
    setHover(Math.round(ratio * (points.length - 1)));
  }

  return (
    <div>
      {header}

      <div className={styles.chart}>
        <div className={styles.yAxis} aria-hidden="true">
          {edges.map((v, i) => (
            <span key={i} style={{ top: `${y(v)}%` }}>
              {f(v)}
            </span>
          ))}
        </div>

        <div
          className={styles.plot}
          role="img"
          aria-label={`${metric.label}, ${RANGES.find((r) => r.value === range)?.label}: mínimo ${withUnit(history.min)}, máximo ${withUnit(history.max)}, actual ${withUnit(points[points.length - 1].value)}; ideal entre ${f(spec.ideal[0])} y ${f(spec.ideal[1])}`}
          onPointerMove={handleMove}
          onPointerLeave={() => setHover(null)}
        >
          <svg
            className={styles.svg}
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden="true"
          >
            <rect className={styles.bandCrit} x="0" y="0" width="100" height="100" />
            <rect
              className={styles.bandWarn}
              x="0"
              width="100"
              {...band(spec.warn[0], spec.warn[1])}
            />
            <rect
              className={styles.bandIdeal}
              x="0"
              width="100"
              {...band(spec.ideal[0], spec.ideal[1])}
            />
            {edges.map((v, i) => (
              <line key={i} className={styles.edge} x1="0" x2="100" y1={y(v)} y2={y(v)} />
            ))}
            <path className={styles.line} d={line} />
          </svg>

          {hover !== null && <span className={styles.cursor} style={{ left: `${x(current)}%` }} />}
          <span
            className={styles.dot}
            style={{ left: `${x(current)}%`, top: `${y(currentPoint.value)}%` }}
          />

          {hover !== null && (
            <span
              className={styles.tooltip}
              style={{ left: `${x(current)}%`, top: `${y(currentPoint.value)}%` }}
              data-side={x(current) > 70 ? 'left' : 'right'}
            >
              <b>{withUnit(currentPoint.value)}</b>
              <span>{fmtFull.format(currentPoint.ts).replace('.', '')}</span>
            </span>
          )}
        </div>

        <div className={styles.xAxis} aria-hidden="true">
          {ticks.map((t) => (
            <span
              key={t.i}
              style={{ left: `${x(t.i)}%` }}
              data-last={t.i === points.length - 1 || undefined}
            >
              {tickLabel(t.ts, range, t.i === points.length - 1)}
            </span>
          ))}
        </div>
      </div>

      <div className={styles.legend}>
        <span className={styles.key}>
          <i className={styles.swIdeal} /> Ideal
        </span>
        <span className={styles.key}>
          <i className={styles.swWarn} /> Observación
        </span>
        <span className={styles.key}>
          <i className={styles.swCrit} /> Crítico
        </span>
        <span className={styles.extremes}>
          Ideal {f(spec.ideal[0])}–{f(spec.ideal[1])} · mín. {f(history.min)} · máx.{' '}
          {f(history.max)} {unit}
        </span>
      </div>
    </div>
  );
}
