import { useState } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useNurseryData } from '@/hooks/NurseryContext';
import { KpiRow } from './components/KpiRow';
import { PlanoVivero } from './components/PlanoVivero';
import { EstadoVivero } from './components/EstadoVivero';
import type { FocoZona } from './components/ZonaBlock';
import { PriorityCard } from './components/PriorityCard';
import { WeatherCard } from './components/WeatherCard';
import { ActivityFeed } from './components/ActivityFeed';
import { RecentDiagnostics } from './components/RecentDiagnostics';

/** Vista principal del panel de control del vivero. */
export function DashboardPage() {
  usePageTitle('Panel general', 'Vivero San Ignacio · Misiones, AR');

  const { stats, zonas, priority, weather, actions, recentDiag, layout } = useNurseryData();

  /* Zona a la que se llegó tocando su parcela del plano (el tick re-dispara el destello). */
  const [foco, setFoco] = useState<FocoZona | null>(null);

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
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0 }}>
          <PlanoVivero
            zonas={zonas}
            onSelectZona={(zonaId) => setFoco((f) => ({ zonaId, tick: (f?.tick ?? 0) + 1 }))}
          />
          <EstadoVivero zonas={zonas} layout={layout} foco={foco} />
        </div>

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
