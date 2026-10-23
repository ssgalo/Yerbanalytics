/* ============================================================
   Plan de nutrición de una macro-zona (reglas_v2 §8.4): fertilizante foliar en todos sus sectores cada N días,
   sin mirar lo que se mide, y adelantado si la CE baja. Lo ejecuta el motor; acá se ve cuándo fue la última
   aplicación y cuándo toca la próxima, y quien tiene `configuracion.editar` fija el intervalo de la zona.

   Durante la rustificación conviene dar menos nitrógeno: el plan de rustificación no cambia el intervalo solo,
   lo alarga el agrónomo desde acá.
   ============================================================ */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Card } from '@/components/ui/Card';
import { Chip } from '@/components/ui/Chip';
import { Field, InputUnit } from '@/components/ui/Field';
import { getRepository } from '@/data';
import { useAuth } from '@/hooks/AuthContext';
import {
  NUTRITION_INTERVAL_MAX_DAYS,
  NUTRITION_INTERVAL_MIN_DAYS,
  nutritionIntervalError,
  nutritionPlanStatus,
} from '@/lib/nutritionPlan';
import { contar } from '@/lib/plural';
import type { NutritionPlan, Zona } from '@/types/domain';
import styles from './NutritionPlanCard.module.css';

interface NutritionPlanCardProps {
  zona: Zona;
  className?: string;
}

const formatTs = (ts: number) =>
  new Date(ts).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

const messageOf = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** Qué dice el plan hoy: un chip y una frase. */
function StatusLine({ plan }: { plan: NutritionPlan }) {
  const status = nutritionPlanStatus(plan, Date.now());
  switch (status.kind) {
    case 'running':
      return (
        <>
          <Chip tone="info">Aplicando</Chip>
          <span className={styles.detail}>Algún sector espera su ventana de aplicación.</span>
        </>
      );
    case 'advanced':
      return (
        <>
          <Chip tone="warning">Adelantado por CE baja</Chip>
          <span className={styles.detail}>Se aplica en la próxima ventana apta.</span>
        </>
      );
    case 'due-now':
      return (
        <>
          <Chip tone="warning">Vence ya</Chip>
          <span className={styles.detail}>Se aplica en la próxima ventana apta.</span>
        </>
      );
    case 'scheduled':
      return <span className={styles.value}>Próxima: {formatTs(status.dueTs)}</span>;
  }
}

export function NutritionPlanCard({ zona, className }: NutritionPlanCardProps) {
  const auth = useAuth();
  const canEdit = auth.puede('configuracion.editar');
  const id = useId();
  const plan = zona.nutritionPlan;
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  // El snapshot no trae el plan (backend sin él): no hay nada que mostrar.
  if (!plan) return null;

  const error = nutritionIntervalError(text);
  const unchanged = error === null && Number(text) === plan.intervalDays;

  const open = () => {
    setText(String(plan.intervalDays));
    setSaveError(null);
    setEditing(true);
  };

  const close = () => {
    setEditing(false);
    setText('');
    setSaveError(null);
  };

  const submit = () => {
    if (error || unchanged) return;
    setBusy(true);
    setSaveError(null);
    getRepository()
      .setNutritionPlanInterval(zona.id, Number(text))
      .then(close)
      .catch((e: unknown) => setSaveError(messageOf(e)))
      .finally(() => setBusy(false));
  };

  return (
    <Card className={className ? `${styles.card} ${className}` : styles.card}>
      <div className={styles.head}>
        <h2 className={styles.title}>Plan de nutrición</h2>
        <span className={styles.meta}>Fertilizante foliar en todos los sectores de la macro-zona</span>
      </div>

      <dl className={styles.list}>
        <div className={styles.row}>
          <dt className={styles.name}>Intervalo</dt>
          <dd className={styles.dd}>
            <span className={styles.value}>Cada {contar(plan.intervalDays, 'día', 'días')}</span>
            <Chip tone="line">{plan.customInterval ? 'Propio de la zona' : 'General'}</Chip>
          </dd>
        </div>
        <div className={styles.row}>
          <dt className={styles.name}>Última aplicación</dt>
          <dd className={styles.dd}>
            <span className={styles.value}>
              {plan.lastApplicationTs != null ? formatTs(plan.lastApplicationTs) : 'Todavía no se aplicó'}
            </span>
          </dd>
        </div>
        <div className={styles.row}>
          <dt className={styles.name}>Estado</dt>
          <dd className={styles.dd}>
            <StatusLine plan={plan} />
          </dd>
        </div>
      </dl>

      {canEdit &&
        (editing ? (
          <form
            className={styles.form}
            aria-label="Intervalo del plan de nutrición"
            onSubmit={(e) => {
              e.preventDefault();
              submit();
            }}
          >
            <Field
              label="Días entre aplicaciones"
              htmlFor={`${id}-intervalo`}
              hint={`Entre ${NUTRITION_INTERVAL_MIN_DAYS} y ${NUTRITION_INTERVAL_MAX_DAYS}. Alargalo durante la rustificación para dar menos nitrógeno.`}
              error={text.trim() !== '' && error ? error : undefined}
            >
              <InputUnit
                id={`${id}-intervalo`}
                unit="días"
                inputMode="numeric"
                value={text}
                autoFocus
                invalid={error !== null}
                onChange={(e) => setText(e.target.value)}
              />
            </Field>
            {saveError && (
              <Callout tone="crit" role="alert">
                {saveError}
              </Callout>
            )}
            <div className={styles.actions}>
              <Button type="submit" variant="primary" disabled={busy || error !== null || unchanged}>
                Guardar intervalo
              </Button>
              <Button disabled={busy} onClick={close}>
                Cancelar
              </Button>
            </div>
          </form>
        ) : (
          <div className={styles.actions}>
            <Button onClick={open}>Cambiar intervalo</Button>
          </div>
        ))}
    </Card>
  );
}
