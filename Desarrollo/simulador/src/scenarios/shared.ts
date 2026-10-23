import type { DiagnosisPlan, ScenarioConflict, ScenarioContext, TelemetryClaim } from './types';

/** Midpoint rounded to one decimal. */
export function mid(a: number, b: number): number {
  return Math.round(((a + b) / 2) * 10) / 10;
}

/**
 * Humidity at which irrigation is due but not urgent (between the critical and the irrigation thresholds).
 * R-01 needs it, and so does R-03, which only has something to postpone when irrigation was due.
 */
export function humidityDueForIrrigation({ thresholds }: ScenarioContext): TelemetryClaim {
  const h = mid(thresholds.umbralCritico, thresholds.umbralHumedad);
  return {
    value: h,
    note: `humSus = ${h}% (entre ${thresholds.umbralCritico}% y ${thresholds.umbralHumedad}%)`,
  };
}

/** Points over the minimum confidence a registered diagnosis carries: clearly valid (P-D1), not on the edge. */
const CONFIDENCE_MARGIN = 15;

/** The AI diagnosis a scenario registers for the rule it makes fire: the state, its severity and a valid confidence. */
export function diagnosisPlan(estado: string, sev: string, minConfidence: number): DiagnosisPlan {
  return { estado, sev, confidence: 'Alta', conf: Math.min(100, minConfidence + CONFIDENCE_MARGIN) };
}

/** Scenarios that make the zone mute or lock it: an application cannot be asked for, so none of the biotic ones can run next to them. */
export const NEED_THE_ZONE_ANSWERING_AND_FREE = ['NODE_OFFLINE', 'LOCK'];

/**
 * Scenarios that register their own AI diagnosis on the target sector, in the order they were written. A run registers one
 * diagnosis per sector, so only one of them can say what the sector has: each scenario declares its conflict with the ones
 * BEFORE it, and the detection is symmetric (a pair is declared once).
 */
const DIAGNOSIS_SCENARIOS = [
  'SUPPLY', 'FUNGAL_DAMAGE', 'FOLIAR_PEST', 'FUNGICIDE_CAP', 'OUTBREAK',
  'CHLOROSIS_NUTRITIONAL', 'CHLOROSIS_NON_NUTRITIONAL', 'SOLAR_STRESS', 'SOLAR_STRESS_UNEXPLAINED',
];

/** The conflicts of a diagnosis scenario with the diagnosis scenarios that come before it. */
export function conflictsWithEarlierDiagnoses(key: string): ScenarioConflict[] {
  return DIAGNOSIS_SCENARIOS.slice(0, DIAGNOSIS_SCENARIOS.indexOf(key)).map((other) => ({
    with: other,
    reason: () =>
      'Los dos escenarios registran su propio diagnóstico en el mismo sector y sólo uno puede decir qué tiene. Ejecutalos por separado.',
  }));
}

/** The conflicts of a biotic scenario with a silent node and with a lock: the application cannot even be asked for. */
export function conflictsWithAZoneThatCannotApply(): ScenarioConflict[] {
  return NEED_THE_ZONE_ANSWERING_AND_FREE.map((other) => ({
    with: other,
    reason: () =>
      'Necesita una zona que conteste el pulso y no esté bloqueada: con el nodo mudo el motor no decide y con un bloqueo no se aplica nada. Ejecutalo por separado.',
  }));
}
