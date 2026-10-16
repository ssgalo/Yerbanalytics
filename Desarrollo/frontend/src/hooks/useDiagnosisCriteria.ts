/* ============================================================
   Hook del criterio de diagnóstico (bandas de severidad y orden de criticidad). Lo obtiene del
   repositorio (mock o http) y expone el guardado con su estado, sin que la vista conozca el
   origen de los datos. Mismo contrato que `useConfig`.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { DiagnosisCriteria } from '@/types/domain';

interface UseDiagnosisCriteriaResult {
  criteria: DiagnosisCriteria | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Guarda el criterio completo; resuelve con el guardado o rechaza con el motivo a mostrar. */
  save: (criteria: DiagnosisCriteria) => Promise<DiagnosisCriteria>;
}

export function useDiagnosisCriteria(): UseDiagnosisCriteriaResult {
  const [criteria, setCriteria] = useState<DiagnosisCriteria | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;
    getRepository()
      .getDiagnosisCriteria()
      .then((c) => {
        if (active) {
          setCriteria(c);
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

  const save = useCallback(async (next: DiagnosisCriteria): Promise<DiagnosisCriteria> => {
    setSaving(true);
    try {
      const saved = await getRepository().saveDiagnosisCriteria(next);
      setCriteria(saved);
      return saved;
    } finally {
      setSaving(false);
    }
  }, []);

  return { criteria, loading, error, saving, save };
}
