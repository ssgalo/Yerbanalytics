/* ============================================================
   Selectores: derivaciones puras sobre el dataset del vivero.
   La UI consume estas funciones sin conocer su implementación.
   ============================================================ */
import type {
  MetricTile,
  NurseryData,
  Range,
  SectorDetail,
  SerieMetrica,
  Zona,
} from '@/types/domain';
import { pathFrom, series } from './mock/generators';
import { buildSectorDetail } from './mock/sectorDetail';

/** Cantidad de puntos de la serie según el rango temporal. */
const PUNTOS_POR_RANGO: Record<Range, number> = { '24h': 24, '7d': 7, '30d': 30 };

/** Detalle derivado de un sector (diagnóstico, actuadores, historial, seguimiento). */
export function selectSectorDetail(data: NurseryData, sectorId: string): SectorDetail | null {
  const sector = data.byId[sectorId];
  if (!sector) return null;
  const zona = data.zonas.find((z) => z.id === sector.zona);
  if (!zona) return null;
  // Find the latest diagnosis for this sector that has an image
  const latestDiag = data.diagnoses.find(d => d.sectorId === sectorId && d.imagenUrl);
  return buildSectorDetail(sector, zona.lectura, latestDiag?.imagenUrl);
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

/**
 * Tiles del panel de sensado: las 10 métricas de la zona con su sparkline.
 *
 * El histórico se deriva de la lectura actual (no hay series persistidas todavía), así que
 * la serie es sintética pero estable para una misma zona y métrica.
 */
export function selectSensadoTiles(zona: Zona): MetricTile[] {
  return zona.lectura.metrics.map((m) => {
    const amp = m.spec.dec === 1 ? 0.6 : Math.max(1, (m.spec.ideal[1] - m.spec.ideal[0]) * 0.18);
    const puntos = m.raw != null ? series(seedDe(zona.id, m.key), m.raw, 14, amp) : [];
    const line = puntos.length > 0 ? pathFrom(puntos, 120, 34).line : '';
    return {
      key: m.key,
      label: m.label,
      value: m.value,
      unit: m.unit,
      status: m.status,
      color: m.color,
      line,
      ideal: m.spec.ideal[0] + '–' + m.spec.ideal[1] + (m.unit === '%' ? '%' : ' ' + m.unit),
      soft:
        m.status === 'ok'
          ? '#E7F1EA'
          : m.status === 'warning'
            ? '#FBF0DC'
            : m.status === 'critical'
              ? '#FBE6E0'
              : '#EEEDE5',
      grupo: m.spec.grupo,
      provisional: !m.spec.afectaEstado,
    };
  });
}

/**
 * Serie histórica de una métrica de la macro-zona para el gráfico del panel.
 * Devuelve `null` si la métrica no existe o el nodo no la reportó.
 */
export function selectSerieMetrica(
  zona: Zona,
  metricKey: string,
  range: Range,
): SerieMetrica | null {
  const m = zona.lectura.metrics.find((x) => x.key === metricKey);
  if (!m || m.raw == null) return null;

  const n = PUNTOS_POR_RANGO[range];
  const amp = m.spec.dec === 1 ? 0.6 : Math.max(1, (m.spec.ideal[1] - m.spec.ideal[0]) * 0.18);
  // El rango entra en la semilla para que cada ventana temporal tenga su propia serie.
  const puntos = series(seedDe(zona.id, metricKey + '|' + range), m.raw, n, amp);
  const path = pathFrom(puntos, 660, 150);
  const unidad = m.unit === '%' ? '%' : ' ' + m.unit;

  return {
    line: path.line,
    area: path.area,
    min: Math.min(...puntos).toFixed(m.spec.dec) + unidad,
    max: Math.max(...puntos).toFixed(m.spec.dec) + unidad,
  };
}
