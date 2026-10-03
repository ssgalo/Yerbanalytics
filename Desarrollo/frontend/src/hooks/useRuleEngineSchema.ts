/* ============================================================
   Hook que obtiene el esquema base del DAG del motor de reglas a través del repositorio
   (GET /api/rules/schema en `http`, esquema derivado del catálogo en `mock`). Se ejecuta
   una sola vez al montarse.
   ============================================================ */
import { useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { DagSchema } from '@/types/domain';

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

    getRepository()
      .getRuleSchema()
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
