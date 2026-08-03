/* ============================================================
   ZoneSummary — conteo de sectores por estado de salud.

   Cumple además el rol de leyenda del mapa: cada chip lleva el color
   con el que ese estado se pinta en la grilla, así que la grilla no
   necesita repetirla.
   ============================================================ */
import type { Status, Zona } from '@/types/domain';
import styles from './ZoneSummary.module.css';

interface ZoneSummaryProps {
  zona: Zona;
}

/** Los cuatro estados, en el orden en que se degradan. */
const ESTADOS: Array<{ status: Status; label: string; ink: string; bg: string; text: string }> = [
  { status: 'ok', label: 'Saludables', ink: 'var(--ok)', bg: 'var(--ok-soft)', text: '#2E7A4F' },
  {
    status: 'warning',
    label: 'Observación',
    ink: 'var(--warn)',
    bg: 'var(--warn-soft)',
    text: '#A66A12',
  },
  { status: 'critical', label: 'Críticos', ink: 'var(--crit)', bg: 'var(--crit-soft)', text: '#A8331C' },
  { status: 'offline', label: 'Sin señal', ink: 'var(--off)', bg: '#EEEDE5', text: '#6A776E' },
];

export function ZoneSummary({ zona }: ZoneSummaryProps) {
  /* `zona.alerta` junta observación y críticos; acá hacen falta separados, así que
     se cuentan sobre los sectores. */
  const conteo = (status: Status) => zona.sectors.filter((s) => s.status === status).length;

  return (
    <div className={styles.strip}>
      {ESTADOS.map((e) => (
        <div key={e.status} className={styles.chip} style={{ background: e.bg }}>
          <span className={styles.count} style={{ color: e.ink }}>
            {conteo(e.status)}
          </span>
          <span className={styles.label} style={{ color: e.text }}>
            {e.label}
          </span>
        </div>
      ))}
    </div>
  );
}
