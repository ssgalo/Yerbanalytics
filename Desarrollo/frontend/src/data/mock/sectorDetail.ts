/* ============================================================
   Detalle de sector — portado EXACTO desde buildDetail().
   Deriva gráficos, tiles, actuadores, historial y seguimiento.
   ============================================================ */
import type { ActuatorRow, HistoryEntry, LecturaZona, Sector, SectorDetail } from '@/types/domain';
import { sevMap, tints } from './specs';

/**
 * Construye el detalle derivado de un sector: diagnóstico, actuadores, historial y
 * seguimiento post-acción. Sin métricas ni gráfico de series — eso vive en el panel de
 * sensado de la macro-zona.
 *
 * Recibe la `lectura` de su macro-zona porque el seguimiento post-acción compara la
 * humedad de sustrato antes y ahora, y ese valor es de la zona.
 */
export function buildSectorDetail(s: Sector, lectura: LecturaZona, imagenUrl?: string): SectorDetail {
  // presentación del diagnóstico
  const diag = {
    estado: s.diagnosis.estado,
    conf: s.diagnosis.conf,
    sev: s.diagnosis.sev,
    sevSoft: sevMap[s.diagnosis.sev].soft,
    sevInk: sevMap[s.diagnosis.sev].ink,
    thumb: imagenUrl ? `url("${imagenUrl}") center/cover no-repeat` : (tints[s.diagnosis.estado] || tints['Sin diagnóstico']),
    concluyente: s.diagnosis.conf != null && s.diagnosis.conf >= 85,
    hasFoto: !!imagenUrl || s.status !== 'offline',
    imagenUrl,
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
    hist.push({
      tipo: 'Riego',
      t: 'hace 12 min',
      d: 'Microaspersor abierto 92 s · 0,41 L emitidos.',
      res: 'Efectiva',
      soft: '#E7F1EA',
      ink: '#2E7A4F',
    });
  if (A.pump === 'Dosificando')
    hist.push({
      tipo: 'Insumo',
      t: 'hace 44 min',
      d: 'Inyección de 4,5 ml de fungicida en línea del sector.',
      res: 'En seguimiento',
      soft: '#F3ECDD',
      ink: '#8A6A22',
    });
  hist.push({
    tipo: 'Mediasombra',
    t: 'hace 2 h',
    d: 'Cobertura ajustada a ' + A.shade + '% por plan de rustificación (día 12).',
    res: 'Efectiva',
    soft: '#E7F1EA',
    ink: '#2E7A4F',
  });
  hist.push({
    tipo: 'Riego',
    t: 'hace 5 h',
    d: 'Riego matinal · 110 s · 0,5 L. Condición: humedad 37%.',
    res: 'Efectiva',
    soft: '#E7F1EA',
    ink: '#2E7A4F',
  });

  // seguimiento post-acción — sobre la humedad de sustrato de la macro-zona
  const hs = lectura.metrics.find((m) => m.key === 'humSus');
  const ahora = hs?.raw ?? null;
  const antes = ahora == null ? null : ahora < 45 ? ahora + 11 : ahora - 9;
  const delta = ahora != null && antes != null ? ahora - antes : 0;
  const evoVerdict =
    s.status === 'critical'
      ? 'En seguimiento'
      : Math.abs(delta) > 6
        ? 'Efectiva'
        : 'En seguimiento';
  const evo = {
    // Sin lectura vigente no hay contra qué comparar: no se muestra el seguimiento.
    show: ahora != null && (s.status === 'critical' || s.status === 'warning'),
    metric: 'Humedad de sustrato',
    antes: antes != null ? antes.toFixed(0) : '—',
    ahora: hs?.value ?? '—',
    unit: '%',
    delta: (delta >= 0 ? '+' : '') + delta.toFixed(0),
    latencia: '2 h',
    verdict: evoVerdict,
    vSoft: evoVerdict === 'Efectiva' ? '#E7F1EA' : '#F3ECDD',
    vInk: evoVerdict === 'Efectiva' ? '#2E7A4F' : '#8A6A22',
  };

  return {
    diag,
    actsRows,
    hist,
    evo,
    statusSoft:
      s.status === 'ok'
        ? '#E7F1EA'
        : s.status === 'warning'
          ? '#FBF0DC'
          : s.status === 'critical'
            ? '#FBE6E0'
            : '#EEEDE5',
    statusInk:
      s.status === 'ok'
        ? '#2E7A4F'
        : s.status === 'warning'
          ? '#A66A12'
          : s.status === 'critical'
            ? '#A8331C'
            : '#6A776E',
  };
}
