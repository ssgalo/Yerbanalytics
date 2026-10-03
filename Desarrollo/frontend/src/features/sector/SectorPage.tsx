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
import { Badge } from '@/components/ui/Badge';
import type { HistoryEntry } from '@/types/domain';

import { Card } from '@/components/ui/Card';
import { DiagnosisCard } from './components/DiagnosisCard';
import { ActuatorsCard } from './components/ActuatorsCard';
import { PostActionCard } from './components/PostActionCard';
import { SectorHistory } from './components/SectorHistory';
import { TUBETES_POR_SECTOR } from './geometriaSector';
import { SectorDiagram } from './components/SectorDiagram';
import { JerarquiaExplainer } from './components/JerarquiaExplainer';
import { AlcanceDiagnostico } from './components/AlcanceDiagnostico';

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

      {/* Dónde estamos parados en la jerarquía física */}
      <JerarquiaExplainer activos={[2, 3]} />

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
          {/* Dibujo del sector físico: es de la macro-zona el nodo testigo, no del sector */}
          <Card className={styles.dibujo}>
            <div className={styles.dibujoHead}>
              <h3 className={styles.dibujoTitle}>El sector, tal como es en el vivero</h3>
              <p className={styles.dibujoSub}>
                {sector.zonaName} · {TUBETES_POR_SECTOR} tubetes (4 bandejas × 25) · 1 microaspersor · nodo
                testigo de la zona
              </p>
            </div>
            <SectorDiagram sector={sector} zona={zona} />
            {sector.status === 'offline' && (
              <p className={styles.sinSenal}>
                Sin señal del nodo testigo de la zona: no hay lectura vigente para evaluar este
                sector.
              </p>
            )}
            <details className={styles.que}>
              <summary>¿Qué estoy viendo?</summary>
              <p>
                Este dibujo representa la realidad física de <b>un sector</b>: ~100 tubetes
                agrupados en 4 bandejas, regados por <b>un</b> microaspersor compartido, con el
                riel y la cámara del vivero pasando por arriba. El <b>nodo testigo</b> que aparece
                a un costado NO está físicamente en este sector: pertenece a la macro-zona y su
                lectura vale para los ~100 sectores de esa zona. El diagnóstico sale de{' '}
                <b>una foto</b> del sector y se aplica al sector completo, por eso todos los
                tubetes llevan el mismo color.
              </p>
            </details>
          </Card>
          <DiagnosisCard
            diag={detail.diag}
            ago={ago}
            sectorId={sector.id}
            isOffline={sector.status === 'offline'}
          />
          <AlcanceDiagnostico />
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
