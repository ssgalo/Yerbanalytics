/* ============================================================
   Alta de un plan de rustificación para una macro-zona: fecha de inicio y, por etapa, cuántos
   días dura y de qué hora a qué hora queda abierta la mediasombra (fuera de esa ventana, cerrada).
   La mediasombra sólo abre o cierra del todo: no hay porcentaje de apertura.
   ============================================================ */
import { useId, useState } from 'react';
import type { EtapaRustificacion, NuevoPlanRustificacion } from '@/types/domain';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Field, Input, InputUnit } from '@/components/ui/Field';
import { Icon } from '@/components/ui/Icon';
import { ShadeWindowStrip } from '@/components/ShadeWindowStrip';
import { formatDate, formatDuration, minutesOf, todayIso, windowMinutes } from '@/lib/horario';
import {
  ETAPAS_SUGERIDAS,
  etapasQueRetroceden,
  fechaFin,
  MAX_DIAS_ETAPA,
  planErrors,
  totalDias,
} from '@/lib/planRustificacion';
import styles from './Hardening.module.css';

type Etapa = Omit<EtapaRustificacion, 'orden'>;

interface HardeningPlanFormProps {
  zonaName: string;
  submitting: boolean;
  /** Rechazo del backend al iniciar (se muestra tal cual). */
  submitError: string | null;
  onSubmit: (plan: NuevoPlanRustificacion) => void;
  onCancel: () => void;
}

/** "HH:MM" corrida `delta` minutos, sin salir del día. */
function shift(hhmm: string, delta: number): string {
  const m = minutesOf(hhmm);
  if (m === null) return hhmm;
  const v = Math.max(0, Math.min(23 * 60 + 59, m + delta));
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(Math.floor(v / 60))}:${p(v % 60)}`;
}

export function HardeningPlanForm({ zonaName, submitting, submitError, onSubmit, onCancel }: HardeningPlanFormProps) {
  const id = useId();
  const hoy = todayIso();
  const [fechaInicio, setFechaInicio] = useState(hoy);
  const [etapas, setEtapas] = useState<Etapa[]>(() => ETAPAS_SUGERIDAS.map((e) => ({ ...e })));

  const plan: NuevoPlanRustificacion = { fechaInicio, etapas };
  const errors = planErrors(plan, hoy);
  const retroceden = etapasQueRetroceden(etapas);
  const horas = etapas.map((e) => windowMinutes(e.abiertaDesde, e.abiertaHasta));

  const update = (i: number, patch: Partial<Etapa>) =>
    setEtapas((es) => es.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));

  // La etapa nueva arranca una hora más abierta de cada lado que la última: la idea del plan.
  const add = () =>
    setEtapas((es) => {
      const last = es[es.length - 1] ?? { dias: 7, abiertaDesde: '10:00', abiertaHasta: '13:00' };
      return [...es, { dias: last.dias, abiertaDesde: shift(last.abiertaDesde, -60), abiertaHasta: shift(last.abiertaHasta, 60) }];
    });

  return (
    <form
      className={styles.form}
      aria-label={`Nuevo plan de rustificación de ${zonaName}`}
      onSubmit={(e) => {
        e.preventDefault();
        if (errors.length === 0) onSubmit(plan);
      }}
    >
      <div className={styles.formHead}>
        <Field label="Fecha de inicio" htmlFor={`${id}-inicio`}>
          <Input
            id={`${id}-inicio`}
            className={styles.dateInput}
            type="date"
            min={hoy}
            value={fechaInicio}
            onChange={(e) => setFechaInicio(e.target.value)}
          />
        </Field>
        {errors.length === 0 && (
          <p className={styles.summary}>
            Del <b>{formatDate(fechaInicio)}</b> al <b>{formatDate(fechaFin(fechaInicio, etapas))}</b> ·{' '}
            {totalDias(etapas)} días · de {formatDuration(Math.min(...horas))} a {formatDuration(Math.max(...horas))} de
            sol por día
          </p>
        )}
      </div>

      <table className={styles.stages}>
        <thead>
          <tr>
            <th>Etapa</th>
            <th>Duración</th>
            <th>Abierta desde</th>
            <th>Abierta hasta</th>
            <th className={styles.stripCol}>Día (0–24 h)</th>
            <th>
              <span className="sr">Acciones</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {etapas.map((e, i) => (
            <tr key={i}>
              <td className={styles.stageNum}>{i + 1}</td>
              <td>
                <InputUnit
                  unit="días"
                  className={styles.daysInput}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={MAX_DIAS_ETAPA}
                  value={Number.isNaN(e.dias) ? '' : e.dias}
                  invalid={!Number.isInteger(e.dias) || e.dias < 1 || e.dias > MAX_DIAS_ETAPA}
                  aria-label={`Etapa ${i + 1} · Duración en días`}
                  onChange={(ev) => update(i, { dias: ev.target.valueAsNumber })}
                />
              </td>
              <td>
                <Input
                  className={styles.timeInput}
                  type="time"
                  value={e.abiertaDesde}
                  invalid={horas[i] === 0}
                  aria-label={`Etapa ${i + 1} · Abierta desde`}
                  onChange={(ev) => update(i, { abiertaDesde: ev.target.value })}
                />
              </td>
              <td>
                <Input
                  className={styles.timeInput}
                  type="time"
                  value={e.abiertaHasta}
                  invalid={horas[i] === 0}
                  aria-label={`Etapa ${i + 1} · Abierta hasta`}
                  onChange={(ev) => update(i, { abiertaHasta: ev.target.value })}
                />
              </td>
              <td className={styles.stripCol}>
                <ShadeWindowStrip desde={e.abiertaDesde} hasta={e.abiertaHasta} />
                <span className={styles.stripNote}>{horas[i] > 0 ? formatDuration(horas[i]) : '—'}</span>
              </td>
              <td>
                <Button
                  variant="ghost"
                  size="sm"
                  className={styles.iconBtn}
                  aria-label={`Quitar etapa ${i + 1}`}
                  disabled={etapas.length === 1}
                  onClick={() => setEtapas((es) => es.filter((_, idx) => idx !== i))}
                >
                  <Icon name="close" size={16} />
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <Button size="sm" className={styles.addBtn} onClick={add}>
        + Agregar etapa
      </Button>

      {retroceden.length > 0 && (
        <Callout tone="warn">
          {retroceden.length === 1 ? `La etapa ${retroceden[0]} expone` : `Las etapas ${retroceden.join(', ')} exponen`}{' '}
          menos horas que la anterior. El plan puede guardarse igual, pero la idea es ir sumando sol de a poco.
        </Callout>
      )}

      {errors.length > 0 && (
        <ul className={styles.errors}>
          {errors.map((e) => (
            <li key={e}>
              <Icon name="alert" size={14} />
              {e}
            </li>
          ))}
        </ul>
      )}

      {submitError && (
        <Callout tone="crit" role="alert">
          No se pudo iniciar el plan: {submitError}
        </Callout>
      )}

      <p className={styles.note}>
        Fuera de la ventana de cada etapa la mediasombra queda cerrada. Dentro de ella, otras reglas (por ejemplo, la
        protección por radiación) pueden cerrarla igual.
      </p>

      <div className={styles.actions}>
        <Button onClick={onCancel} disabled={submitting}>
          Cancelar
        </Button>
        <Button type="submit" variant="primary" disabled={submitting || errors.length > 0}>
          {submitting ? 'Iniciando…' : 'Iniciar plan'}
        </Button>
      </div>
    </form>
  );
}
