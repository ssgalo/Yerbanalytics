/* ============================================================
   Página de detalle de sector — replica pixel a pixel el diseño
   HTML (líneas 405-552 de Yerbanalytics.dc.html).
   ============================================================ */
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSectorDetail } from '@/hooks/useSectorDetail';
import { usePageTitle } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import type { Range } from '@/types/domain';

import { DiagnosisCard } from './components/DiagnosisCard';
import { ActuatorsCard } from './components/ActuatorsCard';
import { MetricTile } from './components/MetricTile';
import { MainChart } from './components/MainChart';
import { PostActionCard } from './components/PostActionCard';
import { SectorHistory } from './components/SectorHistory';

import styles from './SectorPage.module.css';

export function SectorPage() {
  /* ── Hooks — todos antes de cualquier return temprano ── */
  const { id } = useParams();
  const [range, setRange] = useState<Range>('7d');
  const { sector, detail } = useSectorDetail(id, range);
  const navigate = useNavigate();
  usePageTitle(
    sector ? `Sector ${sector.id}` : 'Sector',
    sector ? `${sector.zonaName} · ${sector.statusLabel}` : '',
  );

  /* Return temprano DESPUÉS de todos los hooks */
  if (!sector || !detail) return null;

  return (
    <div className={styles.page}>
      {/* Botón volver */}
      <button className={styles.backBtn} onClick={() => navigate(-1)}>
        <Icon name="chevron-left" size={16} />
        Volver
      </button>

      {/* Header del sector */}
      <div className={styles.header}>
        <span className={styles.colorDot} style={{ background: sector.color }} />
        <h2 className={styles.sectorId}>{sector.id}</h2>
        {/* Badge de estado con colores dinámicos */}
        <Badge soft={detail.statusSoft} ink={detail.statusInk} style={{ fontSize: '12.5px', fontWeight: 700, padding: '4px 12px' }}>
          {sector.statusLabel}
        </Badge>
        <span className={styles.zonaMeta}>
          {sector.zonaName} · última lectura {sector.ago}
        </span>
      </div>

      {/* Grid superior: diagnóstico + actuadores */}
      <div className={styles.topGrid}>
        <DiagnosisCard diag={detail.diag} ago={sector.ago} />
        <ActuatorsCard rows={detail.actsRows} />
      </div>

      {/* Fila de tiles de métricas */}
      <div className={styles.tilesGrid}>
        {detail.metricTiles.map((tile, i) => (
          <MetricTile key={i} tile={tile} />
        ))}
      </div>

      {/* Gráfico principal + seguimiento post-acción */}
      <div className={styles.chartGrid}>
        <MainChart
          mainLine={detail.mainLine}
          mainArea={detail.mainArea}
          mainMin={detail.mainMin}
          mainMax={detail.mainMax}
          range={range}
          onRangeChange={setRange}
        />
        <PostActionCard evo={detail.evo} />
      </div>

      {/* Historial de acciones — full width */}
      <div className={styles.historySection}>
        <SectorHistory hist={detail.hist} />
      </div>
    </div>
  );
}
