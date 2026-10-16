/* ============================================================
   Borradores de las vistas de Configuración.

   `useResourceDraft` es el borrador genérico de un recurso que se guarda entero: lo que se edita,
   cuántos cambios hay respecto de lo guardado, guardar y descartar. Lo usan:

   - `useConfigDraft`: la configuración agronómica (HU-15), compartida por las vistas Umbrales,
     Rustificación y Preferencias. Cada vista edita su parte, pero el guardado manda la
     configuración entera: es un único recurso en el backend (`PUT /api/configuracion`).
   - `useDiagnosisCriteriaDraft`: el criterio de diagnóstico, que es otro recurso
     (`PUT /api/configuracion/diagnostico`).
   ============================================================ */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useConfig } from '@/hooks/useConfig';
import type { Configuracion } from '@/types/domain';
import { countChanges } from './changes';

export type Feedback = { kind: 'ok' | 'err'; msg: string } | null;

export interface ConfigDraft<T = Configuracion> {
  /** Lo guardado. */
  config: T | null;
  /** Null hasta que carga el recurso. */
  draft: T | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Valores cambiados respecto de lo guardado (lo que cuenta la barra de guardado). */
  changes: number;
  feedback: Feedback;
  update: (fn: (d: T) => T) => void;
  save: () => Promise<void>;
  /** Vuelve a lo guardado. */
  discard: () => void;
}

/** De dónde sale y adónde va el recurso (un hook de datos como `useConfig`). */
export interface DraftSource<T> {
  data: T | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Guarda; resuelve con la versión persistida o rechaza con el error a mostrar. */
  save: (value: T) => Promise<T>;
}

export function useResourceDraft<T>(
  source: DraftSource<T>,
  count: (saved: T, draft: T) => number,
  savedMessage: string,
): ConfigDraft<T> {
  const { data, loading, error, saving, save: persist } = source;
  const [draft, setDraft] = useState<T | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);

  // El borrador se reinicia con lo cargado o recién guardado.
  useEffect(() => {
    if (data) setDraft(structuredClone(data));
  }, [data]);

  const changes = useMemo(() => (draft && data ? count(data, draft) : 0), [draft, data, count]);

  const update = useCallback((fn: (d: T) => T) => {
    setDraft((d) => (d ? fn(d) : d));
    setFeedback(null);
  }, []);

  const save = async () => {
    if (!draft) return;
    setFeedback(null);
    try {
      await persist(draft);
      setFeedback({ kind: 'ok', msg: savedMessage });
    } catch (e) {
      setFeedback({ kind: 'err', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  const discard = () => {
    if (data) setDraft(structuredClone(data));
    setFeedback(null);
  };

  return { config: data, draft, loading, error, saving, changes, feedback, update, save, discard };
}

export function useConfigDraft(): ConfigDraft<Configuracion> {
  const { config, loading, error, saving, save } = useConfig();
  return useResourceDraft(
    { data: config, loading, error, saving, save },
    countChanges,
    'Configuración guardada correctamente.',
  );
}

/** Quién y cuándo editó la configuración agronómica por última vez (encabezado de la vista). */
export function configAudit(c: Configuracion): { by: string | null; ts: number | null } {
  return { by: c.operativa.updatedBy, ts: c.operativa.updatedTs };
}

/** Mensaje de la barra cuando hay valores para corregir. */
export function pendingFixes(errors: string[]): string | undefined {
  if (errors.length === 0) return undefined;
  return `Hay ${errors.length === 1 ? 'un valor' : `${errors.length} valores`} para corregir antes de guardar.`;
}
