/* ============================================================
   Página de detalle de sector.

   Tres tarjetas: el último diagnóstico de IA (izquierda), el
   seguimiento post-acción (derecha) y el historial de acciones del
   sector (abajo, a todo el ancho). Los valores sensados son de la
   macro-zona (un nodo testigo por MZ) y viven en su panel.
   ============================================================ */
import { useMemo } from 'react';
import { Link, useParams, useNavigate } from 'react-router-dom';
import { useSectorDetail } from '@/hooks/useSectorDetail';
import { useHistory } from '@/hooks/useHistory';
import { usePageTitle } from '@/hooks/PageMeta';
import { Badge } from '@/components/ui/Badge';
import type { HistoryEntry } from '@/types/domain';

import { DiagnosisCard } from './components/DiagnosisCard';
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
      {/* Rastro de navegación: Vivero / macro-zona / sector */}
      <nav className={styles.crumbs} aria-label="Navegación">
        <button className={styles.crumbBtn} onClick={() => navigate('/')}>
          Vivero
        </button>
        <span className={styles.sep}>/</span>
        <button
          className={styles.crumbBtn}
          onClick={() => navigate(zona ? `/mapa?zona=${zona.id}` : '/')}
        >
          {zona?.id ?? sector.zona}
        </button>
        <span className={styles.sep}>/</span>
        <span className={styles.current}>Sector {sector.id}</span>
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
        {/* Cómo decidió el motor para este sector: recibido vs. umbral de cada regla */}
        <Link className={styles.motorLink} to={`/reglas?tab=inspector&sector=${encodeURIComponent(sector.id)}`}>
          Ver última evaluación del motor
        </Link>
      </div>

      {/* Diagnóstico a la izquierda, seguimiento a la derecha, historial abajo a todo el ancho */}
      <div className={styles.layout}>
        <DiagnosisCard
          diag={detail.diag}
          ago={ago}
          sectorId={sector.id}
          isOffline={sector.status === 'offline'}
        />
        <PostActionCard evo={detail.evo} />
        <div className={styles.full}>
          <SectorHistory hist={realHistory} />
        </div>
      </div>
    </div>
  );
}
