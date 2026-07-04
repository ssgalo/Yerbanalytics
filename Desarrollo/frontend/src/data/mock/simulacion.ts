/* ============================================================
   Reflejo de un envío de telemetría en el mock (VITE_DATA_SOURCE=mock).
   No hay broker: al enviar una lectura se actualizan los sectores de la
   macro-zona del sensor con los nuevos valores y su estado derivado (con las
   mismas bandas `specs` que el backend), y se recalculan los agregados de
   zona y las stats globales. Devuelve un NurseryData NUEVO para que el poll
   del NurseryProvider lo detecte por identidad y re-renderice.
   ============================================================ */
import type {
  EnvioTelemetria,
  Metric,
  NurseryData,
  Sector,
  Stats,
  Status,
  Zona,
} from '@/types/domain';
import { metricStatus } from './generators';
import { C, LAB, specs } from './specs';

/**
 * Reconstruye las 5 métricas de un sector combinando su último valor con los valores
 * enviados. Sólo se sobrescriben las métricas presentes en el envío (parcial o completo);
 * las ausentes conservan el valor actual del sector, igual que la ingesta real.
 */
function metricsDesdeEnvio(sector: Sector, envio: EnvioTelemetria): Metric[] {
  const enviados: Record<string, number | undefined> = {
    humSus: envio.metrics.humSus,
    humAmb: envio.metrics.humAmb,
    temp: envio.metrics.temp,
    ce: envio.metrics.ce,
    uv: envio.metrics.uv,
  };
  const actuales: Record<string, number> = {};
  for (const m of sector.metrics) actuales[m.key] = m.raw;
  return specs.map((sp) => {
    const raw = enviados[sp.key] ?? actuales[sp.key] ?? sp.base;
    const ms = metricStatus(raw, sp);
    return {
      key: sp.key,
      label: sp.label,
      unit: sp.unit,
      raw,
      value: raw.toFixed(sp.dec),
      status: ms,
      color: C[ms],
      spec: sp,
    };
  });
}

/** Estado global del sector: el peor de sus métricas (una lectura enviada nunca es offline). */
function statusDeMetrics(metrics: Metric[]): Status {
  if (metrics.some((m) => m.status === 'critical')) return 'critical';
  if (metrics.some((m) => m.status === 'warning')) return 'warning';
  return 'ok';
}

/** Aplica la lectura a un sector, devolviendo una copia actualizada. */
function actualizarSector(sector: Sector, metrics: Metric[], status: Status): Sector {
  const reason =
    status === 'ok'
      ? 'Todos los parámetros en rango óptimo'
      : (() => {
          const bad = metrics.find((m) => m.status === status) ?? metrics.find((m) => m.status !== 'ok');
          return bad ? `${bad.label} ${bad.value}${bad.unit === '%' ? '%' : ' ' + bad.unit}` : LAB[status];
        })();
  const diagnosis: Sector['diagnosis'] =
    status === 'ok'
      ? { estado: 'Sano', conf: 97, sev: '—' }
      : { estado: 'No concluyente', conf: status === 'critical' ? 80 : 72, sev: '—' };
  return {
    ...sector,
    status,
    color: C[status],
    statusLabel: LAB[status],
    tip: reason,
    metrics,
    diagnosis,
    reason,
    ago: 'hace instantes',
    stale: false,
  };
}

/** Recalcula los conteos de una zona a partir de sus sectores. */
function recalcularZona(zona: Zona): Zona {
  let sano = 0;
  let alerta = 0;
  let off = 0;
  for (const s of zona.sectors) {
    if (s.status === 'ok') sano++;
    else if (s.status === 'offline') off++;
    else alerta++;
  }
  return { ...zona, sano, alerta, off };
}

/** Recalcula las stats globales a partir de todos los sectores. */
function recalcularStats(prev: Stats, sectors: Sector[]): Stats {
  const cnt = (st: Status) => sectors.filter((s) => s.status === st).length;
  const total = sectors.length;
  const sano = cnt('ok');
  const warning = cnt('warning');
  const critical = cnt('critical');
  const offline = cnt('offline');
  return {
    ...prev,
    total,
    sano,
    warning,
    critical,
    offline,
    alerta: warning + critical,
    sanoPct: total > 0 ? Math.round((sano / total) * 100) : 0,
  };
}

/**
 * Devuelve un NurseryData nuevo con los sectores de la macro-zona del sensor actualizados
 * según la lectura enviada. Si la zona no existe, devuelve el dataset sin cambios.
 */
export function aplicarEnvioMock(nursery: NurseryData, envio: EnvioTelemetria): NurseryData {
  const enZona = (s: Sector) => s.zona === envio.zonaId;

  const sectors = nursery.sectors.map((s) => {
    if (!enZona(s)) return s;
    // Cada sector combina sus métricas actuales con las enviadas: un envío parcial sólo
    // pisa las presentes, así que el estado se recomputa por sector.
    const metrics = metricsDesdeEnvio(s, envio);
    return actualizarSector(s, metrics, statusDeMetrics(metrics));
  });
  const byId: Record<string, Sector> = {};
  for (const s of sectors) byId[s.id] = s;

  const zonas = nursery.zonas.map((z) =>
    z.id === envio.zonaId
      ? recalcularZona({ ...z, sectors: z.sectors.map((s) => byId[s.id] ?? s) })
      : z,
  );

  const stats = recalcularStats(nursery.stats, sectors);

  return { ...nursery, sectors, byId, zonas, stats };
}
