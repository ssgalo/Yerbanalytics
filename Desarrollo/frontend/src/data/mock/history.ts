/* ============================================================
   Generador determinístico del historial de acciones (HU-11/HU-12).
   Mismo enfoque que el resto del mock: RNG sembrado → salida estable.
   El contrato (ActionRecord) es idéntico al DTO HistorialEvento del backend.
   ============================================================ */
import { createRng, pick, rr } from '@/lib/rng';
import type { ActionRecord, Evolution, NivelAlerta, Severity } from '@/types/domain';
import { ACT, resMap, zonaDefs } from './specs';

/** Colores del veredicto del seguimiento post-acción. */
const VERDICT: Record<string, { soft: string; ink: string }> = {
  Efectiva: { soft: '#E7F1EA', ink: '#2E7A4F' },
  'En seguimiento': { soft: '#F3ECDD', ink: '#8A6A22' },
  'Sin efectividad': { soft: '#FBE6E0', ink: '#A8331C' },
};

type EvoKind = 'efectiva' | 'sin' | 'seguimiento' | null;

interface Scenario {
  tipo: string;
  lectura: string;
  decision: string;
  accion: string;
  res: string;
  sev: Severity;
  evo: EvoKind;
  /** valor "antes" de la métrica para el seguimiento. */
  antes?: number;
  /** Regla que ordenó la acción (nombre de clase en el backend). */
  regla?: string;
  /** Riego ordenado: volumen (L) y duración de apertura (s). */
  volumenL?: number;
  duracionSeg?: number;
  /** Alerta de macro-zona: nivel. El evento no pertenece a un sector (`sectorId` "—"). */
  alerta?: NivelAlerta;
}

/** Escenarios canónicos de la operación autónoma del vivero. */
const SCENARIOS: Scenario[] = [
  {
    tipo: 'Riego',
    lectura: 'Humedad de sustrato 38% (regla RiegoPorDeficitRule).',
    decision: 'El motor de reglas ordena regar 5,4 L durante 648 s.',
    accion: 'Electroválvula abierta · riego autónomo en curso.',
    res: 'Efectiva',
    sev: 'Media',
    evo: 'efectiva',
    antes: 38,
    regla: 'RiegoPorDeficitRule',
    volumenL: 5.4,
    duracionSeg: 648,
  },
  {
    tipo: 'Riego',
    lectura: 'Humedad de sustrato 31% (regla DeficitCriticoRule).',
    decision: 'El motor de reglas ordena regar 6 L durante 720 s (déficit crítico: volumen máximo, a cualquier hora).',
    accion: 'Electroválvula abierta · riego autónomo en curso.',
    res: 'Efectiva',
    sev: 'Alta',
    evo: 'efectiva',
    antes: 31,
    regla: 'DeficitCriticoRule',
    volumenL: 6,
    duracionSeg: 720,
  },
  {
    tipo: 'Alerta',
    lectura: 'Alerta de la macro-zona (regla DeficitCriticoRule).',
    decision: 'Déficit hídrico crítico',
    accion: 'Alerta CRITICAL registrada para el operador.',
    res: 'Informativo',
    sev: '—',
    evo: null,
    regla: 'DeficitCriticoRule',
    alerta: 'CRITICAL',
  },
  {
    tipo: 'Insumo',
    lectura: 'Diagnóstico IA: Daño fúngico (confianza 93%) + sustrato sobre 80%.',
    decision: 'Confianza sobre el umbral: el motor habilita la dosificación de fungicida.',
    accion: 'Bomba peristáltica inyectó 4,5 ml de fungicida en la línea del sector.',
    res: 'En seguimiento',
    sev: 'Alta',
    evo: 'seguimiento',
    antes: 82,
  },
  {
    tipo: 'Mediasombra',
    lectura: 'Plan de rustificación día 12.',
    decision: 'Avance del cronograma: apertura gradual programada.',
    accion: 'Cobertura ajustada de 35% a 45% por plan de rustificación.',
    res: 'Efectiva',
    sev: '—',
    evo: null,
  },
  {
    tipo: 'Insumo',
    lectura: 'Diagnóstico IA: Daño fúngico (confianza 90%) persistente.',
    decision: 'Segunda dosificación habilitada por el motor de reglas.',
    accion: 'Bomba peristáltica inyectó 4,0 ml de fungicida en la línea del sector.',
    res: 'En seguimiento',
    sev: 'Alta',
    evo: 'sin',
    antes: 84,
  },
  {
    tipo: 'Riego',
    lectura: 'Déficit hídrico detectado (humedad 40%).',
    decision: 'Lluvia prevista (probabilidad máxima 80% y 8 mm en las próximas 4 h): se pospone el riego.',
    accion: 'Riego pospuesto para evitar saturación hídrica del sustrato.',
    res: 'Pospuesta',
    sev: '—',
    evo: null,
    regla: 'PosponerPorLluviaRule',
  },
  {
    tipo: 'Alerta',
    lectura: 'Alerta de la macro-zona (regla PosponerPorLluviaRule).',
    decision: 'Riego pospuesto por pronóstico de lluvia (probabilidad máxima 80% y 8 mm en las próximas 4 h)',
    accion: 'Alerta INFO registrada para el operador.',
    res: 'Informativo',
    sev: '—',
    evo: null,
    regla: 'PosponerPorLluviaRule',
    alerta: 'INFO',
  },
  {
    tipo: 'Alerta',
    lectura: 'Alerta de la macro-zona (regla SustratoSaturadoRule).',
    decision: 'Sustrato saturado, riesgo de asfixia radicular y hongos',
    accion: 'Alerta WARNING registrada para el operador.',
    res: 'Informativo',
    sev: '—',
    evo: null,
    regla: 'SustratoSaturadoRule',
    alerta: 'WARNING',
  },
  {
    tipo: 'Mediasombra',
    lectura: 'Pico de radiación UV 9 detectado por el sensor local.',
    decision: 'Umbral de estrés UV superado: el motor retrae la mediasombra.',
    accion: 'Cobertura llevada a 70% para proteger los plantines.',
    res: 'Efectiva',
    sev: '—',
    evo: null,
  },
  {
    tipo: 'Riego',
    lectura: 'Sensor testigo sin reporte hace más de 2 h.',
    decision: 'Sin telemetría confiable: se anula la actuación autónoma por seguridad.',
    accion: 'Riego abortado para evitar inundación a ciegas.',
    res: 'Abortada',
    sev: '—',
    evo: null,
    regla: 'StaleSensorRule',
  },
  {
    tipo: 'Riego',
    lectura: 'Humedad de sustrato 40% (regla RiegoPorDeficitRule).',
    decision: 'El motor de reglas ordena regar 5 L durante 600 s.',
    accion: 'Electroválvula abierta · riego autónomo en curso.',
    res: 'Efectiva',
    sev: 'Media',
    evo: 'efectiva',
    antes: 40,
    regla: 'RiegoPorDeficitRule',
    volumenL: 5,
    duracionSeg: 600,
  },
  {
    tipo: 'Insumo',
    lectura: 'Diagnóstico IA: Clorosis por déficit nutricional (confianza 89%).',
    decision: 'Confianza sobre el umbral: el motor habilita la dosificación de nutrientes.',
    accion: 'Bomba peristáltica inyectó 3,0 ml de NPK en la línea del sector.',
    res: 'En seguimiento',
    sev: 'Media',
    evo: 'efectiva',
    antes: 30,
  },
  {
    tipo: 'Insumo',
    lectura: 'Posible plaga foliar detectada (confianza 82%).',
    decision: 'Confianza bajo el umbral del 85%: no se inyecta químico.',
    accion: 'Dosificación bloqueada · solicitada validación visual humana.',
    res: 'Abortada',
    sev: '—',
    evo: null,
  },
  {
    tipo: 'Insumo',
    lectura: 'Daño fúngico confirmado: el sector ya recibió la dosis máxima en 24 h.',
    decision: 'Límite químico diario alcanzado: el motor bloquea la aplicación.',
    accion: 'Dosificación bloqueada para evitar intoxicar la planta.',
    res: 'Abortada',
    sev: 'Alta',
    evo: null,
  },
  {
    tipo: 'Mediasombra',
    lectura: 'Plan de rustificación día 11.',
    decision: 'Avance del cronograma: apertura gradual programada.',
    accion: 'Cobertura ajustada de 25% a 35% por plan de rustificación.',
    res: 'Efectiva',
    sev: '—',
    evo: null,
  },
];

/** 'hace X' relativo a ahora. */
function formatAgo(ts: number): string {
  const sec = Math.max(0, Math.floor((Date.now() - ts) / 1000));
  if (sec < 60) return `hace ${sec} s`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `hace ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `hace ${h} h`;
  return `hace ${Math.floor(h / 24)} d`;
}

/** 'dd/MM HH:mm' para el filtro por rango de fechas. */
function formatFecha(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** Construye el bloque de seguimiento post-acción según el tipo de evolución. */
function buildEvo(kind: EvoKind, antes: number | undefined, r: () => number): Evolution | null {
  if (!kind || antes === undefined) return null;

  if (kind === 'seguimiento') {
    const v = VERDICT['En seguimiento'];
    return {
      show: true,
      metric: 'Humedad de sustrato',
      antes: String(antes),
      ahora: '—',
      unit: '%',
      delta: '—',
      latencia: '2 min',
      verdict: 'En seguimiento',
      vSoft: v.soft,
      vInk: v.ink,
    };
  }

  const efectiva = kind === 'efectiva';
  const deltaAbs = efectiva ? Math.round(rr(r, 12, 20)) : Math.round(rr(r, 1, 3));
  const signo = antes < 50 ? 1 : -1; // riego sube humedad; insumo sobre sustrato alto la baja
  const delta = signo * deltaAbs;
  const ahora = antes + delta;
  const verdict = efectiva ? 'Efectiva' : 'Sin efectividad';
  const v = VERDICT[verdict];
  return {
    show: true,
    metric: 'Humedad de sustrato',
    antes: String(antes),
    ahora: String(ahora),
    unit: '%',
    delta: `${delta >= 0 ? '+' : ''}${delta}`,
    latencia: '2 min',
    verdict,
    vSoft: v.soft,
    vInk: v.ink,
  };
}

/** Genera el historial completo de acciones de forma determinística. */
export function buildHistory(seed: number): ActionRecord[] {
  const r = createRng(seed ^ 0x48495354); // 'HIST' — RNG propio, independiente del vivero
  const out: ActionRecord[] = [];

  // ~3 vueltas a los escenarios → historial denso y variado.
  const total = SCENARIOS.length * 3;
  let cursorMs = 4 * 60 * 1000; // el más reciente: hace ~4 min

  for (let i = 0; i < total; i++) {
    const sc = SCENARIOS[i % SCENARIOS.length];
    const z = pick(r, zonaDefs);
    const n = 1 + Math.floor(r() * 100);
    // Las alertas son de la macro-zona, no de un sector: el backend las guarda con sectorId "—".
    const sectorId = sc.alerta ? '—' : `${z.id}-${String(n).padStart(3, '0')}`;
    const ts = Date.now() - cursorMs;
    cursorMs += Math.round(rr(r, 18, 75)) * 60 * 1000; // separa los eventos en el tiempo

    const rm = resMap[sc.res] ?? { soft: '#EEEDE5', ink: '#6A776E' };
    const meta = ACT[sc.tipo] ?? ACT.Riego;

    out.push({
      id: `HE-${String(i + 1).padStart(3, '0')}`,
      sectorId,
      zonaName: z.name,
      tipo: sc.tipo,
      time: formatAgo(ts),
      ts,
      fecha: formatFecha(ts),
      lectura: sc.alerta ? sc.lectura.replace('de la macro-zona', `de la macro-zona ${z.id}`) : sc.lectura,
      decision: sc.decision,
      accion: sc.accion,
      res: sc.res,
      resSoft: rm.soft,
      resInk: rm.ink,
      sev: sc.sev,
      tint: meta.tint,
      ink: meta.ink,
      path: meta.path,
      evo: buildEvo(sc.evo, sc.antes, r),
      regla: sc.regla ?? null,
      alerta: sc.alerta ?? null,
      volumenL: sc.volumenL ?? null,
      duracionSeg: sc.duracionSeg ?? null,
    });
  }

  return out;
}
