import type { MetricSpec, Status } from '@/types/domain';
import { ETIQUETA_ESTADO, TONO } from './estado';
import { escalaDeRango, fmtNum } from './rango';
import styles from './Medidas.module.css';

const ORDEN: Status[] = ['ok', 'warning', 'critical', 'offline'];

/** Composición de un conjunto de sectores por estado (p. ej. los 600 del vivero). */
export function CompositionBar({ counts }: { counts: Partial<Record<Status, number>> }) {
  const total = ORDEN.reduce((a, s) => a + (counts[s] ?? 0), 0);
  const partes = ORDEN.filter((s) => (counts[s] ?? 0) > 0);
  const label = `${total} sectores: ${partes.map((s) => `${counts[s]} ${ETIQUETA_ESTADO[s].toLowerCase()}`).join(', ')}`;
  return (
    <div className={styles.compbar} role="img" aria-label={label}>
      {partes.map((s) => (
        <i key={s} className={styles[TONO[s]]} style={{ flex: counts[s] }} title={`${ETIQUETA_ESTADO[s]}: ${counts[s]}`} />
      ))}
    </div>
  );
}

interface RangeBarProps {
  /** Sin valor, la barra muestra sólo las bandas (p. ej. al editarlas). */
  value?: number;
  spec: Pick<MetricSpec, 'label' | 'unit' | 'dec' | 'ideal' | 'warn' | 'crit'>;
  /** Muestra debajo los extremos de la banda crítica. */
  scale?: boolean;
}

/** Un valor contra sus bandas: crítico (fondo), observación e ideal, con una marca en el valor. */
export function RangeBar({ value, spec, scale = false }: RangeBarProps) {
  const { pos } = escalaDeRango(spec);
  const tramo = (a: number, b: number) => ({ left: `${pos(a)}%`, right: `${100 - pos(b)}%` });
  const f = (v: number) => fmtNum(v, spec.dec);
  return (
    <div>
      <div
        className={styles.rangebar}
        role="img"
        aria-label={`${spec.label}: ${value !== undefined ? `${f(value)} ${spec.unit}; ` : ''}ideal entre ${f(spec.ideal[0])} y ${f(spec.ideal[1])}`}
      >
        <span className={styles.bandWarn} style={tramo(spec.warn[0], spec.warn[1])} />
        <span className={styles.bandIdeal} style={tramo(spec.ideal[0], spec.ideal[1])} />
        {value !== undefined && <span className={styles.mark} style={{ left: `${pos(value)}%` }} />}
      </div>
      {scale && (
        <div className={styles.scale} aria-hidden="true">
          <span>{f(spec.crit[0])}</span>
          <span>{f(spec.crit[1])}</span>
        </div>
      )}
    </div>
  );
}
