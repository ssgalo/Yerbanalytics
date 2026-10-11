import type { Status } from '@/types/domain';

/**
 * Sufijo de token de cada estado: `--ok`, `--warn`, `--crit`, `--off` (y sus `-soft`, `-ink`).
 * Los componentes lo usan para elegir la clase; nunca un hex.
 */
export const TONO: Record<Status, 'ok' | 'warn' | 'crit' | 'off'> = {
  ok: 'ok',
  warning: 'warn',
  critical: 'crit',
  offline: 'off',
};

/** Nombre de cada estado, para texto visible y accesible. */
export const ETIQUETA_ESTADO: Record<Status, string> = {
  ok: 'Saludable',
  warning: 'En observación',
  critical: 'Crítico',
  offline: 'Fuera de servicio',
};

/** Une clases ignorando las vacías. */
export const cx = (...clases: (string | false | null | undefined)[]) => clases.filter(Boolean).join(' ');
