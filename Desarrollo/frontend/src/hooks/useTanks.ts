/* ============================================================
   Tanques de insumo (reglas_v2 §8.3). Trae la lista al montar, la refresca cada 15 s —cada aplicación
   descuenta del tanque y otra persona pudo cargarlo— y expone la carga, que recarga al terminar.

   Un rechazo del backend (`TankLoadError`: la carga no entra, falta la capacidad…) se propaga a quien
   llama, que lo muestra junto al formulario; no queda como error de la consulta.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository } from '@/data';
import type { NewTankLoad, Supply, TankLevel } from '@/types/domain';

const REFRESH_MS = 15_000;

export interface TanksState {
  tanks: TankLevel[];
  loading: boolean;
  /** Falló la consulta (no un rechazo de una carga). */
  error: Error | null;
  /** Hay una carga en vuelo. */
  busy: boolean;
  reload: () => void;
  load: (zoneId: string, supply: Supply, load: NewTankLoad) => Promise<void>;
}

/** `enabled: false` no consulta nada: la vista no tiene permiso para leer los tanques. */
export function useTanks(enabled = true): TanksState {
  const [tanks, setTanks] = useState<TankLevel[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  // Cada pedido lleva un número; sólo el último puede escribir el estado.
  const request = useRef(0);

  const fetchTanks = useCallback(() => {
    const mine = ++request.current;
    getRepository()
      .getTanks()
      .then((t) => {
        if (mine !== request.current) return;
        setTanks(t);
        setError(null);
      })
      .catch((e: unknown) => {
        if (mine === request.current) setError(e instanceof Error ? e : new Error(String(e)));
      })
      .finally(() => {
        if (mine === request.current) setLoading(false);
      });
  }, []);

  // Descarta lo que esté en vuelo: una respuesta tardía no escribe en un componente desmontado.
  const discardInFlight = useCallback(() => {
    request.current++;
  }, []);

  useEffect(() => {
    if (!enabled) return undefined;
    fetchTanks();
    const timer = setInterval(fetchTanks, REFRESH_MS);
    return () => {
      clearInterval(timer);
      discardInFlight();
    };
  }, [enabled, fetchTanks, discardInFlight]);

  const load = async (zoneId: string, supply: Supply, body: NewTankLoad) => {
    setBusy(true);
    try {
      await getRepository().loadTank(zoneId, supply, body);
      fetchTanks();
    } finally {
      setBusy(false);
    }
  };

  return { tanks, loading, error, busy, reload: fetchTanks, load };
}
