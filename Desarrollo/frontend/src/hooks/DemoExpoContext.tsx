/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Visibilidad de la pestaña "Demo Expo". Vive en un contexto (y no en el Sidebar ni en la
   página de Configuración) porque la leen dos lugares que no se conocen: el interruptor
   guarda y el menú reacciona al instante, sin recargar.
   ============================================================ */
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { getRepository } from '@/data';

interface DemoExpoValue {
  visible: boolean;
  /** true hasta que terminó la primera lectura. */
  cargando: boolean;
  /** Guarda y refleja lo que devolvió el repositorio. Si falla, rechaza y no cambia nada. */
  cambiar: (visible: boolean) => Promise<void>;
}

const DemoExpoContext = createContext<DemoExpoValue | null>(null);

interface DemoExpoProviderProps {
  children: ReactNode;
  /**
   * false = el rol no puede leer la preferencia (`vivero.ver`): no se pide y la pestaña queda
   * oculta. Por defecto, true.
   */
  habilitado?: boolean;
}

export function DemoExpoProvider({ children, habilitado = true }: DemoExpoProviderProps) {
  const [visible, setVisible] = useState(false);
  const [cargando, setCargando] = useState(habilitado);

  useEffect(() => {
    if (!habilitado) return undefined;
    let activo = true;
    getRepository()
      .getDemoExpo()
      .then((v) => activo && setVisible(v))
      // Sin lectura no se muestra la pestaña: una sección de demo no tiene por qué molestar.
      .catch(() => activo && setVisible(false))
      .finally(() => activo && setCargando(false));
    return () => {
      activo = false;
    };
  }, [habilitado]);

  const cambiar = useCallback(async (nuevo: boolean) => {
    setVisible(await getRepository().setDemoExpo(nuevo));
  }, []);

  const value = useMemo(() => ({ visible, cargando, cambiar }), [visible, cargando, cambiar]);
  return <DemoExpoContext.Provider value={value}>{children}</DemoExpoContext.Provider>;
}

/** Acceso a la visibilidad de Demo Expo. Lanza si se usa fuera del provider. */
export function useDemoExpo(): DemoExpoValue {
  const ctx = useContext(DemoExpoContext);
  if (!ctx) {
    throw new Error('useDemoExpo debe usarse dentro de <DemoExpoProvider>');
  }
  return ctx;
}
