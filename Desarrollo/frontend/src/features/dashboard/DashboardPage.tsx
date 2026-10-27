import { useRef } from 'react';
import { usePageTitle } from '@/hooks/PageMeta';
import { useNurseryData } from '@/hooks/NurseryContext';
import { useAuth } from '@/hooks/AuthContext';
import { puedeVer, VISTAS } from '@/lib/vistas';
import { NurserySummary } from './components/NurserySummary';
import { NurseryMap } from './components/NurseryMap';
import { SystemActivity } from './components/SystemActivity';
import { useActividadHoy } from './useActividadHoy';
import { useEquipmentSummary } from './useEquipmentSummary';
import styles from './DashboardPage.module.css';

/** Vista principal del panel de control del vivero. */
export function DashboardPage() {
  usePageTitle('Panel general', '');

  const { stats, zonas, layout } = useNurseryData();
  const { puede } = useAuth();
  /* La actividad sale del historial y los equipos, del estado técnico: sin permiso, no se muestran. */
  const canSeeActivity = puedeVer(VISTAS.historial, puede);
  const activity = useActividadHoy(canSeeActivity);
  const equipment = useEquipmentSummary(puedeVer(VISTAS.hardware, puede));
  const activityRef = useRef<HTMLElement>(null);

  function showActivity() {
    const section = activityRef.current;
    if (!section) return;
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    section.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    /* El foco acompaña al scroll: el lector de pantalla y el teclado siguen desde ahí. */
    section.focus({ preventScroll: true });
  }

  return (
    <div className={styles.page}>
      <NurserySummary
        stats={stats}
        zonas={zonas}
        equipment={equipment}
        onShowActivity={canSeeActivity ? showActivity : undefined}
      />

      <NurseryMap zonas={zonas} layout={layout} />

      {canSeeActivity && <SystemActivity ref={activityRef} {...activity} />}
    </div>
  );
}
