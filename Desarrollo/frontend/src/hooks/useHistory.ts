/* ============================================================
   Hook del historial de acciones. Obtiene la lista desde el
   repositorio (mock o http) sin que la vista conozca el origen.

   El recorte (filtros y tope) lo resuelve el origen, nunca el
   navegador: el historial real acumula cientos de miles de
   eventos `Info` y bajarlo entero cuelga la pestaña y el backend.
   ============================================================ */
import { useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { FiltroHistorial } from '@/data/repository';
import type { ActionRecord } from '@/types/domain';

interface UseHistoryResult {
  records: ActionRecord[];
  loading: boolean;
  error: Error | null;
}

export function useHistory(filtro: FiltroHistorial = {}): UseHistoryResult {
  const [records, setRecords] = useState<ActionRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  // Se desarma en primitivos: el llamador suele pasar un objeto literal nuevo en cada render.
  const { tipo, sector, desde, hasta, limit } = filtro;

  useEffect(() => {
    let active = true;
    setLoading(true);

    getRepository()
      .getHistory({ tipo, sector, desde, hasta, limit })
      .then((r) => {
        if (active) {
          setRecords(r);
          setError(null);
          setLoading(false);
        }
      })
      .catch((e) => {
        if (active) {
          setError(e instanceof Error ? e : new Error(String(e)));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [tipo, sector, desde, hasta, limit]);

  return { records, loading, error };
}
