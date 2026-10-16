/* Nivel de confianza de un diagnóstico: "Confianza media", con tres barritas que dicen lo mismo
   por forma (1, 2 o 3 llenas). No es un estado de salud, así que va en tinta neutra: el color de
   estado queda para la severidad. Sin nivel (diagnóstico anterior a los niveles): ninguna llena. */
import type { ConfidenceLevel } from '@/types/domain';
import { confidenceLabel } from '@/lib/diagnosis';
import styles from './ConfidenceChip.module.css';

const FILLED: Record<ConfidenceLevel, number> = { Baja: 1, Media: 2, Alta: 3 };

export function ConfidenceChip({ level }: { level: ConfidenceLevel | null | undefined }) {
  const filled = level ? FILLED[level] : 0;
  return (
    <span className={styles.chip}>
      <span className={styles.bars} aria-hidden="true">
        {[1, 2, 3].map((n) => (
          <i key={n} className={n <= filled ? styles.on : undefined} style={{ height: 3 + n * 3 }} />
        ))}
      </span>
      {confidenceLabel(level)}
    </span>
  );
}
