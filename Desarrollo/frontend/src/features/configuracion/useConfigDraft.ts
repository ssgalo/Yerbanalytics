/* ============================================================
   Borrador de la configuración agronómica (HU-15), compartido por las vistas Umbrales,
   Rustificación y Preferencias. Cada vista edita su parte, pero el guardado manda la
   configuración entera: es un único recurso en el backend (`PUT /api/configuracion`).
   ============================================================ */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useConfig } from '@/hooks/useConfig';
import type { Configuracion } from '@/types/domain';
import { countChanges } from './changes';

export type Feedback = { kind: 'ok' | 'err'; msg: string } | null;

export interface ConfigDraft {
  config: Configuracion | null;
  /** Null hasta que carga la configuración. */
  draft: Configuracion | null;
  loading: boolean;
  error: Error | null;
  saving: boolean;
  /** Valores cambiados respecto de lo guardado (lo que cuenta la barra de guardado). */
  changes: number;
  feedback: Feedback;
  update: (fn: (d: Configuracion) => Configuracion) => void;
  save: () => Promise<void>;
  /** Vuelve a lo guardado. */
  discard: () => void;
}

export function useConfigDraft(): ConfigDraft {
  const { config, loading, error, saving, save: persist } = useConfig();
  const [draft, setDraft] = useState<Configuracion | null>(null);
  const [feedback, setFeedback] = useState<Feedback>(null);

  // El borrador se reinicia con la config cargada o recién guardada.
  useEffect(() => {
    if (config) setDraft(structuredClone(config));
  }, [config]);

  const changes = useMemo(() => (draft && config ? countChanges(config, draft) : 0), [draft, config]);

  const update = useCallback((fn: (d: Configuracion) => Configuracion) => {
    setDraft((d) => (d ? fn(d) : d));
    setFeedback(null);
  }, []);

  const save = async () => {
    if (!draft) return;
    setFeedback(null);
    try {
      await persist(draft);
      setFeedback({ kind: 'ok', msg: 'Configuración guardada correctamente.' });
    } catch (e) {
      setFeedback({ kind: 'err', msg: e instanceof Error ? e.message : String(e) });
    }
  };

  const discard = () => {
    if (config) setDraft(structuredClone(config));
    setFeedback(null);
  };

  return { config, draft, loading, error, saving, changes, feedback, update, save, discard };
}

/** Mensaje de la barra cuando hay valores para corregir. */
export function pendingFixes(errors: string[]): string | undefined {
  if (errors.length === 0) return undefined;
  return `Hay ${errors.length === 1 ? 'un valor' : `${errors.length} valores`} para corregir antes de guardar.`;
}
