/* ============================================================
   La app completa (AuthProvider + rutas + shell) en memoria, para tests de punta a punta.

   Usa `MemoryRouter` + `useRoutes` y no `createMemoryRouter`: con Node 24 + jsdom, navegar en
   un data router falla (`RequestInit: Expected signal to be an instance of AbortSignal`: el
   AbortSignal de jsdom no es el de undici). Las rutas son exactamente las mismas.
   ============================================================ */
import { render } from '@testing-library/react';
import { MemoryRouter, useLocation, useRoutes, type Location } from 'react-router-dom';
import { AuthProvider } from '@/hooks/AuthContext';
import { routes } from '@/router';

/** Monta la app en `ruta`. Devuelve un objeto con la ubicación actual del router, siempre al día. */
export function montarApp(ruta: string): { actual: Location | null } {
  const ubicacion: { actual: Location | null } = { actual: null };
  function Rutas() {
    ubicacion.actual = useLocation();
    return useRoutes(routes);
  }
  render(
    <AuthProvider>
      <MemoryRouter initialEntries={[ruta]} future={{ v7_relativeSplatPath: true, v7_startTransition: true }}>
        <Rutas />
      </MemoryRouter>
    </AuthProvider>,
  );
  return ubicacion;
}
