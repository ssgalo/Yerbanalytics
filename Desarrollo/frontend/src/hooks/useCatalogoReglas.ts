/* ============================================================
   Hook del catálogo de parámetros del motor de reglas. Obtiene el catálogo del repositorio
   (mock o http) y expone el guardado en lote, sin que la vista conozca el origen.
   ============================================================ */
import { useCallback, useEffect, useState } from 'react';
import { getRepository } from '@/data';
import type { CambioEstadoRegla, CambioParametro, CatalogoReglas, ReglaCatalogo } from '@/types/domain';

interface UseCatalogoReglasResult {
  catalogo: CatalogoReglas | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Vuelve a pedir el catálogo (p. ej. tras un error de carga). */
  reload: () => void;
  /**
   * Guarda los cambios, todo o nada. Resuelve con el catálogo actualizado o rechaza (con
   * `ParametrosInvalidosError` si el servidor rechazó valores).
   */
  save: (cambios: CambioParametro[]) => Promise<CatalogoReglas>;
  /**
   * Prende o apaga una pieza en el momento. Reemplaza sólo esa pieza del catálogo (no toca los parámetros que
   * se estén editando) y resuelve con ella; rechaza con `EstadoReglaError` si el backend pide motivo o confirmación.
   */
  setEstado: (nombre: string, cambio: CambioEstadoRegla) => Promise<ReglaCatalogo>;
  /** Nombre propio de una pieza (`null` o vacío vuelve al de fábrica). Reemplaza sólo esa pieza. */
  renombrar: (nombre: string, etiqueta: string | null) => Promise<ReglaCatalogo>;
  /** Apaga una pieza y la saca de la vista. Reemplaza sólo esa pieza. */
  eliminar: (nombre: string) => Promise<ReglaCatalogo>;
  /** Devuelve una pieza eliminada, apagada. Reemplaza sólo esa pieza. */
  restaurar: (nombre: string) => Promise<ReglaCatalogo>;
}

export function useCatalogoReglas(): UseCatalogoReglasResult {
  const [catalogo, setCatalogo] = useState<CatalogoReglas | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [saving, setSaving] = useState(false);
  const [version, setVersion] = useState(0);

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
  }, [version]);

  const reload = useCallback(() => {
    setError(null);
    setLoading(true);
    setVersion((v) => v + 1);
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

  /** Aplica el resultado de una acción sobre una pieza, sin tocar los parámetros que se estén editando. */
  const reemplazar = useCallback((pieza: ReglaCatalogo): ReglaCatalogo => {
    setCatalogo((c) => (c ? { ...c, reglas: c.reglas.map((r) => (r.id === pieza.id ? pieza : r)) } : c));
    return pieza;
  }, []);

  const setEstado = useCallback(
    async (nombre: string, cambio: CambioEstadoRegla) =>
      reemplazar(await getRepository().setEstadoRegla(nombre, cambio)),
    [reemplazar],
  );
  const renombrar = useCallback(
    async (nombre: string, etiqueta: string | null) =>
      reemplazar(await getRepository().renombrarRegla(nombre, etiqueta)),
    [reemplazar],
  );
  const eliminar = useCallback(
    async (nombre: string) => reemplazar(await getRepository().eliminarRegla(nombre)),
    [reemplazar],
  );
  const restaurar = useCallback(
    async (nombre: string) => reemplazar(await getRepository().restaurarRegla(nombre)),
    [reemplazar],
  );

  return { catalogo, loading, error, saving, save, setEstado, renombrar, eliminar, restaurar, reload };
}
