/* ============================================================
   Generador del vivero — portado EXACTO desde build()/makeSector().
   El ORDEN de las llamadas a r() define la salida: NO reordenar.
   ============================================================ */
import { createRng, pick, rr } from '@/lib/rng';
import type {
  ActionEvent,
  Alert,
  DiagnosisCard,
  DisposicionTopologia,
  Metric,
  NurseryData,
  PriorityItem,
  Sector,
  Severity,
  Status,
  Weather,
  Zona,
} from '@/types/domain';
import { ACT, C, LAB, actTpl, pathos, resMap, sevMap, specs, tints, zonaDefs } from './specs';
import {
  clampDisposicion,
  DEFAULT_MACRO_ZONAS_POR_FILA,
  DEFAULT_SECTORES_POR_FILA,
  zonaDefsFor,
} from './topologia';

/**
 * Estructura de la grilla a generar: cantidad de macro-zonas y de sectores por macro-zona, más
 * la disposición visual por fila (opcional; defaults si no se especifica).
 */
export interface TopologiaGrid {
  macroZonas: number;
  sectoresPorMacroZona: number;
  macroZonasPorFila?: number;
  sectoresPorFila?: number;
}

/** Clasifica un valor según las bandas de su spec. */
export function metricStatus(v: number, sp: (typeof specs)[number]): Status {
  if (v < sp.warn[0] || v > sp.warn[1]) return 'critical';
  if (v < sp.ideal[0] || v > sp.ideal[1]) return 'warning';
  return 'ok';
}

/** Serie temporal sintética para sparklines (RNG propio por semilla). */
export function series(seed: number, end: number, n: number, amp: number): number[] {
  const r = createRng(seed);
  const out: number[] = [];
  let v = end + (r() - 0.5) * amp * 2;
  for (let i = 0; i < n; i++) {
    v += (r() - 0.5) * amp;
    out.push(v);
  }
  out[out.length - 1] = end;
  return out;
}

/** Convierte una serie en paths SVG (línea + área). */
export function pathFrom(vals: number[], w: number, h: number): { line: string; area: string } {
  const pad = 6;
  const min = Math.min(...vals);
  const max = Math.max(...vals);
  const span = max - min || 1;
  const step = w / (vals.length - 1);
  const pts = vals.map((v, i) => [i * step, pad + (h - pad * 2) * (1 - (v - min) / span)]);
  const line = 'M' + pts.map((p) => p[0].toFixed(1) + ',' + p[1].toFixed(1)).join(' L');
  const area = line + ' L' + w + ',' + h + ' L0,' + h + ' Z';
  return { line, area };
}

/** Genera un sector completo (métricas, diagnóstico, actuadores). */
function makeSector(
  z: (typeof zonaDefs)[number],
  i: number,
  status: Status,
  r: () => number,
): Sector {
  const id = z.id + '-' + String(i).padStart(3, '0');
  const offIdx = status === 'ok' || status === 'offline' ? -1 : Math.floor(r() * specs.length);
  const metrics: Metric[] = specs.map((sp, mi) => {
    let v: number;
    if (mi === offIdx && status === 'warning') {
      v =
        r() > 0.5
          ? rr(r, sp.warn[1] - (sp.warn[1] - sp.ideal[1]) * 0.5, sp.warn[1])
          : rr(r, sp.warn[0], sp.ideal[0]);
    } else if (mi === offIdx && status === 'critical') {
      v = r() > 0.5 ? rr(r, sp.warn[1], sp.crit[1]) : rr(r, sp.crit[0], sp.warn[0]);
    } else {
      v = rr(r, sp.ideal[0] + (sp.ideal[1] - sp.ideal[0]) * 0.12, sp.ideal[1] - (sp.ideal[1] - sp.ideal[0]) * 0.12);
    }
    const ms = metricStatus(v, sp);
    return {
      key: sp.key,
      label: sp.label,
      unit: sp.unit,
      raw: v,
      value: v.toFixed(sp.dec),
      status: ms,
      color: C[ms],
      spec: sp,
    };
  });

  const realStatus: Status =
    status === 'offline'
      ? 'offline'
      : metrics.some((m) => m.status === 'critical')
        ? 'critical'
        : metrics.some((m) => m.status === 'warning')
          ? 'warning'
          : 'ok';
  const finalStatus: Status = status === 'offline' ? 'offline' : realStatus;

  // diagnóstico
  let diagnosis: Sector['diagnosis'];
  if (finalStatus === 'offline') {
    diagnosis = { estado: 'Sin diagnóstico', conf: null, sev: '—' };
  } else if (finalStatus === 'ok') {
    diagnosis = { estado: 'Sano', conf: Math.round(rr(r, 95, 99)), sev: '—' };
  } else {
    const isNC = r() > 0.86;
    if (isNC) {
      diagnosis = { estado: 'No concluyente', conf: Math.round(rr(r, 64, 83)), sev: '—' };
    } else {
      const p = pick(r, pathos[finalStatus]);
      diagnosis = { estado: p[0], conf: Math.round(rr(r, 86, 98)), sev: p[1] };
    }
  }

  // razón
  let reason: string;
  if (finalStatus === 'offline') {
    reason = 'Sin reporte de telemetría · señal perdida';
  } else if (finalStatus === 'ok') {
    reason = 'Todos los parámetros en rango óptimo';
  } else {
    const bad = metrics.find((m) => m.status === finalStatus) || metrics.find((m) => m.status !== 'ok');
    reason = bad
      ? bad.label + ' ' + bad.value + (bad.unit === '%' ? '%' : ' ' + bad.unit)
      : diagnosis.estado;
  }

  // actuadores
  const humSus = metrics[0];
  const valve = finalStatus !== 'offline' && humSus.raw < 42 ? 'Regando' : 'Cerrada';
  const pump = finalStatus === 'critical' && diagnosis.conf && diagnosis.conf >= 85 ? 'Dosificando' : 'En espera';
  const shadePct = Math.round(rr(r, 30, 65) / 5) * 5;

  const mins = Math.round(rr(r, 4, 28));
  const ago = finalStatus === 'offline' ? 'hace ' + Math.round(rr(r, 24, 31)) + ' h' : 'hace ' + mins + ' min';

  return {
    id,
    zona: z.id,
    zonaName: z.name,
    n: i,
    status: finalStatus,
    color: C[finalStatus],
    statusLabel: LAB[finalStatus],
    tip: id + ' · ' + LAB[finalStatus],
    metrics,
    diagnosis,
    reason,
    actuadores: { valve, pump, shade: shadePct },
    ago,
    stale: finalStatus === 'offline',
  };
}

/**
 * Genera el dataset completo del vivero con una semilla dada.
 *
 * Sin `topologia`: la grilla demo por defecto (6 × 100) con estados aleatorios. Con
 * `topologia` (tras una generación del Administrador, HU-18 CA-01): una grilla N × M con
 * todos los sectores en estado offline ("Fuera de servicio"), como el seed, hasta que
 * llegue telemetría. `disposicion` fija la disposición visual por fila con independencia de
 * la grilla (permite reacomodar la grilla demo sin volverla offline).
 */
export function buildNursery(
  seed: number,
  topologia?: TopologiaGrid,
  disposicion?: DisposicionTopologia,
): NurseryData {
  const r = createRng(seed);

  const zonaList = topologia ? zonaDefsFor(topologia.macroZonas) : zonaDefs;
  const sectoresPorZona = topologia ? topologia.sectoresPorMacroZona : 100;
  const offlineOnly = topologia != null;
  const macroZonasCount = zonaList.length;

  // Disposición visual acotada a la grilla (defaults reproducen la presentación previa).
  // Prioridad: disposición explícita > la incluida en la topología > defaults.
  const layout = {
    macroZonasPorFila: clampDisposicion(
      disposicion?.macroZonasPorFila ?? topologia?.macroZonasPorFila ?? DEFAULT_MACRO_ZONAS_POR_FILA,
      macroZonasCount,
    ),
    sectoresPorFila: clampDisposicion(
      disposicion?.sectoresPorFila ?? topologia?.sectoresPorFila ?? DEFAULT_SECTORES_POR_FILA,
      sectoresPorZona,
    ),
  };

  const sectors: Sector[] = [];
  const byId: Record<string, Sector> = {};
  const zonas: Zona[] = zonaList.map((z) => {
    const list: Sector[] = [];
    let sano = 0;
    let alerta = 0;
    let off = 0;
    for (let i = 1; i <= sectoresPorZona; i++) {
      let status: Status = 'ok';
      if (offlineOnly) {
        status = 'offline';
      } else {
        const u = r();
        if (u > 0.978) status = 'offline';
        else if (u > 0.94) status = 'critical';
        else if (u > 0.83) status = 'warning';
      }
      const s = makeSector(z, i, status, r);
      list.push(s);
      sectors.push(s);
      byId[s.id] = s;
      if (status === 'ok') sano++;
      else if (status === 'offline') off++;
      else alerta++;
    }
    return { ...z, sectors: list, sano, alerta, off, total: sectoresPorZona };
  });

  // stats
  const cnt = (st: Status) => sectors.filter((s) => s.status === st).length;
  const total = sectors.length;
  const sano = cnt('ok');
  const warning = cnt('warning');
  const critical = cnt('critical');
  const offline = cnt('offline');
  const stats = {
    total,
    sano,
    warning,
    critical,
    offline,
    alerta: warning + critical,
    sanoPct: total > 0 ? Math.round((sano / total) * 100) : 0,
    actToday: offlineOnly ? 0 : 41,
    actRiego: offlineOnly ? 0 : 28,
    actInsumo: offlineOnly ? 0 : 7,
    actSombra: offlineOnly ? 0 : 6,
    diagCount: 0,
  };

  // prioridad
  const order: Record<string, number> = { critical: 0, warning: 1 };
  const priority: PriorityItem[] = sectors
    .filter((s) => s.status === 'critical' || s.status === 'warning')
    .sort((a, b) => order[a.status] - order[b.status] || a.id.localeCompare(b.id))
    .slice(0, 7)
    .map((s) => ({
      id: s.id,
      color: s.color,
      reason: s.reason,
      sev: s.diagnosis.sev,
      sevSoft: sevMap[s.diagnosis.sev].soft,
      sevInk: sevMap[s.diagnosis.sev].ink,
      pulse: s.status === 'critical' ? 'ybPulse 2s infinite' : 'none',
    }));

  // diagnósticos
  let dn = 0;
  const diagnoses: DiagnosisCard[] = sectors
    .filter((s) => s.diagnosis.estado !== 'Sano' && s.diagnosis.estado !== 'Sin diagnóstico')
    .map((s) => {
      dn++;
      const d = s.diagnosis;
      return {
        id: 'DG-' + String(dn).padStart(3, '0'),
        sectorId: s.id,
        zonaName: s.zonaName,
        estado: d.estado,
        conf: d.conf as number,
        sev: d.sev,
        sevSoft: sevMap[d.sev].soft,
        sevInk: sevMap[d.sev].ink,
        thumb: tints[d.estado] || tints['Sin diagnóstico'],
        time: s.ago,
        concluyente: (d.conf as number) >= 85,
      };
    });
  // entradas "No concluyente" adicionales
  const ncSectors = sectors.filter((s) => s.status === 'warning').slice(0, 3);
  ncSectors.forEach((s) => {
    dn++;
    diagnoses.push({
      id: 'DG-' + String(dn).padStart(3, '0'),
      sectorId: s.id,
      zonaName: s.zonaName,
      estado: 'No concluyente',
      conf: Math.round(rr(r, 62, 83)),
      sev: '—',
      sevSoft: sevMap['—'].soft,
      sevInk: sevMap['—'].ink,
      thumb: tints['No concluyente'],
      time: s.ago,
      concluyente: false,
    });
  });
  diagnoses.sort((a, b) => a.time.localeCompare(b.time));
  stats.diagCount = diagnoses.length;
  const diagById: Record<string, DiagnosisCard> = {};
  diagnoses.forEach((d) => (diagById[d.id] = d));
  const recentDiag = diagnoses.filter((d) => d.estado !== 'No concluyente').slice(0, 5);

  // feed de acciones
  const actSrc = sectors.filter((s) => s.status !== 'ok' && s.status !== 'offline');
  const actions: ActionEvent[] = actTpl.map((a, i) => {
    const sec = actSrc[i % Math.max(1, actSrc.length)];
    const meta = ACT[a.tipo];
    const rm = resMap[a.res];
    return {
      title: a.title + ' · ' + (sec ? sec.id : 'MZ-2-014'),
      detail: a.d,
      time: a.t,
      result: a.res,
      resSoft: rm.soft,
      resInk: rm.ink,
      tint: meta.tint,
      ink: meta.ink,
      path: meta.path,
    };
  });

  // alertas — un vivero recién generado (offline) aún no tiene alertas activas.
  const alerts: Alert[] = offlineOnly
    ? []
    : (
    [
      { level: 'CRITICAL', color: C.critical, time: '14:08', sectorId: priority[0] ? priority[0].id : 'MZ-3-077', msg: 'Daño fúngico confirmado + humedad de sustrato 84%. Dosificación de fungicida en curso.' },
      { level: 'CRITICAL', color: C.critical, time: '13:41', sectorId: 'MZ-5-042', msg: 'Falla hidráulica: caudalímetro sin flujo tras abrir electroválvula. Sector marcado para revisión.' },
      { level: 'WARNING', color: C.warning, time: '13:20', sectorId: 'MZ-2-091', msg: 'Nodo testigo con batería baja (18%). Recambio preventivo sugerido.' },
      { level: 'WARNING', color: C.warning, time: '12:55', sectorId: priority[2] ? priority[2].id : 'MZ-1-033', msg: 'Clorosis detectada (confianza 88%). A la espera de validación de dosis nutricional.' },
      { level: 'WARNING', color: C.warning, time: '11:30', sectorId: 'MZ-4-005', msg: 'Sensor sin reporte hace 2 h — señal intermitente. Mostrando último dato conocido.' },
    ] as Array<Omit<Alert, 'read'>>
  ).map((a) => ({ ...a, read: false }));

  const weather: Weather = {
    tempC: 21,
    cond: 'Parcial nublado',
    hum: 78,
    uv: 7,
    uvLabel: 'Alto',
    rainText: 'Lluvia probable en ~3 h — riego autónomo pospuesto en 2 macro-zonas.',
    forecast: [
      { t: '15 h', uv: 7, rain: 10 },
      { t: '18 h', uv: 3, rain: 60 },
      { t: '21 h', uv: 0, rain: 80 },
      { t: 'Mañana', uv: 6, rain: 25 },
    ],
  };

  return {
    zonas,
    sectors,
    byId,
    stats,
    priority,
    diagnoses,
    diagById,
    recentDiag,
    actions,
    alerts,
    weather,
    sevMap: sevMap as Record<Severity, { soft: string; ink: string }>,
    tints,
    specs,
    layout,
  };
}
