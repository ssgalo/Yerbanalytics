/* Plan de rustificación vigente de una macro-zona: carga, alta y cancelación. */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { NuevoPlanRustificacion, PlanRustificacion } from '@/types/domain';

export interface HardeningPlanState {
  plan: PlanRustificacion | null;
  loading: boolean;
  error: Error | null;
  /** Hay un alta o una cancelación en vuelo. */
  busy: boolean;
  reload: () => void;
  /** Resuelve con el plan creado; rechaza con el error del backend (la vista lo muestra). */
  start: (plan: NuevoPlanRustificacion) => Promise<PlanRustificacion>;
  cancel: () => Promise<void>;
}

export function useHardeningPlan(zonaId: string): HardeningPlanState {
  const [plan, setPlan] = useState<PlanRustificacion | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    getRepository()
      .getPlanRustificacion(zonaId)
      .then((p) => active && setPlan(p))
      .catch((e) => active && setError(e instanceof Error ? e : new Error(String(e))))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, [zonaId, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  const start = async (nuevo: NuevoPlanRustificacion) => {
    setBusy(true);
    try {
      const creado = await getRepository().iniciarPlanRustificacion(zonaId, nuevo);
      setPlan(creado);
      return creado;
    } finally {
      setBusy(false);
    }
  };

  const cancel = async () => {
    setBusy(true);
    try {
      await getRepository().cancelarPlanRustificacion(zonaId);
      // Cancelado ya no rige: la zona vuelve a no tener plan.
      setPlan(null);
    } finally {
      setBusy(false);
    }
  };

  return { plan, loading, error, busy, reload, start, cancel };
}
