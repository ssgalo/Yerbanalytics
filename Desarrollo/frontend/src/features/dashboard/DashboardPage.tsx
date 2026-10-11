import { usePageTitle } from '@/hooks/PageMeta';
import { useNurseryData } from '@/hooks/NurseryContext';
import { useAuth } from '@/hooks/AuthContext';
import { puedeVer, VISTAS } from '@/lib/vistas';
import { KpiRow } from './components/KpiRow';
import { PlanoVivero } from './components/PlanoVivero';
import { ActivityFeed } from './components/ActivityFeed';
import { useActividadHoy } from './useActividadHoy';

/** Vista principal del panel de control del vivero. */
export function DashboardPage() {
  usePageTitle('Panel general', 'Vivero San Ignacio · Misiones, AR');

  const { stats, zonas, layout } = useNurseryData();
  const { puede } = useAuth();
  /* La actividad sale del historial: sin permiso para verlo, la sección no se muestra. */
  const verActividad = puedeVer(VISTAS.historial, puede);
  const actividad = useActividadHoy(verActividad);

  return (
    <div style={{ animation: 'ybFade .4s both', display: 'flex', flexDirection: 'column', gap: 16 }}>
      <KpiRow stats={stats} />

      <PlanoVivero zonas={zonas} layout={layout} />

      {verActividad && <ActivityFeed {...actividad} />}
    </div>
  );
}
