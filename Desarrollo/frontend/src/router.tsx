import { createBrowserRouter } from 'react-router-dom';
import { AppLayout } from '@/components/layout/AppLayout';
import { DashboardPage } from '@/features/dashboard/DashboardPage';
import { MapPage } from '@/features/map/MapPage';
import { SectorPage } from '@/features/sector/SectorPage';
import { DiagnosticsPage } from '@/features/diagnostics/DiagnosticsPage';
import { HistorialPage } from '@/features/historial/HistorialPage';
import { ConfiguracionPage } from '@/features/configuracion/ConfiguracionPage';
import { PlaceholderPage } from '@/features/placeholder/PlaceholderPage';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'mapa', element: <MapPage /> },
      { path: 'sector/:id', element: <SectorPage /> },
      { path: 'diagnosticos', element: <DiagnosticsPage /> },
      { path: 'historial', element: <HistorialPage /> },
      { path: 'configuracion', element: <ConfiguracionPage /> },
      { path: 'hardware', element: <PlaceholderPage title="Estado del hardware" /> },
    ],
  },
]);
