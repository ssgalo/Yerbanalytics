/* ============================================================
   Selectores: derivaciones puras sobre el dataset del vivero.
   La UI consume estas funciones sin conocer su implementación.
   ============================================================ */
import type { MetricHistory, NurseryData, Range, SectorDetail, Zona } from '@/types/domain';
import { series } from './mock/generators';
import { buildSectorDetail } from './mock/sectorDetail';

/** Detalle derivado de un sector (diagnóstico, actuadores, historial, seguimiento). */
export function selectSectorDetail(data: NurseryData, sectorId: string): SectorDetail | null {
  const sector = data.byId[sectorId];
  if (!sector) return null;
  const zona = data.zonas.find((z) => z.id === sector.zona);
  if (!zona) return null;
  // Find the latest diagnosis for this sector that has an image
  const latestDiag = data.diagnoses.find(d => d.sectorId === sectorId && d.imagenUrl);
  return buildSectorDetail(sector, zona.lectura, latestDiag?.imagenUrl ?? undefined);
}

/**
 * Semilla estable por zona + métrica: la misma serie entre renders, distinta entre
 * métricas y entre zonas.
 */
function seedDe(zonaId: string, metricKey: string): number {
  let h = 2166136261;
  for (const ch of zonaId + '|' + metricKey) {
    h ^= ch.charCodeAt(0);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/** Puntos y paso de cada ventana del histórico: horario en 24 h, cada 4 h en 7 días, diario en 30. */
const HOUR = 3_600_000;
const HISTORY_WINDOWS: Record<Range, { count: number; stepMs: number }> = {
  '24h': { count: 25, stepMs: HOUR },
  '7d': { count: 43, stepMs: 4 * HOUR },
  '30d': { count: 31, stepMs: 24 * HOUR },
};

/**
 * Histórico de una métrica del nodo testigo de la macro-zona, en valores reales (no en paths),
 * para dibujarlo contra las bandas de la métrica. Devuelve `null` si el nodo no la reportó.
 *
 * No hay series persistidas todavía: la serie se deriva de la lectura actual, sintética pero
 * estable para una misma zona, métrica y ventana. El último punto es la lectura actual.
 */
export function selectMetricHistory(
  zona: Zona,
  metricKey: string,
  range: Range,
  now: number = Date.now(),
): MetricHistory | null {
  const m = zona.lectura.metrics.find((x) => x.key === metricKey);
  if (!m || m.raw == null) return null;

  const { count, stepMs } = HISTORY_WINDOWS[range];
  const base = m.spec.dec === 1 ? 0.6 : Math.max(1, (m.spec.ideal[1] - m.spec.ideal[0]) * 0.18);
  // Paso más chico cuantos más puntos: la deriva total queda parecida entre ventanas.
  const amp = base * Math.sqrt(7 / count);
  // El rango entra en la semilla para que cada ventana temporal tenga su propia serie.
  const values = series(seedDe(zona.id, metricKey + '|' + range), m.raw, count, amp);
  const end = zona.lectura.ts ?? now;

  return {
    points: values.map((value, i) => ({ ts: end - (count - 1 - i) * stepMs, value })),
    min: Math.min(...values),
    max: Math.max(...values),
  };
}
