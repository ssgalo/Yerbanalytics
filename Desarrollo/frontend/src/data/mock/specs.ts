/* ============================================================
   Constantes de dominio — portadas EXACTAS desde el diseño.
   Paletas, bandas de métricas, definiciones de zonas y plantillas.
   ============================================================ */
import type { ColorPair, MetricSpec, Severity, Status } from '@/types/domain';

/** Colores por estado. */
export const C: Record<Status, string> = {
  ok: '#3FA06A',
  warning: '#E0972C',
  critical: '#DD5238',
  offline: '#A9B2AB',
};

/** Etiquetas por estado. */
export const LAB: Record<Status, string> = {
  ok: 'Saludable',
  warning: 'En observación',
  critical: 'Crítico',
  offline: 'Fuera de servicio',
};

/** Pares soft/ink por severidad. */
export const sevMap: Record<Severity, ColorPair> = {
  Alta: { soft: '#FBE6E0', ink: '#A8331C' },
  Media: { soft: '#FBF0DC', ink: '#A66A12' },
  Baja: { soft: '#E7F1EA', ink: '#2E7A4F' },
  '—': { soft: '#EEEDE5', ink: '#6A776E' },
};

/** Especificaciones de las 5 métricas monitoreadas. */
export const specs: MetricSpec[] = [
  { key: 'humSus', label: 'Humedad de sustrato', unit: '%', ideal: [42, 68], warn: [32, 80], crit: [22, 90], dec: 0, base: 55 },
  { key: 'humAmb', label: 'Humedad ambiental', unit: '%', ideal: [62, 84], warn: [52, 91], crit: [42, 96], dec: 0, base: 72 },
  { key: 'temp', label: 'Temperatura', unit: '°C', ideal: [18, 27], warn: [15, 31], crit: [11, 35], dec: 1, base: 23 },
  { key: 'ce', label: 'Nutrientes (CE)', unit: 'dS/m', ideal: [1.0, 1.9], warn: [0.8, 2.5], crit: [0.5, 3.1], dec: 1, base: 1.4 },
  { key: 'uv', label: 'Radiación UV', unit: 'UVI', ideal: [1, 6], warn: [0, 8], crit: [0, 12], dec: 0, base: 4 },
];

/** Definiciones de las 6 macro-zonas. */
export const zonaDefs = [
  { id: 'MZ-1', name: 'Macro-zona 1', sub: 'Sector norte' },
  { id: 'MZ-2', name: 'Macro-zona 2', sub: 'Sector norte' },
  { id: 'MZ-3', name: 'Macro-zona 3', sub: 'Sector centro' },
  { id: 'MZ-4', name: 'Macro-zona 4', sub: 'Sector centro' },
  { id: 'MZ-5', name: 'Macro-zona 5', sub: 'Sector sur' },
  { id: 'MZ-6', name: 'Macro-zona 6', sub: 'Sector sur' },
];

/** Patologías posibles por estado [nombre, severidad]. */
export const pathos: Record<'warning' | 'critical', Array<[string, Severity]>> = {
  warning: [
    ['Clorosis', 'Media'],
    ['Estrés solar', 'Media'],
  ],
  critical: [
    ['Daño fúngico', 'Alta'],
    ['Plaga foliar', 'Alta'],
    ['Estrés solar', 'Alta'],
  ],
};

/** Gradientes (thumbnails) por estado de diagnóstico. */
export const tints: Record<string, string> = {
  Sano: 'radial-gradient(circle at 35% 30%, #5BBE82, #2C7A4E)',
  'Estrés solar': 'radial-gradient(circle at 35% 30%, #E89B5A, #B5572A)',
  Clorosis: 'radial-gradient(circle at 35% 30%, #E4CE5E, #B79A2C)',
  'Plaga foliar': 'radial-gradient(circle at 35% 30%, #6FB0A6, #2F6F66)',
  'Daño fúngico': 'radial-gradient(circle at 35% 30%, #B89B7D, #6E563C)',
  'No concluyente': 'radial-gradient(circle at 35% 30%, #B7BEB8, #7C857E)',
  'Sin diagnóstico': 'linear-gradient(135deg,#C4CAC4,#9AA39D)',
};

/** Metadatos visuales de cada tipo de acción autónoma. */
export const ACT: Record<string, { tint: string; ink: string; path: string }> = {
  Riego: { tint: '#E2EEF3', ink: '#2A6E8C', path: 'M12 2.7s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11Z' },
  Insumo: { tint: '#EDEAF6', ink: '#5A4B9E', path: 'M9 2h6M10 2v4l-4.5 9A2 2 0 0 0 7.3 18h9.4a2 2 0 0 0 1.8-3L14 6V2' },
  Mediasombra: { tint: '#F3ECDD', ink: '#8A6A22', path: 'M4 12h16M12 4v3M6 7l1.5 1.5M18 7l-1.5 1.5M3 16h18a9 9 0 0 0-18 0Z' },
};

/** Pares soft/ink por resultado de acción. */
export const resMap: Record<string, ColorPair> = {
  Efectiva: { soft: '#E7F1EA', ink: '#2E7A4F' },
  'En seguimiento': { soft: '#F3ECDD', ink: '#8A6A22' },
  Pospuesta: { soft: '#FBE7D7', ink: '#9A4410' },
  Abortada: { soft: '#FBE6E0', ink: '#A8331C' },
};

/** Plantillas del feed de actividad del sistema. */
export const actTpl = [
  { tipo: 'Riego', title: 'Riego ejecutado', d: 'Humedad de sustrato bajo umbral (38%). Microaspersor abierto 95 s · 0,42 L.', res: 'Efectiva', t: 'hace 6 min' },
  { tipo: 'Riego', title: 'Riego pospuesto', d: 'Déficit hídrico detectado pero API meteorológica confirma lluvia inminente (60%).', res: 'Pospuesta', t: 'hace 22 min' },
  { tipo: 'Insumo', title: 'Dosificación de fungicida', d: 'Daño fúngico (confianza 93%) + sustrato >80%. Bomba peristáltica inyectó 4,5 ml.', res: 'En seguimiento', t: 'hace 48 min' },
  { tipo: 'Mediasombra', title: 'Apertura de mediasombra', d: 'Plan de rustificación día 12 · apertura gradual 35% → 45%.', res: 'Efectiva', t: 'hace 1 h' },
  { tipo: 'Insumo', title: 'Dosificación de nutrientes', d: 'Clorosis por déficit nutricional (confianza 89%). Inyección de 3,0 ml de NPK.', res: 'En seguimiento', t: 'hace 1 h' },
  { tipo: 'Riego', title: 'Riego abortado', d: 'Sensor testigo sin reporte hace 2 h. Actuación autónoma anulada por seguridad.', res: 'Abortada', t: 'hace 2 h' },
  { tipo: 'Mediasombra', title: 'Retracción de mediasombra', d: 'Pico de radiación UV 9 detectado. Cobertura llevada a 70% para proteger plantines.', res: 'Efectiva', t: 'hace 3 h' },
  { tipo: 'Insumo', title: 'Dosificación bloqueada', d: 'Límite químico diario alcanzado (sector ya recibió dosis máx. en 24 h).', res: 'Abortada', t: 'hace 4 h' },
];
