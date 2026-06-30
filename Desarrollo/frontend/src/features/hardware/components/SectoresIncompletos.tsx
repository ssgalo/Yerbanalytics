/* Sectores con mapeo de hardware incompleto (HU-18 CA-04) */
import { Card } from '@/components/ui/Card';
import type { SectorIncompleto } from '@/types/domain';
import styles from '../Hardware.module.css';

interface SectoresIncompletosProps {
  incompletos: SectorIncompleto[];
}

export function SectoresIncompletos({ incompletos }: SectoresIncompletosProps) {
  if (incompletos.length === 0) return null;

  return (
    <Card className={styles.section}>
      <div className={styles.sectionHead}>
        <span className={styles.sectionTitle}>Sectores con mapeo incompleto</span>
        <span className={styles.sectionHint}>
          La actuación autónoma permanece deshabilitada hasta completar el equipamiento
        </span>
      </div>
      {incompletos.map((s) => (
        <div key={s.sectorId} className={styles.incompleteRow}>
          <span className={styles.incompleteSector}>{s.sectorId}</span>
          <span className={styles.incompleteFaltan}>
            Falta: {s.faltantes.join(' · ')}
          </span>
        </div>
      ))}
    </Card>
  );
}
