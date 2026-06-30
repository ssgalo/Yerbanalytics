/* ============================================================
   Hook del estado técnico de la flota (HU-18 / HU-21). Obtiene la flota
   desde el repositorio (mock o http) y expone el alta y el recambio con su
   estado, sin que la vista conozca el origen de los datos.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { HardwareData, NuevoDispositivo } from '@/types/domain';

interface UseHardwareResult {
  data: HardwareData | null;
  loading: boolean;
  error: Error | null;
  mutating: boolean;
  /** Da de alta un dispositivo; resuelve con la flota actualizada o rechaza con el error. */
  register: (device: NuevoDispositivo) => Promise<HardwareData>;
  /** Recambia un dispositivo reutilizando su registro (HU-21 CA-05). */
  replace: (id: string, device: NuevoDispositivo) => Promise<HardwareData>;
}

export function useHardware(): UseHardwareResult {
  const [data, setData] = useState<HardwareData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [mutating, setMutating] = useState(false);

  useEffect(() => {
    let active = true;

    getRepository()
      .getHardware()
      .then((d) => {
        if (active) {
          setData(d);
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

  const register = useCallback(async (device: NuevoDispositivo): Promise<HardwareData> => {
    setMutating(true);
    try {
      const updated = await getRepository().registerDevice(device);
      setData(updated);
      return updated;
    } finally {
      setMutating(false);
    }
  }, []);

  const replace = useCallback(async (id: string, device: NuevoDispositivo): Promise<HardwareData> => {
    setMutating(true);
    try {
      const updated = await getRepository().replaceDevice(id, device);
      setData(updated);
      return updated;
    } finally {
      setMutating(false);
    }
  }, []);

  return { data, loading, error, mutating, register, replace };
}
