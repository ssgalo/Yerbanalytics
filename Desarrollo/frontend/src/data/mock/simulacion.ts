/* ============================================================
   Reflejo de un envío de telemetría en el mock (VITE_DATA_SOURCE=mock).
   No hay broker: al enviar una lectura se actualiza la LECTURA DE LA
   MACRO-ZONA del sensor (una sola, como en la ingesta real) y de ahí se
   re-deriva el estado de sus sectores, más los agregados de zona y las
   stats globales. Devuelve un NurseryData NUEVO para que el poll del
   NurseryProvider lo detecte por identidad y re-renderice.
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
import { metricStatus, peor, pisoDeZona } from './generators';
import { C, LAB, specs } from './specs';

/**
 * Reconstruye las 10 métricas de la zona combinando su última lectura con los valores
 * enviados. Sólo se sobrescriben las presentes en el envío; las ausentes conservan el
 * valor actual, igual que la ingesta real.
 */
function metricsDesdeEnvio(zona: Zona, envio: EnvioTelemetria): Metric[] {
  const enviados: Record<string, number | undefined> = { ...envio.metrics };
  const actuales: Record<string, number | null> = {};
  for (const m of zona.lectura.metrics) actuales[m.key] = m.raw;

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

/**
 * Estado propio del plantín, deducido de su diagnóstico de IA. Es lo que diferencia a un
 * sector de otro dentro de una misma macro-zona: la lectura la comparten, el diagnóstico no.
 */
function estadoDelPlantin(sector: Sector): Status {
  if (sector.diagnosis.sev === 'Alta') return 'critical';
  if (sector.diagnosis.sev === 'Media') return 'warning';
  return 'ok';
}

/** Aplica el estado derivado a un sector, devolviendo una copia actualizada. */
function actualizarSector(sector: Sector, metrics: Metric[], piso: Status): Sector {
  // El nodo del propio sector sigue caído: una lectura de la zona no lo revive.
  if (sector.status === 'offline') return sector;

  const propio = estadoDelPlantin(sector);
  const status = peor(piso, propio);
  const reason =
    status === 'ok'
      ? 'Todos los parámetros en rango óptimo'
      : (() => {
          // Si lo que degradó al sector fue el ambiente, se cita la métrica; si fue el
          // plantín, su diagnóstico.
          if (piso === 'ok') return sector.diagnosis.estado;
          const decisivas = metrics.filter((m) => m.spec.afectaEstado);
          const bad =
            decisivas.find((m) => m.status === piso) ?? decisivas.find((m) => m.status !== 'ok');
          return bad
            ? `${bad.label} ${bad.value}${bad.unit === '%' ? '%' : ' ' + bad.unit}`
            : LAB[status];
        })();

  return {
    ...sector,
    status,
    color: C[status],
    statusLabel: LAB[status],
    tip: reason,
    reason,
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
 * Devuelve un NurseryData nuevo con la lectura de la macro-zona del sensor actualizada y
 * el estado de sus sectores re-derivado. Si la zona no existe, devuelve el dataset sin
 * cambios.
 */
export function aplicarEnvioMock(nursery: NurseryData, envio: EnvioTelemetria): NurseryData {
  const zonaPrev = nursery.zonas.find((z) => z.id === envio.zonaId);
  if (!zonaPrev) return nursery;

  // Una sola lectura para toda la zona, como en la ingesta real.
  const metrics = metricsDesdeEnvio(zonaPrev, envio);
  const piso = pisoDeZona(metrics);
  const ts = envio.timestamp ?? Date.now();
  const lectura = { metrics, ts, ago: 'hace instantes', stale: false };
  const battery = envio.battery ?? zonaPrev.nodo.battery;
  const nodo = {
    mac: envio.serial || zonaPrev.nodo.mac,
    battery,
    signal: envio.signal ?? zonaPrev.nodo.signal,
    bateriaBaja: battery != null && battery < 20,
  };

  const sectors = nursery.sectors.map((s) =>
    s.zona === envio.zonaId ? actualizarSector(s, metrics, piso) : s,
  );
  const byId: Record<string, Sector> = {};
  for (const s of sectors) byId[s.id] = s;

  const zonas = nursery.zonas.map((z) =>
    z.id === envio.zonaId
      ? recalcularZona({ ...z, sectors: z.sectors.map((s) => byId[s.id] ?? s), lectura, nodo })
      : z,
  );

  const stats = recalcularStats(nursery.stats, sectors);

  return { ...nursery, sectors, byId, zonas, stats };
}
