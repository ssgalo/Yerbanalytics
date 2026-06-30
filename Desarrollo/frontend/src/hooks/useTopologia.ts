/* ============================================================
   Hook de la topología del vivero (HU-18 CA-01). Obtiene el resumen de la grilla
   desde el repositorio (mock o http) y expone la generación con su estado, sin que la
   vista conozca el origen de los datos.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { DisposicionTopologia, NuevaTopologia, TopologiaVivero } from '@/types/domain';

interface UseTopologiaResult {
  data: TopologiaVivero | null;
  loading: boolean;
  error: Error | null;
  generating: boolean;
  /** Genera (o regenera) la grilla; resuelve con el resumen actualizado o rechaza con el error. */
  generar: (input: NuevaTopologia) => Promise<TopologiaVivero>;
  /** Guarda la disposición visual sin regenerar la grilla; resuelve con el resumen actualizado. */
  guardarDisposicion: (input: DisposicionTopologia) => Promise<TopologiaVivero>;
}

export function useTopologia(): UseTopologiaResult {
  const [data, setData] = useState<TopologiaVivero | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [generating, setGenerating] = useState(false);

  useEffect(() => {
    let active = true;

    getRepository()
      .getTopologia()
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

  const generar = useCallback(async (input: NuevaTopologia): Promise<TopologiaVivero> => {
    setGenerating(true);
    try {
      const updated = await getRepository().generarTopologia(input);
      setData(updated);
      return updated;
    } finally {
      setGenerating(false);
    }
  }, []);

  const guardarDisposicion = useCallback(
    async (input: DisposicionTopologia): Promise<TopologiaVivero> => {
      setGenerating(true);
      try {
        const updated = await getRepository().guardarDisposicion(input);
        setData(updated);
        return updated;
      } finally {
        setGenerating(false);
      }
    },
    [],
  );

  return { data, loading, error, generating, generar, guardarDisposicion };
}
