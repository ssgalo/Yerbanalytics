/**
 * Espejo en TS de los design tokens (tokens.css).
 * Útil cuando un valor de color se necesita en JS (p. ej. props de SVG).
 * La fuente de verdad sigue siendo tokens.css: si cambia un valor allá, cambia acá.
 */
export const theme = {
  bg: '#F1F3EE',
  card: '#FFFFFF',
  card2: '#F7F8F5',
  ink: '#15231B',
  ink2: '#2B3B32',
  muted: '#5B6A61',
  faint: '#7D8A82',
  line: '#DDE2DA',
  line2: '#E9ECE6',
  lineField: '#8D978F',
  brand: '#2C6A4F',
  brandSoft: '#E6EFE9',
  ok: '#2F9B62',
  okSoft: '#E3F2E8',
  okInk: '#1E6B41',
  warn: '#E2A230',
  warnSoft: '#FBF0D9',
  warnInk: '#7A5310',
  crit: '#D2462E',
  critSoft: '#FBE3DD',
  critInk: '#9A2B17',
  off: '#9BA39E',
  offSoft: '#ECEEEB',
  offInk: '#545C57',
  info: '#2D6FB5',
  infoSoft: '#E2ECF8',
  infoInk: '#1F4F86',
  tray: '#1C2420',
  tray2: '#2A332E',
  trayLine: '#39433D',
  onTray: '#FFFFFF',
  onTrayMuted: '#A9B5AE',
} as const;

export type Theme = typeof theme;
