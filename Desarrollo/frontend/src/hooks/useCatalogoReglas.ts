/* ============================================================
   Hook del catálogo de parámetros del motor de reglas. Obtiene el catálogo del repositorio
   (mock o http) y expone el guardado en lote, sin que la vista conozca el origen.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { CambioParametro, CatalogoReglas } from '@/types/domain';

interface UseCatalogoReglasResult {
  catalogo: CatalogoReglas | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /**
   * Guarda los cambios, todo o nada. Resuelve con el catálogo actualizado o rechaza (con
   * `ParametrosInvalidosError` si el servidor rechazó valores).
   */
  save: (cambios: CambioParametro[]) => Promise<CatalogoReglas>;
}

export function useCatalogoReglas(): UseCatalogoReglasResult {
  const [catalogo, setCatalogo] = useState<CatalogoReglas | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let active = true;

    getRepository()
      .getCatalogoReglas()
      .then((c) => {
        if (active) {
          setCatalogo(c);
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

  const save = useCallback(async (cambios: CambioParametro[]): Promise<CatalogoReglas> => {
    setSaving(true);
    try {
      const guardado = await getRepository().saveParametros(cambios);
      setCatalogo(guardado);
      return guardado;
    } finally {
      setSaving(false);
    }
  }, []);

  return { catalogo, loading, error, saving, save };
}
