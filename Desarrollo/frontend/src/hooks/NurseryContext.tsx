/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Provider + hook del dataset del vivero.

   La fuente sale de `getRepository()`, igual que en todas las demás vistas: una sola
   decisión, tomada por `VITE_DATA_SOURCE` al arrancar. Eso es lo que garantiza que la app
   no muestre nunca datos mock y datos del backend en la misma pantalla.
   ============================================================ */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getRepository } from '@/data';
import type { NurseryData } from '@/types/domain';

const NurseryContext = createContext<NurseryData | null>(null);

export function NurseryProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<NurseryData | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;

    const fetchNursery = () => {
      getRepository()
        .getNursery()
        .then((d) => active && setData(d))
        .catch((e) => active && setError(e instanceof Error ? e : new Error(String(e))));
    };

    fetchNursery();
    const interval = setInterval(fetchNursery, 5000);

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  if (error) {
    return (
      <div style={{ padding: 40, fontFamily: 'var(--font-body)', color: 'var(--crit)' }}>
        No se pudo cargar el vivero: {error.message}
      </div>
    );
  }

  if (!data) {
    return (
      <div style={{ padding: 40, fontFamily: 'var(--font-body)', color: 'var(--muted)' }}>
        Cargando vivero…
      </div>
    );
  }

  return <NurseryContext.Provider value={data}>{children}</NurseryContext.Provider>;
}

/** Acceso al dataset del vivero. Lanza si se usa fuera del provider. */
export function useNurseryData(): NurseryData {
  const ctx = useContext(NurseryContext);
  if (!ctx) {
    throw new Error('useNurseryData debe usarse dentro de <NurseryProvider>');
  }
  return ctx;
}
