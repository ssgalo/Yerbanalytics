import type { EstadoPasada, EstadoPaso } from '@/types/domain';

/** Color de cada estado, con los tokens del sistema. */
export const COLOR: Record<EstadoPaso, { fg: string; bg: string }> = {
  PENDIENTE: { fg: 'var(--faint)', bg: 'var(--off-soft)' },
  EN_CURSO: { fg: 'var(--info)', bg: 'var(--info-soft)' },
  OK: { fg: 'var(--ok)', bg: 'var(--ok-soft)' },
  ERROR: { fg: 'var(--crit)', bg: 'var(--crit-soft)' },
  OMITIDO: { fg: 'var(--off)', bg: 'var(--off-soft)' },
};

/** Colores del badge de estado de una pasada o secuencia. */
export const COLOR_ESTADO: Record<EstadoPasada, { soft: string; ink: string }> = {
  EN_CURSO: { soft: 'var(--info-soft)', ink: 'var(--info-ink)' },
  COMPLETADA: { soft: 'var(--ok-soft)', ink: 'var(--ok-ink)' },
  FALLIDA: { soft: 'var(--crit-soft)', ink: 'var(--crit-ink)' },
  CANCELADA: { soft: 'var(--off-soft)', ink: 'var(--muted)' },
};
