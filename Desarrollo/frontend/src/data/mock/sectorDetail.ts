/* ============================================================
   Detalle de sector — portado EXACTO desde buildDetail().
   Deriva gráficos, tiles, actuadores, historial y seguimiento.
   ============================================================ */
import type { ActuatorRow, HistoryEntry, Range, Sector, SectorDetail } from '@/types/domain';
import { pathFrom, series } from './generators';
import { sevMap, tints } from './specs';

/** Construye el detalle derivado de un sector para un rango temporal. */
export function buildSectorDetail(s: Sector, range: Range): SectorDetail {
  // gráfico principal: humedad de sustrato según el rango
  const n = range === '24h' ? 24 : range === '30d' ? 30 : 7;
  const seed = (s.zona.charCodeAt(3) * 131 + s.n) >>> 0;
  const main = series(seed, s.metrics[0].raw, n, 6);
  const mainPath = pathFrom(main, 660, 150);

  const metricTiles = s.metrics.map((m, mi) => {
    const sp = series(seed + (mi + 1) * 97, m.raw, 14, m.spec.dec === 1 ? 0.6 : 5);
    const p = pathFrom(sp, 120, 34);
    return {
      label: m.label,
      value: m.value,
      unit: m.unit,
      status: m.status,
      color: m.color,
      line: p.line,
      ideal: m.spec.ideal[0] + '–' + m.spec.ideal[1] + (m.unit === '%' ? '%' : ' ' + m.unit),
      soft: m.status === 'ok' ? '#E7F1EA' : m.status === 'warning' ? '#FBF0DC' : '#FBE6E0',
    };
  });

  // presentación del diagnóstico
  const diag = {
    estado: s.diagnosis.estado,
    conf: s.diagnosis.conf,
    sev: s.diagnosis.sev,
    sevSoft: sevMap[s.diagnosis.sev].soft,
    sevInk: sevMap[s.diagnosis.sev].ink,
    thumb: tints[s.diagnosis.estado] || tints['Sin diagnóstico'],
    concluyente: s.diagnosis.conf != null && s.diagnosis.conf >= 85,
    hasFoto: s.status !== 'offline',
  };

  // actuadores
  const A = s.actuadores;
  const actsRows: ActuatorRow[] = [
    {
      name: 'Electroválvula (riego)',
      state: A.valve,
      active: A.valve === 'Regando',
      path: 'M12 2.7s6 6.6 6 11a6 6 0 0 1-12 0c0-4.4 6-11 6-11Z',
    },
    {
      name: 'Bomba peristáltica',
      state: A.pump,
      active: A.pump === 'Dosificando',
      path: 'M9 2h6M10 2v4l-4.5 9A2 2 0 0 0 7.3 18h9.4a2 2 0 0 0 1.8-3L14 6V2',
    },
    {
      name: 'Mediasombra',
      state: 'Apertura ' + A.shade + '%',
      active: false,
      path: 'M4 12h16M12 4v3M3 16h18a9 9 0 0 0-18 0Z',
    },
  ].map((a) => ({
    ...a,
    soft: a.active ? '#E7F1EA' : '#F0EFE7',
    ink: a.active ? '#2E7A4F' : '#6A776E',
    dot: a.active ? '#3FA06A' : '#A9B2AB',
  }));

  // historial
  const hist: HistoryEntry[] = [];
  if (A.valve === 'Regando')
    hist.push({ tipo: 'Riego', t: 'hace 12 min', d: 'Microaspersor abierto 92 s · 0,41 L emitidos.', res: 'Efectiva', soft: '#E7F1EA', ink: '#2E7A4F' });
  if (A.pump === 'Dosificando')
    hist.push({ tipo: 'Insumo', t: 'hace 44 min', d: 'Inyección de 4,5 ml de fungicida en línea del sector.', res: 'En seguimiento', soft: '#F3ECDD', ink: '#8A6A22' });
  hist.push({ tipo: 'Mediasombra', t: 'hace 2 h', d: 'Cobertura ajustada a ' + A.shade + '% por plan de rustificación (día 12).', res: 'Efectiva', soft: '#E7F1EA', ink: '#2E7A4F' });
  hist.push({ tipo: 'Riego', t: 'hace 5 h', d: 'Riego matinal · 110 s · 0,5 L. Condición: humedad 37%.', res: 'Efectiva', soft: '#E7F1EA', ink: '#2E7A4F' });

  // seguimiento post-acción
  const hs = s.metrics[0];
  const antes = hs.raw < 45 ? hs.raw + 11 : hs.raw - 9;
  const delta = hs.raw - antes;
  const evoVerdict = s.status === 'critical' ? 'En seguimiento' : Math.abs(delta) > 6 ? 'Efectiva' : 'En seguimiento';
  const evo = {
    show: s.status === 'critical' || s.status === 'warning',
    metric: 'Humedad de sustrato',
    antes: antes.toFixed(0),
    ahora: hs.value,
    unit: '%',
    delta: (delta >= 0 ? '+' : '') + delta.toFixed(0),
    latencia: '2 h',
    verdict: evoVerdict,
    vSoft: evoVerdict === 'Efectiva' ? '#E7F1EA' : '#F3ECDD',
    vInk: evoVerdict === 'Efectiva' ? '#2E7A4F' : '#8A6A22',
  };

  return {
    mainLine: mainPath.line,
    mainArea: mainPath.area,
    mainMin: Math.min(...main).toFixed(0),
    mainMax: Math.max(...main).toFixed(0),
    metricTiles,
    diag,
    actsRows,
    hist,
    evo,
    statusSoft:
      s.status === 'ok' ? '#E7F1EA' : s.status === 'warning' ? '#FBF0DC' : s.status === 'critical' ? '#FBE6E0' : '#EEEDE5',
    statusInk:
      s.status === 'ok' ? '#2E7A4F' : s.status === 'warning' ? '#A66A12' : s.status === 'critical' ? '#A8331C' : '#6A776E',
  };
}
