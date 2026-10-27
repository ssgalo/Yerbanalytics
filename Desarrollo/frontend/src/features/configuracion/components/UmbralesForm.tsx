/* Umbrales de las métricas: ideal, crítico y puntos de alerta sobre una barra (HU-15 CA-03) */
import { useRef, type KeyboardEvent, type PointerEvent } from 'react';
import type { MetricThreshold } from '@/types/domain';
import { umbralError } from '@/lib/configValidation';
import { Input } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { fmtNum } from '@/components/ui/rango';
import styles from './UmbralesForm.module.css';

interface UmbralesFormProps {
  value: MetricThreshold[];
  /** Umbrales guardados: marcan qué campo cambió. */
  saved?: MetricThreshold[];
  /** Sólo lectura: ni campos ni manijas se pueden mover. */
  disabled?: boolean;
  onChange: (index: number, patch: Partial<MetricThreshold>) => void;
}

type EditableKey = 'warnMin' | 'alertMin' | 'idealMin' | 'idealMax' | 'alertMax' | 'warnMax';

/*
 * De izquierda a derecha sobre la recta. `warnMin`/`warnMax` son el borde del crítico: es lo que
 * hacen en el backend (debajo de warnMin, crítico). critMin/critMax son el rango fisiológico: la
 * escala de la barra y el tope de las manijas, no se editan acá.
 */
const ORDER: EditableKey[] = ['warnMin', 'alertMin', 'idealMin', 'idealMax', 'alertMax', 'warnMax'];

const LABEL: Record<EditableKey, string> = {
  warnMin: 'Crítico debajo de',
  alertMin: 'Alerta debajo de',
  idealMin: 'Ideal desde',
  idealMax: 'Ideal hasta',
  alertMax: 'Alerta arriba de',
  warnMax: 'Crítico arriba de',
};

const isAlert = (k: EditableKey) => k === 'alertMin' || k === 'alertMax';

/** Paso de una métrica según sus decimales (1, 0,1, 0,01…). */
const stepOf = (dec: number) => 10 ** -dec;
const roundTo = (v: number, dec: number) => Number(v.toFixed(dec));

/**
 * Límites entre los que puede moverse un valor sin romper el orden: sus vecinos en la recta (el
 * ideal, además, nunca se cierra sobre sí mismo). Los extremos son el rango fisiológico.
 */
function boundsOf(u: MetricThreshold, key: EditableKey): [number, number] {
  const i = ORDER.indexOf(key);
  const step = stepOf(u.dec);
  let lo = i === 0 ? u.critMin : u[ORDER[i - 1]];
  let hi = i === ORDER.length - 1 ? u.critMax : u[ORDER[i + 1]];
  if (key === 'idealMax') lo = roundTo(lo + step, u.dec);
  if (key === 'idealMin') hi = roundTo(hi - step, u.dec);
  return [lo, hi];
}

/** El valor rompe el orden con alguno de sus vecinos: el campo se marca inválido. */
function outOfOrder(u: MetricThreshold, key: EditableKey): boolean {
  const [lo, hi] = boundsOf(u, key);
  return u[key] < lo || u[key] > hi;
}

interface BandBarProps {
  u: MetricThreshold;
  disabled?: boolean;
  onChange: (patch: Partial<MetricThreshold>) => void;
}

/** Barra de la métrica: zonas roja, amarilla (con y sin alerta) y verde, y una manija por valor. */
function BandBar({ u, disabled, onChange }: BandBarProps) {
  const trackRef = useRef<HTMLDivElement>(null);
  const span = u.critMax - u.critMin;
  const pos = (v: number) => (span > 0 ? Math.max(0, Math.min(100, ((v - u.critMin) / span) * 100)) : 50);
  const step = stepOf(u.dec);

  const set = (key: EditableKey, raw: number) => {
    const [lo, hi] = boundsOf(u, key);
    const v = roundTo(Math.min(hi, Math.max(lo, Math.round(raw / step) * step)), u.dec);
    if (v !== u[key]) onChange({ [key]: v });
  };

  const valueAt = (clientX: number) => {
    const r = trackRef.current?.getBoundingClientRect();
    if (!r || r.width === 0) return null;
    return u.critMin + ((clientX - r.left) / r.width) * span;
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (disabled) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    e.currentTarget.focus();
  };

  const onPointerMove = (key: EditableKey) => (e: PointerEvent<HTMLDivElement>) => {
    if (disabled || !e.currentTarget.hasPointerCapture(e.pointerId)) return;
    const v = valueAt(e.clientX);
    if (v !== null) set(key, v);
  };

  const onKeyDown = (key: EditableKey) => (e: KeyboardEvent<HTMLDivElement>) => {
    if (disabled) return;
    const big = e.shiftKey ? 10 : 1;
    const [lo, hi] = boundsOf(u, key);
    const next: Record<string, number> = {
      ArrowLeft: u[key] - step * big,
      ArrowDown: u[key] - step * big,
      ArrowRight: u[key] + step * big,
      ArrowUp: u[key] + step * big,
      Home: lo,
      End: hi,
    };
    if (e.key in next) {
      e.preventDefault();
      set(key, next[e.key]);
    }
  };

  const zone = (from: number, to: number, className: string) => (
    <span className={className} style={{ left: `${pos(from)}%`, width: `${Math.max(0, pos(to) - pos(from))}%` }} />
  );

  return (
    <div className={styles.bar}>
      <div className={styles.track} ref={trackRef} aria-hidden="true">
        {zone(u.critMin, u.warnMin, styles.zoneCrit)}
        {zone(u.warnMin, u.alertMin, styles.zoneAlert)}
        {zone(u.alertMin, u.idealMin, styles.zoneWarn)}
        {zone(u.idealMin, u.idealMax, styles.zoneIdeal)}
        {zone(u.idealMax, u.alertMax, styles.zoneWarn)}
        {zone(u.alertMax, u.warnMax, styles.zoneAlert)}
        {zone(u.warnMax, u.critMax, styles.zoneCrit)}
      </div>
      {ORDER.map((key) => {
        const [lo, hi] = boundsOf(u, key);
        return (
          <div
            key={key}
            role="slider"
            tabIndex={disabled ? -1 : 0}
            aria-label={`${u.label} · ${LABEL[key]}`}
            aria-valuemin={lo}
            aria-valuemax={hi}
            aria-valuenow={u[key]}
            aria-valuetext={`${fmtNum(u[key], u.dec)} ${u.unit}`}
            aria-disabled={disabled || undefined}
            title={`${LABEL[key]}: ${fmtNum(u[key], u.dec)} ${u.unit}`}
            className={isAlert(key) ? styles.knobAlert : styles.knob}
            style={{ left: `${pos(u[key])}%` }}
            onPointerDown={onPointerDown}
            onPointerMove={onPointerMove(key)}
            onKeyDown={onKeyDown(key)}
          />
        );
      })}
    </div>
  );
}

export function UmbralesForm({ value, saved, disabled, onChange }: UmbralesFormProps) {
  return (
    <div>
      <div className={styles.legend} aria-hidden="true">
        <span>
          <i className={styles.swIdeal} /> Ideal
        </span>
        <span>
          <i className={styles.swWarn} /> En observación
        </span>
        <span>
          <i className={styles.swAlert} /> En observación, con alerta
        </span>
        <span>
          <i className={styles.swCrit} /> Crítico
        </span>
      </div>
      <ul className={styles.list}>
        {value.map((u, i) => {
          const err = umbralError(u);
          const antes = saved?.find((s) => s.key === u.key);
          return (
            <li key={u.key} className={styles.row}>
              <div className={styles.metric}>
                <span className={styles.metricName}>
                  {u.label} <span className={styles.metricUnit}>{u.unit}</span>
                </span>
              </div>
              <div className={styles.editor}>
                <BandBar u={u} disabled={disabled} onChange={(patch) => onChange(i, patch)} />
                <div className={styles.fields}>
                  {ORDER.map((key) => (
                    <label key={key} className={isAlert(key) ? `${styles.field} ${styles.fieldAlert}` : styles.field}>
                      <span className={styles.fieldLabel}>
                        {isAlert(key) && <Icon name="bell" size={12} />}
                        {LABEL[key]}
                      </span>
                      <Input
                        className={styles.input}
                        type="number"
                        inputMode="decimal"
                        step={stepOf(u.dec)}
                        value={u[key]}
                        invalid={outOfOrder(u, key)}
                        modified={antes !== undefined && antes[key] !== u[key]}
                        aria-label={`${u.label} · ${LABEL[key]}`}
                        onChange={(e) => {
                          const n = Number(e.target.value);
                          if (e.target.value !== '' && !Number.isNaN(n)) onChange(i, { [key]: n });
                        }}
                      />
                    </label>
                  ))}
                </div>
                {err && (
                  <p className={styles.error}>
                    <Icon name="alert" size={14} />
                    {err}
                  </p>
                )}
              </div>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
