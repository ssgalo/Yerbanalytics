/**
 * Espejo en TS de los design tokens (tokens.css).
 * Útil cuando un valor de color se necesita en JS (p. ej. props de SVG).
 * La fuente de verdad sigue siendo tokens.css.
 */
export const theme = {
  bg: '#F3F2EA',
  card: '#FFFFFF',
  ink: '#16241D',
  muted: '#6A776E',
  faint: '#97A299',
  line: '#E7E5D9',
  line2: '#F0EFE7',
  g900: '#0C2319',
  g800: '#11352A',
  g700: '#1A4D3B',
  brand: '#1E6A4E',
  ok: '#3FA06A',
  okSoft: '#E7F1EA',
  warn: '#E0972C',
  warnSoft: '#FBF0DC',
  crit: '#DD5238',
  critSoft: '#FBE6E0',
  off: '#A9B2AB',
  orange: '#EC6A1E',
  orangeSoft: '#FBE7D7',
  cream: '#ECF1E9',
  cdim: 'rgba(236,241,233,.62)',
  cfaint: 'rgba(236,241,233,.40)',
} as const;

export type Theme = typeof theme;
