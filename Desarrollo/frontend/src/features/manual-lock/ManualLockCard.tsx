/* ============================================================
   Bloqueo manual de la actuación autónoma (HU-19, reglas_v2 S-01), de una macro-zona o de un
   sector. Mientras rige, el motor no ordena riego, insumos ni mediasombra para ese alcance, y al
   bloquear se cierran las válvulas y las bombas.

   Quien tiene `bloqueos.ver` ve el estado; quien tiene `bloqueos.gestionar` puede bloquear (con
   motivo obligatorio) y reanudar. Un sector alcanzado por el bloqueo de su macro-zona lo informa,
   pero se reanuda desde la zona: es un solo bloqueo.
   ============================================================ */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Card } from '@/components/ui/Card';
import { Field, Input } from '@/components/ui/Field';
import { Skeleton } from '@/components/ui/EstadosPantalla';
import { linkClass } from '@/components/ui/clases';
import { useAuth } from '@/hooks/AuthContext';
import { useManualLocks } from '@/hooks/useManualLocks';
import type { ManualLock } from '@/types/domain';
import { locksForScope, type ManualLockScope } from './manualLockScope';
import styles from './ManualLockCard.module.css';

interface ManualLockCardProps extends ManualLockScope {
  /** Cómo se nombra el alcance en los textos: "la macro-zona 2", "el sector MZ-2-014". */
  label: string;
  className?: string;
}

const formatTs = (ts: number) =>
  new Date(ts).toLocaleString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });

const messageOf = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** Detalle del bloqueo: quién, desde cuándo y por qué. */
function LockDetail({ lock }: { lock: ManualLock }) {
  return (
    <p className={styles.detail}>
      <span>
        Bloqueado por <b>{lock.activadoPor}</b> el {formatTs(lock.activadoTs)}
      </span>
      <span>
        Motivo: <b>{lock.motivo}</b>
      </span>
    </p>
  );
}

export function ManualLockCard({ zonaId, sectorId, label, className }: ManualLockCardProps) {
  const auth = useAuth();
  const canSee = auth.puede('bloqueos.ver');
  const canManage = auth.puede('bloqueos.gestionar');
  const { locks, loading, error, busy, reload, activate, release } = useManualLocks(canSee);
  const id = useId();
  const [mode, setMode] = useState<'idle' | 'locking' | 'releasing'>('idle');
  const [reason, setReason] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  // Sin permiso de lectura no hay nada que mostrar (ni se consulta).
  if (!canSee) return null;

  const { own, inheritedFromZone, lockedSectors } = locksForScope(locks, { zonaId, sectorId });
  const trimmed = reason.trim();

  const close = () => {
    setMode('idle');
    setReason('');
    setActionError(null);
  };

  const submitLock = () => {
    setActionError(null);
    activate({ zonaId, ...(sectorId && { sectorId }), motivo: trimmed })
      .then(close)
      .catch((e: unknown) => setActionError(messageOf(e)));
  };

  const submitRelease = (lock: ManualLock) => {
    setActionError(null);
    release(lock.id)
      .then(close)
      .catch((e: unknown) => setActionError(messageOf(e)));
  };

  const body = (() => {
    if (loading) return <Skeleton height={64} />;
    if (error) {
      return (
        <Callout tone="crit" role="alert">
          No se pudo leer el bloqueo manual: {error.message}{' '}
          <button type="button" className={linkClass} onClick={reload}>
            Reintentar
          </button>
        </Callout>
      );
    }
    if (own) {
      return (
        <div className={styles.body}>
          <Callout tone="warn">
            <b>Bloqueado</b> · el motor no actúa sobre {label} hasta que se reanude.
          </Callout>
          <LockDetail lock={own} />
          {actionError && (
            <Callout tone="crit" role="alert">
              {actionError}
            </Callout>
          )}
          {canManage &&
            (mode === 'releasing' ? (
              <div className={styles.actions}>
                <Button variant="primary" disabled={busy} onClick={() => submitRelease(own)}>
                  Confirmar
                </Button>
                <Button disabled={busy} onClick={close}>
                  Cancelar
                </Button>
              </div>
            ) : (
              <div className={styles.actions}>
                <Button onClick={() => setMode('releasing')}>Reanudar</Button>
              </div>
            ))}
        </div>
      );
    }
    return (
      <div className={styles.body}>
        {inheritedFromZone && (
          <>
            <Callout tone="warn">
              La macro-zona {inheritedFromZone.zonaId} está bloqueada: el motor tampoco actúa sobre{' '}
              {label}. Se reanuda desde el detalle de la macro-zona.
            </Callout>
            <LockDetail lock={inheritedFromZone} />
          </>
        )}
        {lockedSectors.length > 0 && (
          <Callout tone="warn">
            {lockedSectors.length === 1
              ? 'Hay un sector bloqueado por separado'
              : `Hay ${lockedSectors.length} sectores bloqueados por separado`}
            : {lockedSectors.map((l) => l.sectorId).join(', ')}.
          </Callout>
        )}
        {mode === 'locking' ? (
          <form
            className={styles.form}
            aria-label={`Bloquear ${label}`}
            onSubmit={(e) => {
              e.preventDefault();
              if (trimmed) submitLock();
            }}
          >
            <Field
              label="Motivo"
              htmlFor={`${id}-motivo`}
              hint="Queda en el historial junto a tu usuario. Al bloquear se cierran las válvulas y las bombas."
            >
              <Input
                id={`${id}-motivo`}
                value={reason}
                maxLength={200}
                autoFocus
                onChange={(e) => setReason(e.target.value)}
              />
            </Field>
            {actionError && (
              <Callout tone="crit" role="alert">
                {actionError}
              </Callout>
            )}
            <div className={styles.actions}>
              <Button type="submit" variant="danger" disabled={busy || !trimmed}>
                Bloquear
              </Button>
              <Button disabled={busy} onClick={close}>
                Cancelar
              </Button>
            </div>
          </form>
        ) : (
          <div className={styles.actions}>
            {canManage ? (
              <Button onClick={() => setMode('locking')}>Bloquear actuación</Button>
            ) : (
              !inheritedFromZone && <span className={styles.meta}>Sin bloqueo vigente.</span>
            )}
          </div>
        )}
      </div>
    );
  })();

  return (
    <Card className={className ? `${styles.card} ${className}` : styles.card}>
      <div className={styles.head}>
        <h2 className={styles.title}>Bloqueo manual</h2>
        <span className={styles.meta}>Suspende la actuación autónoma sobre {label}</span>
      </div>
      {body}
    </Card>
  );
}
