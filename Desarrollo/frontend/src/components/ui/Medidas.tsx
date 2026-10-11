import type { ReactNode } from 'react';
import type { MetricSpec, Status } from '@/types/domain';
import { ETIQUETA_ESTADO, TONO, cx } from './estado';
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
  value: number;
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
        aria-label={`${spec.label}: ${f(value)} ${spec.unit}; ideal entre ${f(spec.ideal[0])} y ${f(spec.ideal[1])}`}
      >
        <span className={styles.bandWarn} style={tramo(spec.warn[0], spec.warn[1])} />
        <span className={styles.bandIdeal} style={tramo(spec.ideal[0], spec.ideal[1])} />
        <span className={styles.mark} style={{ left: `${pos(value)}%` }} />
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

interface ConfidenceBarProps {
  /** Confianza del modelo, en %. */
  value: number;
  /** Mínimo para que el motor actúe, en %. Siempre del catálogo de parámetros, nunca fijo. */
  min: number;
  /** Texto debajo; por defecto dice si alcanza el mínimo. */
  children?: ReactNode;
}

/** Confianza del diagnóstico con el mínimo marcado sobre la barra. */
export function ConfidenceBar({ value, min, children }: ConfidenceBarProps) {
  const alcanza = value >= min;
  return (
    <div>
      <div className={styles.confHead}>
        <span>Confianza del modelo</span>
        <b className={styles.confValor}>{fmtNum(value)} %</b>
      </div>
      <div
        className={cx(styles.conf, !alcanza && styles.low)}
        role="img"
        aria-label={`Confianza ${fmtNum(value)} %, mínimo para actuar ${fmtNum(min)} %`}
      >
        <i style={{ width: `${Math.max(0, Math.min(100, value))}%` }} />
        <span className={styles.thr} style={{ left: `${min}%` }}>
          <span className={styles.thrLabel}>mín. {fmtNum(min)} %</span>
        </span>
      </div>
      <p className={styles.confNota}>
        {children ??
          (alcanza
            ? 'Alcanza el mínimo para que el motor actúe.'
            : 'No alcanza el mínimo: el motor no actúa con este diagnóstico.')}
      </p>
    </div>
  );
}
