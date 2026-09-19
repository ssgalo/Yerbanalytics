/* ============================================================
   Página de detalle de sector.

   Muestra SÓLO lo que es propio del sector: diagnóstico de IA del
   plantín, actuadores, seguimiento post-acción e historial. Los valores
   sensados son de la macro-zona (un nodo testigo por MZ) y viven en su
   panel.

   Ocupa el alto de la pantalla repartiendo las tarjetas, para no dejar
   media vista vacía.
   ============================================================ */
import { useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSectorDetail } from '@/hooks/useSectorDetail';
import { useHistory } from '@/hooks/useHistory';
import { usePageTitle } from '@/hooks/PageMeta';
import { Icon } from '@/components/ui/Icon';
import { Badge } from '@/components/ui/Badge';
import type { HistoryEntry } from '@/types/domain';

import { DiagnosisCard } from './components/DiagnosisCard';
import { ActuatorsCard } from './components/ActuatorsCard';
import { PostActionCard } from './components/PostActionCard';
import { SectorHistory } from './components/SectorHistory';

import styles from './SectorPage.module.css';

export function SectorPage() {
  /* ── Hooks — todos antes de cualquier return temprano ── */
  const { id } = useParams();
  const { sector, zona, detail } = useSectorDetail(id);
  const { records } = useHistory();
  const navigate = useNavigate();
  usePageTitle(
    sector ? `Sector ${sector.id}` : 'Sector',
    sector ? `${sector.zonaName} · ${sector.statusLabel}` : '',
  );

  const realHistory = useMemo(() => {
    if (!sector) return [];
    return records
      .filter((r) => r.sectorId === sector.id)
      .sort((a, b) => b.ts - a.ts)
      .slice(0, 5)
      .map((r) => ({
        tipo: r.tipo,
        t: r.time,
        d: r.accion && r.accion !== '—' ? `${r.decision} · ${r.accion}` : r.decision,
        res: r.res,
        soft: r.resSoft,
        ink: r.resInk,
      } as HistoryEntry));
  }, [records, sector]);

  /* Return temprano DESPUÉS de todos los hooks */
  if (!sector || !detail) return null;

  const ago = zona?.lectura.ago ?? 'hace —';

  return (
    <div className={styles.page}>
      {/* Rastro de navegación: vuelve a la macro-zona del sector */}
      <nav className={styles.crumbs} aria-label="Navegación">
        <button
          className={styles.backBtn}
          onClick={() => navigate(zona ? `/mapa?zona=${zona.id}` : '/')}
        >
          <Icon name="chevron-left" size={15} />
          {sector.zonaName}
        </button>
      </nav>

      {/* Header del sector */}
      <div className={styles.header}>
        <span className={styles.colorDot} style={{ background: sector.color }} />
        <h2 className={styles.sectorId}>{sector.id}</h2>
        <Badge
          soft={detail.statusSoft}
          ink={detail.statusInk}
          style={{ fontSize: '12.5px', fontWeight: 700, padding: '4px 12px' }}
        >
          {sector.statusLabel}
        </Badge>
        <span className={styles.reason}>{sector.reason}</span>
      </div>

      {/* Diagnóstico e historial a la izquierda; actuadores y seguimiento a la derecha */}
      <div className={styles.layout}>
        <div className={styles.col}>
          <DiagnosisCard
            diag={detail.diag}
            ago={ago}
            sectorId={sector.id}
            isOffline={sector.status === 'offline'}
          />
          <SectorHistory hist={realHistory} />
        </div>
        <div className={styles.col}>
          <ActuatorsCard rows={detail.actsRows} />
          <PostActionCard evo={detail.evo} />
        </div>
      </div>
    </div>
  );
}
