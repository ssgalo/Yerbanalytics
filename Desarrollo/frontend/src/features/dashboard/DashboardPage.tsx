import { usePageTitle } from '@/hooks/PageMeta';
import { useNurseryData } from '@/hooks/NurseryContext';
import { KpiRow } from './components/KpiRow';
import { ViveroOverview } from './components/ViveroOverview';
import { PriorityCard } from './components/PriorityCard';
import { WeatherCard } from './components/WeatherCard';
import { ActivityFeed } from './components/ActivityFeed';
import { RecentDiagnostics } from './components/RecentDiagnostics';

/** Vista principal del panel de control del vivero. */
export function DashboardPage() {
  usePageTitle('Panel general', 'Vivero San Ignacio · Misiones, AR');

  const { stats, zonas, priority, weather, actions, recentDiag } = useNurseryData();

  return (
    <div style={{ animation: 'ybFade .4s both' }}>

      {/* Fila de KPIs */}
      <KpiRow stats={stats} />

      {/* Grilla media: vivero overview + rail derecho */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 360px',
          gap: 16,
          marginTop: 16,
          alignItems: 'start',
        }}
      >
        <ViveroOverview zonas={zonas} />

        {/* Rail derecho: prioridad + clima */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <PriorityCard priority={priority} alerta={stats.alerta} />
          <WeatherCard weather={weather} />
        </div>
      </div>

      {/* Grilla inferior: actividad + diagnósticos */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: '1fr 360px',
          gap: 16,
          marginTop: 16,
          alignItems: 'start',
        }}
      >
        <ActivityFeed actions={actions} />
        <RecentDiagnostics recentDiag={recentDiag} />
      </div>

    </div>
  );
}
