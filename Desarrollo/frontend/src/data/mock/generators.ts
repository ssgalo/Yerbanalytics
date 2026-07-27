/* ============================================================
   Generador del vivero. El ORDEN de las llamadas a r() define la
   salida: NO reordenar.

   La lectura sensada se genera UNA vez por macro-zona (hay un solo
   nodo testigo por MZ) y de ahí sale el "piso" de estado de sus 100
   sectores. Lo que diferencia a un sector de otro dentro de la misma
   zona es el diagnóstico de IA de su plantín.
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

/** Severidad relativa, para combinar el estado de la zona con el del plantín. */
export const SEVERIDAD: Record<Status, number> = { ok: 0, warning: 1, critical: 2, offline: 3 };

/** El más severo de dos estados. */
export function peor(a: Status, b: Status): Status {
  return SEVERIDAD[a] >= SEVERIDAD[b] ? a : b;
}

/**
 * Lectura del nodo testigo de una macro-zona: las 10 métricas evaluadas contra sus bandas.
 * `sesgo` fuerza a UNA métrica fuera de rango, para que el vivero demo tenga zonas con
 * condiciones ambientales degradadas y no sólo lecturas perfectas.
 */
function makeLectura(r: () => number, sesgo: Status): Metric[] {
  const offIdx = sesgo === 'warning' || sesgo === 'critical' ? Math.floor(r() * specs.length) : -1;
  return specs.map((sp, mi) => {
    let v: number;
    if (mi === offIdx && sesgo === 'warning') {
      v =
        r() > 0.5
          ? rr(r, sp.warn[1] - (sp.warn[1] - sp.ideal[1]) * 0.5, sp.warn[1])
          : rr(r, sp.warn[0], sp.ideal[0]);
    } else if (mi === offIdx && sesgo === 'critical') {
      v = r() > 0.5 ? rr(r, sp.warn[1], sp.crit[1]) : rr(r, sp.crit[0], sp.warn[0]);
    } else {
      v = rr(
        r,
        sp.ideal[0] + (sp.ideal[1] - sp.ideal[0]) * 0.12,
        sp.ideal[1] - (sp.ideal[1] - sp.ideal[0]) * 0.12,
      );
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
}

/** Lectura vacía: el nodo de la zona nunca reportó (o dejó de hacerlo). */
function offlineMetrics(): Metric[] {
  return specs.map((sp) => ({
    key: sp.key,
    label: sp.label,
    unit: sp.unit,
    raw: null,
    value: '—',
    status: 'offline' as Status,
    color: C.offline,
    spec: sp,
  }));
}

/** MAC estable del nodo testigo de una macro-zona, derivada de su id (MZ-3 → …:03). */
function macDeZona(zonaId: string): string {
  const n = Number(zonaId.replace(/\D/g, '')) || 0;
  return 'A4:CF:12:9A:00:' + String(n).padStart(2, '0');
}

/**
 * Estado que la lectura de la zona impone a TODOS sus sectores. Sólo cuentan las métricas
 * no informativas: las de la sonda de suelo tienen rangos provisionales y no deben mover
 * el estado hasta validarlos.
 */
export function pisoDeZona(metrics: Metric[]): Status {
  const decisivas = metrics.filter((m) => m.spec.afectaEstado);
  if (decisivas.some((m) => m.status === 'critical')) return 'critical';
  if (decisivas.some((m) => m.status === 'warning')) return 'warning';
  return 'ok';
}

/**
 * Genera un sector: diagnóstico del plantín y actuadores.
 *
 * El sector NO tiene métricas propias — las comparte con su macro-zona. Su estado combina
 * dos causas independientes: el `piso` ambiental que impone la lectura de la zona, y el
 * estado del plantín según el diagnóstico de IA, que sí es individual (un plantín puede
 * tener daño fúngico con el ambiente perfecto).
 */
function makeSector(
  z: (typeof zonaDefs)[number],
  i: number,
  piso: Status,
  metrics: Metric[],
  r: () => number,
): Sector {
  const id = z.id + '-' + String(i).padStart(3, '0');

  // Estado propio del plantín, independiente del ambiente.
  let plantin: Status = 'ok';
  if (piso !== 'offline') {
    const u = r();
    if (u > 0.985)
      plantin = 'offline'; // nodo del sector sin responder
    else if (u > 0.955) plantin = 'critical';
    else if (u > 0.9) plantin = 'warning';
  }
  const finalStatus: Status = peor(piso, plantin);

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

  // razón: si lo que degradó al sector fue el ambiente, se cita la métrica; si fue el
  // plantín, el diagnóstico.
  let reason: string;
  if (finalStatus === 'offline') {
    reason = 'Sin reporte de telemetría · señal perdida';
  } else if (finalStatus === 'ok') {
    reason = 'Todos los parámetros en rango óptimo';
  } else if (SEVERIDAD[piso] >= SEVERIDAD[plantin]) {
    const bad =
      metrics.find((m) => m.spec.afectaEstado && m.status === finalStatus) ??
      metrics.find((m) => m.spec.afectaEstado && m.status !== 'ok');
    reason = bad
      ? bad.label + ' ' + bad.value + (bad.unit === '%' ? '%' : ' ' + bad.unit)
      : diagnosis.estado;
  } else {
    reason = diagnosis.estado;
  }

  // actuadores — el riego responde a la humedad de sustrato de la zona
  const humSus = metrics.find((m) => m.key === 'humSus')?.raw;
  const valve = finalStatus !== 'offline' && humSus != null && humSus < 42 ? 'Regando' : 'Cerrada';
  const pump =
    finalStatus === 'critical' && diagnosis.conf && diagnosis.conf >= 85
      ? 'Dosificando'
      : 'En espera';
  const shadePct = Math.round(rr(r, 30, 65) / 5) * 5;

  return {
    id,
    zona: z.id,
    zonaName: z.name,
    n: i,
    status: finalStatus,
    color: C[finalStatus],
    statusLabel: LAB[finalStatus],
    tip: id + ' · ' + LAB[finalStatus],
    diagnosis,
    reason,
    actuadores: { valve, pump, shade: shadePct },
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
      disposicion?.macroZonasPorFila ??
        topologia?.macroZonasPorFila ??
        DEFAULT_MACRO_ZONAS_POR_FILA,
      macroZonasCount,
    ),
    sectoresPorFila: clampDisposicion(
      disposicion?.sectoresPorFila ?? topologia?.sectoresPorFila ?? DEFAULT_SECTORES_POR_FILA,
      sectoresPorZona,
    ),
  };

  const sectors: Sector[] = [];
  const byId: Record<string, Sector> = {};
  /** Antigüedad de la lectura por zona: los diagnósticos la usan como sello temporal. */
  const agoPorZona: Record<string, string> = {};

  const zonas: Zona[] = zonaList.map((z) => {
    // Lectura del nodo testigo: una por macro-zona, compartida por sus 100 sectores.
    // Un vivero recién generado por topología todavía no tiene nodos reportando.
    const u = r();
    const sesgo: Status = offlineOnly
      ? 'offline'
      : u > 0.94
        ? 'critical'
        : u > 0.75
          ? 'warning'
          : 'ok';
    const metrics = offlineOnly ? offlineMetrics() : makeLectura(r, sesgo);
    const piso: Status = offlineOnly ? 'offline' : pisoDeZona(metrics);

    const ago = offlineOnly ? 'hace —' : 'hace ' + Math.round(rr(r, 4, 28)) + ' min';
    agoPorZona[z.id] = ago;
    const lectura = {
      metrics,
      ts: offlineOnly ? null : Date.now() - Math.round(rr(r, 4, 28)) * 60_000,
      ago,
      stale: offlineOnly,
    };
    const battery = offlineOnly ? null : Math.round(rr(r, 14, 98));
    const nodo = {
      mac: offlineOnly ? null : macDeZona(z.id),
      battery,
      signal: offlineOnly ? null : -Math.round(rr(r, 52, 89)),
      bateriaBaja: battery != null && battery < 20,
    };

    const list: Sector[] = [];
    let sano = 0;
    let alerta = 0;
    let off = 0;
    for (let i = 1; i <= sectoresPorZona; i++) {
      const s = makeSector(z, i, piso, metrics, r);
      list.push(s);
      sectors.push(s);
      byId[s.id] = s;
      if (s.status === 'ok') sano++;
      else if (s.status === 'offline') off++;
      else alerta++;
    }
    return { ...z, sectors: list, sano, alerta, off, total: sectoresPorZona, lectura, nodo };
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
        time: agoPorZona[s.zona] ?? 'hace —',
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
      time: agoPorZona[s.zona] ?? 'hace —',
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
          {
            level: 'CRITICAL',
            color: C.critical,
            time: '14:08',
            sectorId: priority[0] ? priority[0].id : 'MZ-3-077',
            msg: 'Daño fúngico confirmado + humedad de sustrato 84%. Dosificación de fungicida en curso.',
          },
          {
            level: 'CRITICAL',
            color: C.critical,
            time: '13:41',
            sectorId: 'MZ-5-042',
            msg: 'Falla hidráulica: caudalímetro sin flujo tras abrir electroválvula. Sector marcado para revisión.',
          },
          {
            level: 'WARNING',
            color: C.warning,
            time: '13:20',
            sectorId: 'MZ-2-091',
            msg: 'Nodo testigo con batería baja (18%). Recambio preventivo sugerido.',
          },
          {
            level: 'WARNING',
            color: C.warning,
            time: '12:55',
            sectorId: priority[2] ? priority[2].id : 'MZ-1-033',
            msg: 'Clorosis detectada (confianza 88%). A la espera de validación de dosis nutricional.',
          },
          {
            level: 'WARNING',
            color: C.warning,
            time: '11:30',
            sectorId: 'MZ-4-005',
            msg: 'Sensor sin reporte hace 2 h — señal intermitente. Mostrando último dato conocido.',
          },
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
