/* ============================================================
   Tanques de insumo de una macro-zona (reglas_v2 §8.3): un tanque de fertilizante y uno de fitosanitario.
   Cada aplicación descuenta del tanque; con el nivel bajo el aviso se pide reponer y con el tanque vacío no se
   aplica. Un tanque que nadie registró figura "Sin registrar": su stock es desconocido, no está lleno.

   Quien tiene `insumos.ver` ve el stock; quien tiene `insumos.gestionar` registra una carga. La primera carga de
   un tanque declara además su capacidad.
   ============================================================ */
import { useId, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Callout } from '@/components/ui/Callout';
import { Card } from '@/components/ui/Card';
import { Chip } from '@/components/ui/Chip';
import { Field, InputUnit } from '@/components/ui/Field';
import { ProgressBar } from '@/components/ui/ProgressBar';
import { Skeleton } from '@/components/ui/EstadosPantalla';
import { linkClass } from '@/components/ui/clases';
import { useAuth } from '@/hooks/AuthContext';
import { useTanks } from '@/hooks/useTanks';
import type { Supply, TankLevel } from '@/types/domain';
import styles from './TankCard.module.css';

interface TankCardProps {
  zoneId: string;
  className?: string;
}

const SUPPLIES: { supply: Supply; label: string }[] = [
  { supply: 'FERTILIZER', label: 'Fertilizante' },
  { supply: 'FUNGICIDE', label: 'Fitosanitario' },
];

const liters = (ml: number) => (ml / 1000).toLocaleString('es-AR', { maximumFractionDigits: 1 });

const formatTs = (ts: number) =>
  new Date(ts).toLocaleString('es-AR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

const messageOf = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** El color de la barra sale del nivel: vacío es crítico, bajo el aviso es advertencia. */
const barColor = (tank: TankLevel) =>
  tank.levelMl === 0 ? 'var(--crit)' : tank.low ? 'var(--warn)' : 'var(--ok)';

/** "12,5" → 12.5; vacío o ilegible → NaN. Acepta la coma decimal del teclado en español. */
const parseLiters = (text: string) => Number(text.trim().replace(',', '.'));

export function TankCard({ zoneId, className }: TankCardProps) {
  const auth = useAuth();
  const canSee = auth.puede('insumos.ver');
  const canManage = auth.puede('insumos.gestionar');
  const { tanks, loading, error, busy, reload, load } = useTanks(canSee);
  const id = useId();
  const [editingSupply, setEditingSupply] = useState<Supply | null>(null);
  const [litersText, setLitersText] = useState('');
  const [capacityText, setCapacityText] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  // Sin permiso de lectura no hay nada que mostrar (ni se consulta).
  if (!canSee) return null;

  const close = () => {
    setEditingSupply(null);
    setLitersText('');
    setCapacityText('');
    setActionError(null);
  };

  const submit = (supply: Supply, registered: boolean) => {
    setActionError(null);
    const amount = parseLiters(litersText);
    const capacity = parseLiters(capacityText);
    load(zoneId, supply, {
      liters: amount,
      ...(!registered || capacityText.trim() ? { capacityLiters: capacity } : {}),
    })
      .then(close)
      .catch((e: unknown) => setActionError(messageOf(e)));
  };

  const body = (() => {
    if (loading) return <Skeleton height={96} />;
    if (error) {
      return (
        <Callout tone="crit" role="alert">
          No se pudieron leer los tanques: {error.message}{' '}
          <button type="button" className={linkClass} onClick={reload}>
            Reintentar
          </button>
        </Callout>
      );
    }
    return (
      <ul className={styles.list}>
        {SUPPLIES.map(({ supply, label }) => {
          const tank = tanks.find((t) => t.zoneId === zoneId && t.supply === supply);
          const editing = editingSupply === supply;
          const amount = parseLiters(litersText);
          const invalid = !(amount > 0) || (!tank && !(parseLiters(capacityText) > 0));
          return (
            <li key={supply} className={styles.tank}>
              <div className={styles.row}>
                <span className={styles.name}>{label}</span>
                {tank ? (
                  <>
                    <span className={styles.level}>
                      {liters(tank.levelMl)} L de {liters(tank.capacityMl)} L · {Math.round(tank.levelPct)} %
                    </span>
                    {tank.levelMl === 0 ? (
                      <Chip tone="critical">Vacío</Chip>
                    ) : (
                      tank.low && <Chip tone="warning">Reponer</Chip>
                    )}
                  </>
                ) : (
                  <Chip tone="line">Sin registrar</Chip>
                )}
              </div>
              {tank && (
                <>
                  <ProgressBar value={tank.levelPct} color={barColor(tank)} />
                  <span className={styles.detail}>
                    Último cambio: {formatTs(tank.updatedTs)}
                    {tank.updatedBy && <> · cargó {tank.updatedBy}</>}
                  </span>
                </>
              )}
              {!tank && (
                <span className={styles.detail}>
                  Sin el tanque registrado no se aplica {label.toLowerCase()} en esta macro-zona.
                </span>
              )}
              {canManage &&
                (editing ? (
                  <form
                    className={styles.form}
                    aria-label={`Registrar carga de ${label.toLowerCase()}`}
                    onSubmit={(e) => {
                      e.preventDefault();
                      if (!invalid) submit(supply, !!tank);
                    }}
                  >
                    <Field label="Litros que se cargan" htmlFor={`${id}-${supply}-litros`}>
                      <InputUnit
                        id={`${id}-${supply}-litros`}
                        unit="L"
                        inputMode="decimal"
                        value={litersText}
                        autoFocus
                        onChange={(e) => setLitersText(e.target.value)}
                      />
                    </Field>
                    <Field
                      label="Capacidad del tanque"
                      htmlFor={`${id}-${supply}-capacidad`}
                      hint={tank ? 'Sólo si cambió.' : 'Obligatoria la primera vez.'}
                      optional={!!tank}
                    >
                      <InputUnit
                        id={`${id}-${supply}-capacidad`}
                        unit="L"
                        inputMode="decimal"
                        value={capacityText}
                        onChange={(e) => setCapacityText(e.target.value)}
                      />
                    </Field>
                    {actionError && (
                      <Callout tone="crit" role="alert">
                        {actionError}
                      </Callout>
                    )}
                    <div className={styles.actions}>
                      <Button type="submit" variant="primary" disabled={busy || invalid}>
                        Registrar carga
                      </Button>
                      <Button disabled={busy} onClick={close}>
                        Cancelar
                      </Button>
                    </div>
                  </form>
                ) : (
                  <div className={styles.actions}>
                    <Button
                      aria-label={`${tank ? 'Cargar' : 'Registrar tanque de'} ${label.toLowerCase()}`}
                      onClick={() => setEditingSupply(supply)}
                    >
                      {tank ? 'Cargar' : 'Registrar tanque'}
                    </Button>
                  </div>
                ))}
            </li>
          );
        })}
      </ul>
    );
  })();

  return (
    <Card className={className ? `${styles.card} ${className}` : styles.card}>
      <div className={styles.head}>
        <h2 className={styles.title}>Tanques de insumo</h2>
        <span className={styles.meta}>Stock de fertilizante y fitosanitario de la macro-zona</span>
      </div>
      {body}
    </Card>
  );
}
