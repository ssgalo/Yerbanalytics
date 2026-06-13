/* ============================================================
   ZoneSummary — tarjeta con resumen numérico de la zona activa.
   Tres cajas: Saludables (ok-soft), En alerta (warn-soft), Sin señal (#EEEDE5).
   ============================================================ */
import type { Zona } from '@/types/domain';
import { Card } from '@/components/ui/Card';
import styles from './ZoneSummary.module.css';

interface ZoneSummaryProps {
  zona: Zona;
}

export function ZoneSummary({ zona }: ZoneSummaryProps) {
  return (
    <Card style={{ padding: '18px 19px' }}>
      <h2 className={styles.title}>Resumen de zona</h2>
      <div className={styles.grid}>
        {/* Saludables */}
        <div className={styles.box} style={{ background: 'var(--ok-soft)' }}>
          <div className={styles.count} style={{ color: 'var(--ok)' }}>
            {zona.sano}
          </div>
          <div className={styles.label} style={{ color: '#2E7A4F' }}>
            Saludables
          </div>
        </div>

        {/* En alerta */}
        <div className={styles.box} style={{ background: 'var(--warn-soft)' }}>
          <div className={styles.count} style={{ color: 'var(--warn)' }}>
            {zona.alerta}
          </div>
          <div className={styles.label} style={{ color: '#A66A12' }}>
            En alerta
          </div>
        </div>

        {/* Sin señal */}
        <div className={styles.box} style={{ background: '#EEEDE5' }}>
          <div className={styles.count} style={{ color: 'var(--off)' }}>
            {zona.off}
          </div>
          <div className={styles.label} style={{ color: '#6A776E' }}>
            Sin señal
          </div>
        </div>
      </div>
    </Card>
  );
}
