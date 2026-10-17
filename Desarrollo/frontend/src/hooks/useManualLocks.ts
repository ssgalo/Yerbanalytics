/* ============================================================
   Bloqueos manuales vigentes (HU-19). Trae la lista al montar, la refresca cada 15 s —otra
   persona pudo bloquear o reanudar— y expone bloquear/reanudar, que recargan al terminar.

   Un rechazo del backend (`ManualLockError`: motivo vacío, ya bloqueado…) se propaga a quien
   llama, que lo muestra junto al formulario; no queda como error de la consulta.
   ============================================================ */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getRepository } from '@/data';
import type { ManualLock, NewManualLock } from '@/types/domain';

const REFRESH_MS = 15_000;

export interface ManualLocksState {
  locks: ManualLock[];
  loading: boolean;
  /** Falló la consulta (no un rechazo de una acción). */
  error: Error | null;
  /** Hay un bloqueo o una reanudación en vuelo. */
  busy: boolean;
  reload: () => void;
  activate: (lock: NewManualLock) => Promise<void>;
  release: (id: number) => Promise<void>;
}

/** `enabled: false` no consulta nada: la vista no tiene permiso para leer bloqueos. */
export function useManualLocks(enabled = true): ManualLocksState {
  const [locks, setLocks] = useState<ManualLock[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<Error | null>(null);
  const [busy, setBusy] = useState(false);
  // Cada pedido lleva un número; sólo el último puede escribir el estado.
  const request = useRef(0);

  const load = useCallback(() => {
    const mine = ++request.current;
    getRepository()
      .getManualLocks()
      .then((l) => {
        if (mine !== request.current) return;
        setLocks(l);
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
    load();
    const timer = setInterval(load, REFRESH_MS);
    return () => {
      clearInterval(timer);
      discardInFlight();
    };
  }, [enabled, load, discardInFlight]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    try {
      await action();
      load();
    } finally {
      setBusy(false);
    }
  };

  return {
    locks,
    loading,
    error,
    busy,
    reload: load,
    activate: (lock) => run(() => getRepository().activateManualLock(lock)),
    release: (id) => run(() => getRepository().releaseManualLock(id)),
  };
}
