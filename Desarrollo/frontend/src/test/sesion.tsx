/* eslint-disable react-refresh/only-export-components -- ayudas de test, no hay fast refresh */
/* ============================================================
   Ayudas de test para la sesión.

   - `seguridadDemo()`: un `MockSeguridadRepository` nuevo (sin estado compartido entre tests)
     detrás de `getSeguridadRepository()`, con sesión iniciada si se pide.
   - `ConPermisos`: un `AuthContext` fijo para probar una vista suelta con un rol dado.
   La app completa se monta con `montarApp()` (`test/app.tsx`).
   ============================================================ */
import type { ReactNode } from 'react';
import { vi } from 'vitest';
import * as data from '@/data';
import { MockSeguridadRepository } from '@/data/mock/mockSeguridadRepository';
import { AuthContext, type AuthValue } from '@/hooks/AuthContext';
import { MATRIZ_POR_DEFECTO, NOMBRE_ROL } from '@/lib/catalogoSeguridad';
import type { PerfilSesion, Permiso, Rol } from '@/types/seguridad';

export async function seguridadDemo(usuario?: string, ahora?: () => number): Promise<MockSeguridadRepository> {
  const repo = new MockSeguridadRepository(ahora);
  vi.spyOn(data, 'getSeguridadRepository').mockReturnValue(repo);
  if (usuario) await repo.login(usuario, 'demo');
  return repo;
}

export function perfilDe(rol: Rol, permisos?: readonly Permiso[]): PerfilSesion {
  return {
    id: 1,
    username: 'prueba',
    nombre: 'Persona de Prueba',
    rol,
    rolNombre: NOMBRE_ROL[rol],
    permisos: [...(permisos ?? MATRIZ_POR_DEFECTO[rol])],
    inactividadMin: 60,
    debeCambiarClave: false,
  };
}

interface ConPermisosProps {
  /** Rol con su matriz por defecto. */
  rol?: Rol;
  /** O una lista explícita de permisos (pisa la del rol). */
  permisos?: readonly Permiso[];
  children: ReactNode;
}

/** Sesión fija, sin repositorio detrás: para probar cómo una vista respeta los permisos. */
export function ConPermisos({ rol = 'ADMINISTRADOR', permisos, children }: ConPermisosProps) {
  const perfil = perfilDe(rol, permisos);
  const set = new Set(perfil.permisos);
  const value: AuthValue = {
    estado: 'autenticado',
    perfil,
    motivoCierre: null,
    puede: (p) => set.has(p),
    login: vi.fn().mockResolvedValue(perfil),
    logout: vi.fn().mockResolvedValue(undefined),
    cambiarClave: vi.fn().mockResolvedValue(undefined),
    recargarPerfil: vi.fn().mockResolvedValue(undefined),
    cerrarPorInactividad: vi.fn(),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
