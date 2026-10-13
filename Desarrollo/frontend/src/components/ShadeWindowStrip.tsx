/* ============================================================
   Tira de 24 h con la ventana en que la mediasombra está abierta. La usan el horario por defecto
   (Umbrales) y las etapas de un plan de rustificación (detalle de macro-zona).
   ============================================================ */
import { formatDuration, minutesOf, windowMinutes } from '@/lib/horario';
import styles from './ShadeWindowStrip.module.css';

interface ShadeWindowStripProps {
  desde: string;
  hasta: string;
  /** Muestra las horas 0 · 6 · 12 · 18 · 24 debajo. */
  scale?: boolean;
  /** Marca la hora actual. */
  now?: Date;
}

const DAY = 24 * 60;

export function ShadeWindowStrip({ desde, hasta, scale = false, now }: ShadeWindowStripProps) {
  const a = minutesOf(desde);
  const b = minutesOf(hasta);
  const valida = a !== null && b !== null && b > a;
  const ahora = now ? now.getHours() * 60 + now.getMinutes() : null;
  return (
    <div className={styles.wrap}>
      <div
        className={styles.strip}
        role="img"
        aria-label={
          valida
            ? `Abierta de ${desde} a ${hasta} (${formatDuration(windowMinutes(desde, hasta))}), cerrada el resto del día`
            : 'Horario inválido'
        }
      >
        {valida && (
          <span className={styles.open} style={{ left: `${(a / DAY) * 100}%`, width: `${((b - a) / DAY) * 100}%` }} />
        )}
        {ahora !== null && <span className={styles.now} style={{ left: `${(ahora / DAY) * 100}%` }} />}
      </div>
      {scale && (
        <div className={styles.scale} aria-hidden="true">
          {[0, 6, 12, 18, 24].map((h) => (
            <span key={h} style={{ left: `${(h / 24) * 100}%` }}>
              {h} h
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
