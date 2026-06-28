/* ============================================================
   Hook de la configuración agronómica (HU-15). Obtiene la config
   desde el repositorio (mock o http) y expone un guardado con su
   estado, sin que la vista conozca el origen de los datos.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { Configuracion } from '@/types/domain';

interface UseConfigResult {
  config: Configuracion | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Guarda la configuración; resuelve con la versión persistida o rechaza con el error. */
  save: (cfg: Configuracion) => Promise<Configuracion>;
}

export function useConfig(): UseConfigResult {
  const [config, setConfig] = useState<Configuracion | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    getRepository()
      .getConfig()
      .then((c) => {
        if (active) {
          setConfig(c);
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

  const save = useCallback(async (cfg: Configuracion): Promise<Configuracion> => {
    setSaving(true);
    try {
      const saved = await getRepository().saveConfig(cfg);
      setConfig(saved);
      return saved;
    } finally {
      setSaving(false);
    }
  }, []);

  return { config, loading, error, saving, save };
}
