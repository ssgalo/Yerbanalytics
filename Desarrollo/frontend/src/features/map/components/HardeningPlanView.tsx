/* Plan de rustificación vigente de una macro-zona: dónde va, qué rige hoy, sus etapas y la cancelación. */
import { useState } from 'react';
import type { PlanRustificacion } from '@/types/domain';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Chip } from '@/components/ui/Chip';
import { ShadeWindowStrip } from '@/components/ShadeWindowStrip';
import { cx } from '@/components/ui/estado';
import { addDays, formatDate, formatDuration, todayIso, windowMinutes } from '@/lib/horario';
import { fechaFin, progreso } from '@/lib/planRustificacion';
import styles from './Hardening.module.css';

interface HardeningPlanViewProps {
  plan: PlanRustificacion;
  /** Horario por defecto al que vuelve la zona si se cancela; null si no se pudo leer. */
  defaultWindow: { desde: string; hasta: string } | null;
  canManage: boolean;
  busy: boolean;
  onCancel: () => Promise<void>;
}

export function HardeningPlanView({ plan, defaultWindow, canManage, busy, onCancel }: HardeningPlanViewProps) {
  const [confirming, setConfirming] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);
  const pr = progreso(plan);
  const hoy = pr.etapa;

  // Rango de fechas de cada etapa, encadenadas desde el inicio.
  let desde = 0;
  const rangos = plan.etapas.map((e) => {
    const r = { inicio: addDays(plan.fechaInicio, desde), fin: addDays(plan.fechaInicio, desde + e.dias - 1) };
    desde += e.dias;
    return r;
  });

  const cancel = async () => {
    setCancelError(null);
    try {
      await onCancel();
    } catch (e) {
      setCancelError(e instanceof Error ? e.message : String(e));
      setConfirming(false);
    }
  };

  return (
    <div className={styles.view}>
      <div className={styles.status}>
        {pr.dia === 0 ? (
          <>
            <Chip tone="line">Programado</Chip>
            <span>
              Empieza el <b>{formatDate(plan.fechaInicio)}</b> ({pr.faltan === 1 ? 'mañana' : `en ${pr.faltan} días`})
            </span>
          </>
        ) : (
          <>
            <Chip tone="info">En curso</Chip>
            <span>
              Día <b>{pr.dia}</b> de {pr.totalDias} · etapa {hoy?.orden} de {plan.etapas.length}
            </span>
          </>
        )}
        <span className={styles.statusDates}>
          {formatDate(plan.fechaInicio)} → {formatDate(fechaFin(plan.fechaInicio, plan.etapas))}
        </span>
      </div>

      <div
        className={styles.progress}
        role="progressbar"
        aria-label="Avance del plan"
        aria-valuemin={0}
        aria-valuemax={pr.totalDias}
        aria-valuenow={pr.dia}
      >
        <i style={{ width: `${pr.totalDias > 0 ? (pr.dia / pr.totalDias) * 100 : 0}%` }} />
      </div>

      {hoy && (
        <div className={styles.today}>
          <span className={styles.todayLabel}>
            Hoy, abierta de <b>{hoy.abiertaDesde}</b> a <b>{hoy.abiertaHasta}</b> (
            {formatDuration(windowMinutes(hoy.abiertaDesde, hoy.abiertaHasta))})
          </span>
          <ShadeWindowStrip desde={hoy.abiertaDesde} hasta={hoy.abiertaHasta} scale now={new Date()} />
        </div>
      )}

      <ol className={styles.stageList}>
        {plan.etapas.map((e, i) => {
          const actual = hoy?.orden === e.orden;
          const pasada = pr.dia > 0 && !actual && rangos[i].fin < addDays(plan.fechaInicio, pr.dia - 1);
          return (
            <li key={e.orden} className={cx(styles.stageItem, actual && styles.stageCurrent, pasada && styles.stagePast)}>
              <span className={styles.stageTitle}>
                Etapa {e.orden}
                {actual && <span className="sr"> (actual)</span>}
              </span>
              <span className={styles.stageMeta}>
                {formatDate(rangos[i].inicio)} – {formatDate(rangos[i].fin)} · {e.dias} {e.dias === 1 ? 'día' : 'días'}
              </span>
              <span className={styles.stageWindow}>
                {e.abiertaDesde}–{e.abiertaHasta}
              </span>
              <ShadeWindowStrip desde={e.abiertaDesde} hasta={e.abiertaHasta} />
            </li>
          );
        })}
      </ol>

      <p className={styles.note}>
        Fuera de la ventana de cada etapa la mediasombra queda cerrada. Dentro de ella, otras reglas pueden cerrarla
        igual.
      </p>

      {cancelError && (
        <Callout tone="crit" role="alert">
          No se pudo cancelar el plan: {cancelError}
        </Callout>
      )}

      {confirming ? (
        <Callout tone="warn">
          <p className={styles.confirmText}>
            ¿Cancelar el plan? La mediasombra de esta macro-zona vuelve desde ahora al horario por defecto
            {defaultWindow ? ` (abierta de ${defaultWindow.desde} a ${defaultWindow.hasta})` : ''}. No se puede deshacer.
          </p>
          <div className={styles.confirmActions}>
            <Button size="sm" onClick={() => setConfirming(false)} disabled={busy}>
              No, seguir con el plan
            </Button>
            <Button size="sm" variant="danger" onClick={() => void cancel()} disabled={busy}>
              {busy ? 'Cancelando…' : 'Sí, cancelar plan'}
            </Button>
          </div>
        </Callout>
      ) : (
        <div className={styles.footer}>
          <span className={styles.meta}>
            {plan.creadoPor ? `Iniciado por ${plan.creadoPor}` : 'Iniciado'} el{' '}
            {formatDate(todayIso(new Date(plan.creadoEn)))}
          </span>
          {canManage && (
            <Button variant="danger" size="sm" onClick={() => setConfirming(true)}>
              Cancelar plan
            </Button>
          )}
        </div>
      )}
    </div>
  );
}
