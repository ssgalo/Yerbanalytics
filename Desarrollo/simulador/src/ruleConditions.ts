/* ============================================================
   Automatización de telemetría para disparo de reglas agronómicas.

   This module is intentionally pure: no fetch calls, no React. It takes the thresholds
   read from the backend and returns what telemetry and weather must be sent so the rule
   engine fires the selected rules. The orchestration (zeroing cooldowns, calling the
   weather endpoint, publishing MQTT) lives in the component.

   It is the only place that knows the mapping between rule names and their telemetry
   requirements. If a rule's condition changes, only this file changes.
   ============================================================ */
import type { ParametroRegla, SimulatedWeather } from './types';

export const DEFAULT_THRESHOLDS: Record<string, number> = {
  'riego.umbral-humedad': 45,
  'riego.umbral-critico': 35,
  'riego.saturacion-bloqueo': 75,
  'riego.saturacion-alerta': 80,
  'riego.lluvia-probabilidad': 70,
  'riego.lluvia-mm': 5,
  'mediasombra.uv-umbral': 7,
};

export type RuleKey = 'R01' | 'R02' | 'R04' | 'R03' | 'SHADING' | 'SUPPLY';

export interface RuleInfo {
  key: RuleKey;
  label: string;
  description: string;
  branch: string;
  /** Needs cooldown reset in demo mode to fire immediately. */
  needsCooldown: boolean;
}

export const RULE_CATALOG: RuleInfo[] = [
  {
    key: 'R01',
    label: 'Riego por déficit hídrico (R-01)',
    description: 'Humedad entre el umbral crítico y el umbral de riego',
    branch: 'RIEGO',
    needsCooldown: true,
  },
  {
    key: 'R02',
    label: 'Déficit hídrico crítico (R-02)',
    description: 'Humedad bajo el umbral crítico — riego urgente',
    branch: 'RIEGO',
    needsCooldown: true,
  },
  {
    key: 'R04',
    label: 'Sustrato saturado (R-04)',
    description: 'Humedad sobre el umbral de saturación — bloquea el riego',
    branch: 'RIEGO',
    needsCooldown: false,
  },
  {
    key: 'R03',
    label: 'Posponer por lluvia inminente (R-03)',
    description: 'Condición de riego + alta probabilidad de lluvia',
    branch: 'RIEGO',
    needsCooldown: true,
  },
  {
    key: 'SHADING',
    label: 'Mediasombra por pico UV',
    description: 'Índice UV por encima del umbral — despliega la mediasombra',
    branch: 'MEDIASOMBRA',
    needsCooldown: false,
  },
  {
    key: 'SUPPLY',
    label: 'Dosificación de insumo',
    description: 'Diagnóstico IA crítico pendiente de actuar — requiere un diagnóstico previo',
    branch: 'INSUMO',
    needsCooldown: false,
  },
];

export interface ThresholdMap {
  umbralHumedad: number;
  umbralCritico: number;
  saturacionBloqueo: number;
  saturacionAlerta: number;
  lluviaProbabilidad: number;
  lluviaMm: number;
  uvUmbral: number;
}

export function buildThresholds(parametros: ParametroRegla[]): ThresholdMap {
  const get = (key: string): number => {
    const p = parametros.find((x) => x.clave === key);
    const v = p ? parseFloat(p.valor) : NaN;
    return Number.isNaN(v) ? DEFAULT_THRESHOLDS[key] : v;
  };
  return {
    umbralHumedad: get('riego.umbral-humedad'),
    umbralCritico: get('riego.umbral-critico'),
    saturacionBloqueo: get('riego.saturacion-bloqueo'),
    saturacionAlerta: get('riego.saturacion-alerta'),
    lluviaProbabilidad: get('riego.lluvia-probabilidad'),
    lluviaMm: get('riego.lluvia-mm'),
    uvUmbral: get('mediasombra.uv-umbral'),
  };
}

export interface RuleConditionResult {
  telemetria: Record<string, number>;
  clima: SimulatedWeather | null;
  needsCooldownReset: boolean;
  conflictos: string[];
  resumen: string[];
}

/** Midpoint rounded to one decimal. */
function mid(a: number, b: number): number {
  return Math.round((a + b) / 2 * 10) / 10;
}

/**
 * Given the selected rules and resolved thresholds, returns the telemetry payload and
 * climate conditions needed to make those rules fire. Detects mutually-exclusive combos
 * (e.g. R-02 low humidity vs. R-04 high humidity) and puts them in `conflictos`.
 */
export function calculateConditions(
  selectedRules: RuleKey[],
  thresholds: ThresholdMap,
): RuleConditionResult {
  const conflictos: string[] = [];
  const resumen: string[] = [];

  // Conflict detection: rules with incompatible humidity requirements.
  const wantsLow = selectedRules.includes('R02');
  const wantsHigh = selectedRules.includes('R04');
  if (wantsLow && wantsHigh) {
    conflictos.push(
      `R-02 requiere humSus < ${thresholds.umbralCritico}% y R-04 requiere humSus ≥ ${thresholds.saturacionBloqueo}%. Son excluyentes; ejecutalas por separado.`,
    );
  }

  // Base: a healthy nursery.
  const telemetria: Record<string, number> = {
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

  let clima: SimulatedWeather | null = null;
  let needsCooldownReset = false;

  // Determine the humidity value required.
  if (selectedRules.includes('R02') && !wantsHigh) {
    const h = Math.max(5, thresholds.umbralCritico - 7);
    telemetria.humSus = h;
    needsCooldownReset = true;
    resumen.push(`humSus = ${h}% (< umbral crítico ${thresholds.umbralCritico}%)`);
  } else if (selectedRules.includes('R04') && !wantsLow) {
    const h = Math.min(98, thresholds.saturacionBloqueo + 5);
    telemetria.humSus = h;
    resumen.push(`humSus = ${h}% (≥ saturación ${thresholds.saturacionBloqueo}%)`);
  } else if (selectedRules.includes('R01') || selectedRules.includes('R03')) {
    const h = mid(thresholds.umbralCritico, thresholds.umbralHumedad);
    telemetria.humSus = h;
    needsCooldownReset = true;
    resumen.push(`humSus = ${h}% (entre ${thresholds.umbralCritico}% y ${thresholds.umbralHumedad}%)`);
  }

  // Weather for R-03 (rain) and ShadingRule (UV).
  if (selectedRules.includes('R03')) {
    clima = {
      tempC: 18,
      humedadRelPct: 88,
      probLluviaPct: Math.min(100, thresholds.lluviaProbabilidad + 10),
      precipMm: thresholds.lluviaMm + 3,
      uvIndex: 2,
    };
    resumen.push(
      `clima: prob. lluvia = ${clima.probLluviaPct}%, precipMm = ${clima.precipMm} (umbral: ${thresholds.lluviaMm})`,
    );
  }

  if (selectedRules.includes('SHADING')) {
    const uv = thresholds.uvUmbral + 1;
    if (clima) {
      clima = { ...clima, uvIndex: uv };
    } else {
      clima = { tempC: 32, humedadRelPct: 45, probLluviaPct: 5, precipMm: 0, uvIndex: uv };
    }
    resumen.push(`clima: uvIndex = ${uv} (umbral: ${thresholds.uvUmbral})`);
  }

  if (selectedRules.includes('SUPPLY')) {
    resumen.push('SupplyRule: requiere diagnóstico IA crítico previo en el sector.');
  }

  if (needsCooldownReset) {
    resumen.push('Cooldowns → 0 en modo demo antes de disparar el pulso.');
  }

  return { telemetria, clima, needsCooldownReset, conflictos, resumen };
}
