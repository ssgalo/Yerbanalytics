/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Provider + hook del dataset del vivero.

   La fuente del vivero sigue el MODO DE OPERACIÓN persistido en el backend
   (fuente de verdad compartida con el simulador :5180):
     - estático   → repositorio mock (demo hardcodeada de 6 macro-zonas);
     - simulación → repositorio http (vivero real: topología regenerable + MQTT).
   El modo se lee al montar (se respeta al refrescar). Si no se puede leer, degrada a
   estático para que la demo siempre cargue.
   ============================================================ */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { getHttpRepository, getMockRepository, type DataRepository } from '@/data';
import type { NurseryData } from '@/types/domain';

const NurseryContext = createContext<NurseryData | null>(null);

export function NurseryProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<NurseryData | null>(null);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let active = true;
    let interval: ReturnType<typeof setInterval> | undefined;

    const fetchFrom = (repo: DataRepository) => {
      repo
        .getNursery()
        .then((d) => active && setData(d))
        .catch((e) => active && setError(e instanceof Error ? e : new Error(String(e))));
    };

    // Determina la fuente según el modo persistido; ante cualquier fallo, demo estática.
    getHttpRepository()
      .getSimulacionEstado()
      .then((estado) => (estado.modo === 'simulacion' ? getHttpRepository() : getMockRepository()))
      .catch(() => getMockRepository())
      .then((repo) => {
        if (!active) return;
        fetchFrom(repo);
        interval = setInterval(() => fetchFrom(repo), 5000);
      });

    return () => {
      active = false;
      if (interval) clearInterval(interval);
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
