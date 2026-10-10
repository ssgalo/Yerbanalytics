import { createBrowserRouter, type RouteObject } from 'react-router-dom';
import type { ReactNode } from 'react';
import { AppLayout } from '@/components/layout/AppLayout';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { MapPage } from '@/features/map/MapPage';
import { SectorPage } from '@/features/sector/SectorPage';
import { DiagnosticsPage } from '@/features/diagnostics/DiagnosticsPage';
import { HistorialPage } from '@/features/historial/HistorialPage';
import { ConfiguracionPage } from '@/features/configuracion/ConfiguracionPage';
import { HardwarePage } from '@/features/hardware/HardwarePage';
import { TopologiaPage } from '@/features/topologia/TopologiaPage';
import { ReglasPage } from '@/features/reglas/ReglasPage';
import { DemoExpoPage } from '@/features/demo-expo/DemoExpoPage';
import { UsuariosPage } from '@/features/usuarios/UsuariosPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { CambiarClavePage } from '@/features/auth/CambiarClavePage';
import { Inicio, RequireAuth, RequirePermiso } from '@/features/auth/Guardas';
import { VISTAS, type Vista } from '@/lib/vistas';

/** Cada vista detrás de su permiso, según la tabla compartida con el sidebar. */
const con = (vista: Vista, pagina: ReactNode) => <RequirePermiso vista={vista}>{pagina}</RequirePermiso>;

/**
 * Definición de rutas; aparte del router del navegador para poder montarla en tests.
 * `/login` y `/cambiar-clave` van fuera del shell: sin sesión (o con clave temporal) no hay
 * sidebar ni datos del vivero.
 */
export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/cambiar-clave', element: <CambiarClavePage /> },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppLayout />
      </RequireAuth>
    ),
    children: [
      {
        index: true,
        element: (
          <Inicio>
            <DashboardPage />
          </Inicio>
        ),
      },
      { path: 'mapa', element: con(VISTAS.mapa, <MapPage />) },
      { path: 'sector/:id', element: con(VISTAS.sector, <SectorPage />) },
      { path: 'diagnosticos', element: con(VISTAS.diagnosticos, <DiagnosticsPage />) },
      { path: 'historial', element: con(VISTAS.historial, <HistorialPage />) },
      { path: 'configuracion', element: con(VISTAS.configuracion, <ConfiguracionPage />) },
      { path: 'reglas', element: con(VISTAS.reglas, <ReglasPage />) },
      { path: 'hardware', element: con(VISTAS.hardware, <HardwarePage />) },
      { path: 'topologia', element: con(VISTAS.topologia, <TopologiaPage />) },
      { path: 'demo-expo', element: con(VISTAS.demoExpo, <DemoExpoPage />) },
      { path: 'usuarios', element: con(VISTAS.usuarios, <UsuariosPage />) },
    ],
  },
];

export const router = createBrowserRouter(routes);
