/* eslint-disable react-refresh/only-export-components -- provider + hook colocados a propósito */
/* ============================================================
   Sesión del usuario (HU-01): perfil, permisos, login, logout y motivo del último cierre.

   Envuelve a toda la app y existe ANTES que los datos del vivero: el `NurseryProvider` y el
   `DemoExpoProvider` se montan recién con sesión, así que sin sesión no se sondea nada.

   Escucha a la capa de datos (`escucharSesion`): cualquier 401 —de un sondeo, de un guardado—
   cierra la sesión acá y el router muestra el login con el aviso que corresponda; un 403
   inesperado recarga el perfil (la matriz pudo cambiar mientras el usuario navegaba).
   ============================================================ */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { escucharSesion, getSeguridadRepository } from '@/data';
import type { MotivoCierre, PerfilSesion, Permiso } from '@/types/seguridad';

export type EstadoSesion = 'cargando' | 'anonimo' | 'autenticado';

/** Motivos de cierre que merecen un aviso en el login. `SIN_SESION` no lleva ninguno. */
export type MotivoAviso = Exclude<MotivoCierre, 'SIN_SESION'>;

export interface AuthValue {
  estado: EstadoSesion;
  perfil: PerfilSesion | null;
  /** Por qué se cerró la última sesión, si fue el sistema y no el usuario. */
  motivoCierre: MotivoAviso | null;
  /** ¿El rol del usuario tiene este permiso? Sin sesión, nunca. */
  puede: (permiso: Permiso) => boolean;
  /** Rechaza con `CredencialesIncorrectasError` (u otro error) sin tocar el estado. */
  login: (username: string, clave: string) => Promise<PerfilSesion>;
  logout: () => Promise<void>;
  /** Cambio de clave propio; al terminar recarga el perfil (ya sin clave temporal). */
  cambiarClave: (actual: string, nueva: string) => Promise<void>;
  /** Vuelve a pedir el perfil: permisos e inactividad vigentes. */
  recargarPerfil: () => Promise<void>;
  /** El temporizador local de inactividad venció: cierra sin esperar al próximo sondeo. */
  cerrarPorInactividad: () => void;
}

export const AuthContext = createContext<AuthValue | null>(null);

interface Sesion {
  estado: EstadoSesion;
  perfil: PerfilSesion | null;
  motivo: MotivoAviso | null;
}

/** Un 403 inesperado recarga el perfil, pero no más de una vez cada tantos ms (varios sondeos pueden fallar juntos). */
const RECARGA_MINIMA_MS = 5000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sesion, setSesion] = useState<Sesion>({ estado: 'cargando', perfil: null, motivo: null });
  const ultimaRecarga = useRef(0);

  const cerrarLocal = useCallback((motivo: MotivoCierre) => {
    setSesion((prev) => {
      // Ya cerrada: un 401 "sin sesión" tardío (un sondeo que estaba en vuelo) no pisa el aviso.
      if (prev.estado === 'anonimo' && motivo === 'SIN_SESION') return prev;
      return { estado: 'anonimo', perfil: null, motivo: motivo === 'SIN_SESION' ? null : motivo };
    });
  }, []);

  const recargarPerfil = useCallback(async () => {
    ultimaRecarga.current = Date.now();
    try {
      const perfil = await getSeguridadRepository().getPerfil();
      setSesion({ estado: 'autenticado', perfil, motivo: null });
    } catch {
      // Un 401 ya cerró la sesión por el canal de eventos; otro error deja el perfil como estaba.
    }
  }, []);

  useEffect(() => {
    let activo = true;
    const dejarDeEscuchar = escucharSesion({
      sesionCerrada: (motivo) => activo && cerrarLocal(motivo),
      permisoDenegado: () => {
        if (activo && Date.now() - ultimaRecarga.current > RECARGA_MINIMA_MS) void recargarPerfil();
      },
      cambioClaveRequerido: () =>
        activo &&
        setSesion((prev) => (prev.perfil ? { ...prev, perfil: { ...prev.perfil, debeCambiarClave: true } } : prev)),
    });

    getSeguridadRepository()
      .getPerfil()
      .then((perfil) => activo && setSesion({ estado: 'autenticado', perfil, motivo: null }))
      // Sin sesión (o sin backend): al login. El motivo, si lo hubo, ya llegó por el canal.
      .catch(() => activo && setSesion((prev) => (prev.estado === 'cargando' ? { ...prev, estado: 'anonimo' } : prev)));

    return () => {
      activo = false;
      dejarDeEscuchar();
    };
  }, [cerrarLocal, recargarPerfil]);

  const login = useCallback(async (username: string, clave: string) => {
    const perfil = await getSeguridadRepository().login(username, clave);
    setSesion({ estado: 'autenticado', perfil, motivo: null });
    return perfil;
  }, []);

  const logout = useCallback(async () => {
    await getSeguridadRepository().logout();
    setSesion({ estado: 'anonimo', perfil: null, motivo: null });
  }, []);

  const cambiarClave = useCallback(
    async (actual: string, nueva: string) => {
      await getSeguridadRepository().cambiarClave(actual, nueva);
      await recargarPerfil();
    },
    [recargarPerfil],
  );

  const cerrarPorInactividad = useCallback(() => {
    cerrarLocal('SESION_EXPIRADA');
    // Mejor esfuerzo: si el backend ya la había dado por vencida, el resultado es el mismo.
    void getSeguridadRepository().logout().catch(() => undefined);
  }, [cerrarLocal]);

  const permisos = useMemo(() => new Set(sesion.perfil?.permisos ?? []), [sesion.perfil]);
  const puede = useCallback((permiso: Permiso) => permisos.has(permiso), [permisos]);

  const value = useMemo<AuthValue>(
    () => ({
      estado: sesion.estado,
      perfil: sesion.perfil,
      motivoCierre: sesion.motivo,
      puede,
      login,
      logout,
      cambiarClave,
      recargarPerfil,
      cerrarPorInactividad,
    }),
    [sesion, puede, login, logout, cambiarClave, recargarPerfil, cerrarPorInactividad],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

/** Acceso a la sesión. Lanza si se usa fuera del provider. */
export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth debe usarse dentro de <AuthProvider>');
  }
  return ctx;
}
