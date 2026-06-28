/* ============================================================
   Hook del historial de acciones. Obtiene la lista desde el
   repositorio (mock o http) sin que la vista conozca el origen.
   ============================================================ */
import { useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { ActionRecord } from '@/types/domain';

interface UseHistoryResult {
  records: ActionRecord[];
  loading: boolean;
  error: Error | null;
}

export function useHistory(): UseHistoryResult {
  const [records, setRecords] = useState<ActionRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;

    getRepository()
      .getHistory()
      .then((r) => {
        if (active) {
          setRecords(r);
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
  }, []);

  return { records, loading, error };
}
