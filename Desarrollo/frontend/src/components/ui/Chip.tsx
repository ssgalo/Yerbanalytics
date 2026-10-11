import type { ReactNode } from 'react';
import type { Status } from '@/types/domain';
import { StateGlyph } from './StateGlyph';
import { ETIQUETA_ESTADO, TONO, cx } from './estado';
import styles from './Chip.module.css';

/** Un estado de salud, o: `info` = el sistema está actuando, `line` = neutro, `dark` = destacado. */
export type ChipTone = Status | 'info' | 'line' | 'dark';

interface ChipProps {
  tone: ChipTone;
  /** Por defecto, el nombre del estado. */
  children?: ReactNode;
  /** Antepone el glifo del estado (sólo con un `Status`). */
  glyph?: boolean;
  size?: 'md' | 'lg';
}

const esEstado = (t: ChipTone): t is Status => t in TONO;

/** Chip de estado: fondo suave + texto en la tinta del estado (siempre ≥ 6:1). */
export function Chip({ tone, children, glyph, size = 'md' }: ChipProps) {
  const clase = esEstado(tone) ? TONO[tone] : tone;
  return (
    <span className={cx(styles.chip, styles[clase], size === 'lg' && styles.lg)}>
      {glyph && esEstado(tone) && <StateGlyph status={tone} />}
      {children ?? (esEstado(tone) ? ETIQUETA_ESTADO[tone] : null)}
    </span>
  );
}
