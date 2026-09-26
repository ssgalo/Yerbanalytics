/* ============================================================
   Hook que obtiene el esquema base del DAG del motor de reglas
   desde GET /api/rules/schema. Se ejecuta una sola vez al montarse.
   ============================================================ */
import { useEffect, useState } from 'react';
import type { DagSchema } from '@/types/domain';

/**
 * VITE_API_BASE_URL ya incluye el prefijo /api (ej: http://localhost:8000/api).
 * Por eso la ruta del endpoint es /rules/schema, sin repetir /api.
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

interface UseRuleEngineSchemaResult {
  schema: DagSchema | null;
  loading: boolean;
  error: Error | null;
}

export function useRuleEngineSchema(): UseRuleEngineSchemaResult {
  const [schema, setSchema] = useState<DagSchema | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;

    fetch(`${BASE_URL}/rules/schema`)
      .then((res) => {
        if (!res.ok) throw new Error(`Error ${res.status} al obtener el esquema del motor`);
        return res.json() as Promise<DagSchema>;
      })
      .then((data) => {
        if (active) {
          setSchema(data);
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

  return { schema, loading, error };
}
