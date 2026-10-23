/* ============================================================
   Rustificación de la macro-zona: el plan vigente (si hay) o el horario por defecto de la
   mediasombra, y desde acá se inicia o se elimina un plan. Un plan es de UNA macro-zona, como
   su mediasombra (hay una por zona).
   ============================================================ */
import { useState } from 'react';
import type { Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { EmptyState, Skeleton } from '@/components/ui/EstadosPantalla';
import { linkClass } from '@/components/ui/clases';
import { cx } from '@/components/ui/estado';
import { useAuth } from '@/hooks/AuthContext';
import { useConfig } from '@/hooks/useConfig';
import { useHardeningPlan } from '../useHardeningPlan';
import { HardeningPlanForm } from './HardeningPlanForm';
import { HardeningPlanView } from './HardeningPlanView';
import panel from './Panel.module.css';

export function HardeningCard({ zona, className }: { zona: Zona; className?: string }) {
  const { plan, loading, error, busy, reload, start, cancel } = useHardeningPlan(zona.id);
  const { config } = useConfig();
  // Iniciar o eliminar un plan cambia cómo opera la mediasombra: lo hace quien edita la configuración.
  const canManage = useAuth().puede('configuracion.editar');
  const [editing, setEditing] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const defaultWindow = config
    ? { desde: config.operativa.mediasombraAbiertaDesde, hasta: config.operativa.mediasombraAbiertaHasta }
    : null;

  const body = (() => {
    if (loading) return <Skeleton height={120} />;
    if (error) {
      return (
        <Callout tone="crit" role="alert">
          No se pudo leer el plan de rustificación: {error.message}{' '}
          <button type="button" className={linkClass} onClick={reload}>
            Reintentar
          </button>
        </Callout>
      );
    }
    if (editing) {
      return (
        <HardeningPlanForm
          zonaName={zona.name}
          submitting={busy}
          submitError={submitError}
          onCancel={() => {
            setEditing(false);
            setSubmitError(null);
          }}
          onSubmit={(nuevo) => {
            setSubmitError(null);
            start(nuevo)
              .then(() => setEditing(false))
              .catch((e: unknown) => setSubmitError(e instanceof Error ? e.message : String(e)));
          }}
        />
      );
    }
    if (plan) {
      return <HardeningPlanView plan={plan} defaultWindow={defaultWindow} canManage={canManage} busy={busy} onCancel={cancel} />;
    }
    return (
      <EmptyState
        title="Sin plan de rustificación"
        action={
          <Button variant="primary" disabled={!canManage} onClick={() => setEditing(true)}>
            Iniciar plan
          </Button>
        }
      >
        {defaultWindow
          ? `La mediasombra sigue el horario por defecto: abierta de ${defaultWindow.desde} a ${defaultWindow.hasta}, cerrada el resto del día.`
          : 'La mediasombra sigue el horario por defecto de la configuración.'}{' '}
        {canManage
          ? 'Un plan la va abriendo cada vez más horas, etapa por etapa, antes del trasplante.'
          : 'Tu rol no puede iniciar planes.'}
      </EmptyState>
    );
  })();

  return (
    <Card className={cx(panel.panel, className)}>
      <div className={panel.head}>
        <h2 className={panel.title}>Rustificación</h2>
        <span className={panel.meta}>
          {editing
            ? `Nuevo plan para ${zona.name}`
            : zona.mediasombra
              ? `Mediasombra ${zona.mediasombra} (última orden)`
              : 'Exposición progresiva al sol antes del trasplante'}
        </span>
      </div>
      {body}
    </Card>
  );
}
