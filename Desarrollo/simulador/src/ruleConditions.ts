/* ============================================================
   Automatización de telemetría para disparo de reglas agronómicas.

   This module is intentionally pure: no fetch calls, no React. It takes the thresholds
   read from the backend and returns what telemetry and weather must be sent so the rule
   engine fires the selected rules. The orchestration (zeroing cooldowns, calling the
   weather endpoint, publishing MQTT) lives in the component.

   It knows NOTHING about any particular rule: each rule's conditions live in its own
   scenario (`scenarios/`). This file only resolves thresholds, detects conflicts between
   the selected scenarios and merges what each one asks for.
   ============================================================ */
import { SCENARIOS } from './scenarios';
import type { DiagnosisPlan, RuleScenario, ScenarioContext, TelemetryClaim, ThresholdMap } from './scenarios/types';
import type { NodeErrorPlan, ParametroRegla, SimulatedWeather } from './types';

export type { DiagnosisPlan, ThresholdMap } from './scenarios/types';

/**
 * Factory values of `reglas_v2` §11, used when the backend catalog does not answer.
 * They only matter in that case: with the backend up, `buildThresholds` reads its current values.
 */
export const DEFAULT_THRESHOLDS: Record<string, number> = {
  'riego.umbral-humedad': 45,
  'riego.umbral-critico': 35,
  'riego.saturacion-bloqueo': 75,
  'riego.saturacion-alerta': 80,
  'riego.lluvia-probabilidad': 70,
  'riego.lluvia-mm': 5,
  'mediasombra.uv-umbral': 7,
  'nutricion.ce-baja': 0.5,
  'biotico.humedad-predisponente': 75,
  'clorosis.ph-ferrica': 6.5,
  'estres.luz-alta': 80,
  'estres.humedad-reposicion': 55,
  'estres.luz-baja': 60,
  'estres.temp-baja': 30,
  'diagnostico.confianza-minima': 70,
  'seguridad.rango-hum-max': 100,
  'seguridad.relecturas-invalidas': 2,
};

/**
 * Each parameter has one copy per backend piece (rediseno-motor-reglas): a threshold is read from the copy of the rule
 * its scenario exercises. `humedadPredisponente` comes from F-F2 because the fungal-condition scenario is the one that
 * needs it, and so on.
 */
const THRESHOLD_SOURCES: Record<Exclude<keyof ThresholdMap, 'confianzaMinima'>, { key: string; rule: string }> = {
  umbralHumedad: { key: 'riego.umbral-humedad', rule: 'RiegoPorDeficitRule' },
  umbralCritico: { key: 'riego.umbral-critico', rule: 'DeficitCriticoRule' },
  saturacionBloqueo: { key: 'riego.saturacion-bloqueo', rule: 'SustratoSaturadoRule' },
  saturacionAlerta: { key: 'riego.saturacion-alerta', rule: 'SustratoSaturadoRule' },
  lluviaProbabilidad: { key: 'riego.lluvia-probabilidad', rule: 'PosponerPorLluviaRule' },
  lluviaMm: { key: 'riego.lluvia-mm', rule: 'PosponerPorLluviaRule' },
  uvUmbral: { key: 'mediasombra.uv-umbral', rule: 'ShadingRule' },
  ceBaja: { key: 'nutricion.ce-baja', rule: 'LowEcRule' },
  humedadPredisponente: { key: 'biotico.humedad-predisponente', rule: 'FungalConditionRule' },
  phFerrica: { key: 'clorosis.ph-ferrica', rule: 'ChlorosisNonNutritionalRule' },
  luzAlta: { key: 'estres.luz-alta', rule: 'SolarStressRule' },
  humedadReposicion: { key: 'estres.humedad-reposicion', rule: 'SolarStressRule' },
  luzBaja: { key: 'estres.luz-baja', rule: 'SolarStressUnexplainedRule' },
  tempBaja: { key: 'estres.temp-baja', rule: 'SolarStressUnexplainedRule' },
  humedadMaxima: { key: 'seguridad.rango-hum-max', rule: 'ReadingValidationGuard' },
  relecturasInvalidas: { key: 'seguridad.relecturas-invalidas', rule: 'ReadingValidationGuard' },
};

const CONFIDENCE_KEY = 'diagnostico.confianza-minima';

const numeric = (p: ParametroRegla | undefined): number => (p ? parseFloat(p.valor) : NaN);

/**
 * The copy of `key` that `rule` owns. Falls back to any copy of the key (an older backend without `regla`, or a rule
 * renamed in code) and then to the factory value.
 */
function copyOf(parametros: ParametroRegla[], key: string, rule: string): number {
  const own = numeric(parametros.find((x) => x.clave === key && x.regla === rule));
  if (!Number.isNaN(own)) return own;
  const any = numeric(parametros.find((x) => x.clave === key));
  return Number.isNaN(any) ? DEFAULT_THRESHOLDS[key] : any;
}

/**
 * Several pieces check the minimum diagnosis confidence (every diagnosis rule and the application preconditions, P-D1),
 * each with its own copy: a diagnosis that clears the strictest one is valid for all of them.
 */
function strictestConfidence(parametros: ParametroRegla[]): number {
  const values = parametros.filter((x) => x.clave === CONFIDENCE_KEY).map(numeric).filter((v) => !Number.isNaN(v));
  return values.length > 0 ? Math.max(...values) : DEFAULT_THRESHOLDS[CONFIDENCE_KEY];
}

export function buildThresholds(parametros: ParametroRegla[]): ThresholdMap {
  const resolved = Object.fromEntries(
    Object.entries(THRESHOLD_SOURCES).map(([field, { key, rule }]) => [field, copyOf(parametros, key, rule)]),
  ) as Omit<ThresholdMap, 'confianzaMinima'>;
  return { ...resolved, confianzaMinima: strictestConfidence(parametros) };
}

export interface RuleConditionResult {
  telemetria: Record<string, number>;
  clima: SimulatedWeather | null;
  /** Diagnosis to register BEFORE the pulse; null if no selected rule needs one. */
  diagnostico: DiagnosisPlan | null;
  /** The same diagnosis registered on several adjacent sectors BEFORE the pulse (an outbreak); null if no scenario asks for it. */
  outbreak: { diagnosis: DiagnosisPlan; sectors: number } | null;
  needsCooldownReset: boolean;
  /** The backend's «sin pronóstico» demo mode must be on before the pulse (O-01…O-03). */
  offlineForecast: boolean;
  /** The target zone must stop answering LEER_AHORA, and no telemetry is published for it. */
  silenceZone: boolean;
  /** Sensing pulses to request, one after the other (at least one). */
  pulses: number;
  /** A manual lock to create over the target before the pulse; null if no selected scenario asks for one. */
  manualLock: { reason: string } | null;
  /** Metrics pinned on the target zone's readings (every reading carries them); empty if no scenario asks for it. */
  pinnedMetrics: Record<string, number>;
  /** The target zone needs a sector with no electrovalve registered (the simulator reads the registry, never edits it). */
  needsSectorWithoutValve: boolean;
  /** Errors the target zone's node publishes before the pulse (S-05), in `applyOrder`; empty if no scenario asks for one. */
  nodeErrors: NodeErrorPlan[];
  /** An error about a sector's valve is published: the operator picks a sector of the zone that has its valve registered. */
  needsSectorWithValve: boolean;
  /** Repair of the zone's faulty devices to register before the pulse; null if no selected scenario asks for it. */
  repair: { detail: string } | null;
  /** The zone must have a device marked as faulty for the scenario to have something to repair. */
  needsFaultyDevice: boolean;
  /** The target zone's input tanks need stock before the pulse (registered if missing, topped up if low). */
  ensureTankStock: boolean;
  /** Bypass keys to turn on before the pulse, by backend rule name; empty if no selected scenario asks for any. */
  bypassRules: Record<string, string[]>;
  conflictos: string[];
  resumen: string[];
}

/** A healthy nursery: what every metric reads when no selected rule asks for something else. */
const HEALTHY_TELEMETRY: Readonly<Record<string, number>> = {
  humSus: 55,
  humAmb: 72,
  temp: 23,
  uv: 50,
  tempSuelo: 20,
  ce: 1.4,
  phSuelo: 5.5,
  n: 150,
  p: 45,
  k: 180,
};

/** A mild day: the weather fields no selected scenario sets. */
const MILD_WEATHER: Readonly<SimulatedWeather> = {
  tempC: 32,
  humedadRelPct: 45,
  probLluviaPct: 5,
  precipMm: 0,
  uvIndex: 5,
};

/** Each incompatible pair once, with the message of the scenario that declared it. */
function detectConflicts(selected: RuleScenario[], ctx: ScenarioContext): string[] {
  const messages: string[] = [];
  for (const a of selected) {
    for (const conflict of a.conflicts ?? []) {
      if (selected.some((b) => b.key === conflict.with)) {
        messages.push(conflict.reason(ctx));
      }
    }
  }
  return messages;
}

/**
 * Given the selected rules and resolved thresholds, returns the telemetry payload and
 * climate conditions needed to make those rules fire. Detects mutually-exclusive combos
 * (e.g. R-02 low humidity vs. R-04 high humidity): with a conflict nothing is applied and
 * `conflictos` says why.
 */
export function calculateConditions(selectedKeys: string[], thresholds: ThresholdMap): RuleConditionResult {
  const ctx: ScenarioContext = { thresholds };
  // Applying in `applyOrder` makes the scenario with the higher order win when two claim the same metric.
  const selected = SCENARIOS.filter((s) => selectedKeys.includes(s.key)).sort((a, b) => a.applyOrder - b.applyOrder);

  const conflictos = detectConflicts(selected, ctx);
  const telemetria: Record<string, number> = { ...HEALTHY_TELEMETRY };
  if (conflictos.length > 0) {
    return {
      telemetria,
      clima: null,
      diagnostico: null,
      outbreak: null,
      needsCooldownReset: false,
      offlineForecast: false,
      silenceZone: false,
      pulses: 1,
      manualLock: null,
      pinnedMetrics: {},
      needsSectorWithoutValve: false,
      nodeErrors: [],
      needsSectorWithValve: false,
      repair: null,
      needsFaultyDevice: false,
      ensureTankStock: false,
      bypassRules: {},
      conflictos,
      resumen: [],
    };
  }

  const claims = new Map<string, TelemetryClaim>();
  const pinnedClaims = new Map<string, TelemetryClaim>();
  let needsSectorWithoutValve = false;
  const nodeErrors: NodeErrorPlan[] = [];
  let needsSectorWithValve = false;
  let repair: { detail: string } | null = null;
  let needsFaultyDevice = false;
  let ensureTankStock = false;
  const bypassRules: Record<string, Set<string>> = {};
  const notes: string[] = [];
  let weather: Partial<SimulatedWeather> | null = null;
  let diagnostico: DiagnosisPlan | null = null;
  let outbreak: { diagnosis: DiagnosisPlan; sectors: number } | null = null;
  let silenceZone = false;
  let offlineForecast = false;
  let pulses = 1;
  let manualLock: { reason: string } | null = null;

  for (const scenario of selected) {
    const patch = scenario.apply(ctx);
    for (const [metric, claim] of Object.entries(patch.telemetry ?? {})) {
      claims.set(metric, claim);
    }
    if (patch.weather) {
      weather = { ...(weather ?? {}), ...patch.weather };
    }
    diagnostico = patch.diagnosis ?? diagnostico;
    outbreak = patch.outbreak ?? outbreak;
    silenceZone = silenceZone || (patch.silenceZone ?? false);
    offlineForecast = offlineForecast || (patch.offlineForecast ?? false);
    pulses = Math.max(pulses, patch.pulses ?? 1);
    manualLock = patch.manualLock ?? manualLock;
    for (const [metric, claim] of Object.entries(patch.pinnedReadings ?? {})) {
      pinnedClaims.set(metric, claim);
    }
    needsSectorWithoutValve = needsSectorWithoutValve || (patch.needsSectorWithoutValve ?? false);
    if (patch.nodeError) nodeErrors.push(patch.nodeError);
    needsSectorWithValve = needsSectorWithValve || (patch.needsSectorWithValve ?? false);
    repair = patch.repair ?? repair;
    needsFaultyDevice = needsFaultyDevice || (patch.needsFaultyDevice ?? false);
    ensureTankStock = ensureTankStock || (patch.ensureTankStock ?? false);
    for (const [rule, keys] of Object.entries(patch.bypassRules ?? {})) {
      bypassRules[rule] = new Set([...(bypassRules[rule] ?? []), ...keys]);
    }
    notes.push(...(patch.notes ?? []));
  }

  claims.forEach((claim, metric) => {
    telemetria[metric] = claim.value;
  });

  const needsCooldownReset = selected.some((s) => s.needsCooldownReset);
  // A line about a metric another scenario overrode is not shown: the preview says what is actually sent.
  const resumen = [...[...claims.values()].map((c) => c.note), ...[...pinnedClaims.values()].map((c) => c.note), ...notes];
  if (needsCooldownReset) {
    resumen.push('Cooldowns → 0 en modo demo antes de disparar el pulso.');
  }

  return {
    telemetria,
    clima: weather ? { ...MILD_WEATHER, ...weather } : null,
    diagnostico,
    outbreak,
    needsCooldownReset,
    offlineForecast,
    silenceZone,
    pulses,
    manualLock,
    pinnedMetrics: Object.fromEntries([...pinnedClaims].map(([metric, claim]) => [metric, claim.value])),
    needsSectorWithoutValve,
    nodeErrors,
    needsSectorWithValve,
    repair,
    needsFaultyDevice,
    ensureTankStock,
    bypassRules: Object.fromEntries(Object.entries(bypassRules).map(([rule, keys]) => [rule, [...keys]])),
    conflictos,
    resumen,
  };
}
