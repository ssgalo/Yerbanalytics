/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Provider + hook del dataset del vivero.

   La fuente sale de `getRepository()`, igual que en todas las demás vistas: una sola
   decisión, tomada por `VITE_DATA_SOURCE` al arrancar. Eso es lo que garantiza que la app
   no muestre nunca datos mock y datos del backend en la misma pantalla.

   Se monta sólo con sesión (dentro de `RequireAuth`), así que sin sesión no se sondea. Si el
   rol no tiene `vivero.ver`, no se pide el snapshot: los hijos se montan sin datos y el shell
   usa `useNurseryDataOpcional()`; las vistas que lo necesitan ya están vedadas por permiso.
   ============================================================ */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getRepository, SesionCerradaError } from '@/data';
import type { NurseryData } from '@/types/domain';

const NurseryContext = createContext<NurseryData | null>(null);

interface NurseryProviderProps {
  children: ReactNode;
  /** false = el rol no puede ver el vivero: no se sondea. Por defecto, true. */
  habilitado?: boolean;
}

export function NurseryProvider({ children, habilitado = true }: NurseryProviderProps) {
  const [data, setData] = useState<NurseryData | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!habilitado) return undefined;
    let active = true;

    const fetchNursery = () => {
      getRepository()
        .getNursery()
        .then((d) => active && setData(d))
        .catch((e) => {
          if (!active) return;
          // Sesión cerrada (401): se dejan de sondear y el AuthProvider ya muestra el login. No es
          // un error del vivero, así que no se pinta como tal.
          if (e instanceof SesionCerradaError) {
            active = false;
            clearInterval(interval);
            return;
          }
          setError(e instanceof Error ? e : new Error(String(e)));
        });
    };

    const interval = setInterval(fetchNursery, 5000);
    fetchNursery();

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, [habilitado]);

  if (!habilitado) return <>{children}</>;

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

/**
 * El dataset del vivero, o null si el rol no puede verlo. Para el shell (topbar, sidebar),
 * que se muestra igual con o sin `vivero.ver`.
 */
export function useNurseryDataOpcional(): NurseryData | null {
  return useContext(NurseryContext);
}

/** Acceso al dataset del vivero. Lanza si se usa fuera del provider. */
export function useNurseryData(): NurseryData {
  const ctx = useContext(NurseryContext);
  if (!ctx) {
    throw new Error('useNurseryData debe usarse dentro de <NurseryProvider>');
  }
  return ctx;
}
