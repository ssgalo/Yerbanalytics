import type { ScenarioContext, TelemetryClaim } from './types';

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
