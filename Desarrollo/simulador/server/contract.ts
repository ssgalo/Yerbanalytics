/* ============================================================
   Keys and units of the ESP32 node's MQTT contract.

   SOURCE OF TRUTH: `Desarrollo/embebido/comun/contrato.h`. This file mirrors it on the
   simulator side, the same way `ContratoNodo.java` mirrors it on the backend side. If the
   firmware changes a key or a unit, all three must be updated.

   The duplication is deliberate: sharing the contract with the backend would make the
   simulator depend on it, and this folder would stop being deletable.

   Units exactly as the node publishes them:
     - humSus %RH · humAmb %RH · temp °C air · tempSuelo °C
     - ce  µS/cm   — the platform stores dS/m, so it is converted ON PUBLISH.
     - uv  LDR light percentage, not a UV index. The key is kept for compatibility with the
           firmware already written; the metric is luminosity.
     - phSuelo pH · n/p/k mg/kg

   The metric keys keep the firmware's vocabulary verbatim (`humSus`, `tempSuelo`, `ce`,
   `uv`): they are a compatibility boundary, not names we get to choose.
   ============================================================ */

/** 1 dS/m = 1000 µS/cm. */
export const CE_USCM_PER_DSM = 1000;

/** Conductivity in dS/m (platform and UI unit) to µS/cm (contract unit). */
export function ceToMicroSPerCm(ceDsPerM: number | null | undefined): number | undefined {
  return ceDsPerM === null || ceDsPerM === undefined ? undefined : ceDsPerM * CE_USCM_PER_DSM;
}

/** Telemetry topic of a macro-zone. The one the backend listens on and the firmware publishes to. */
export function telemetryTopic(zonaId: string): string {
  return `nursery/zone/${zonaId}/telemetry`;
}

/** The ten modelled metrics, in contract units. */
export interface ContractMetrics {
  humSus?: number;
  humAmb?: number;
  temp?: number;
  tempSuelo?: number;
  /** LDR light percentage, not a UV index. */
  uv?: number;
  /** µS/cm. */
  ce?: number;
  phSuelo?: number;
  n?: number;
  p?: number;
  k?: number;
}

/** Telemetry payload, exactly as a node publishes it. */
export interface TelemetryPayload {
  mac: string;
  battery?: number | null;
  signal?: number | null;
  timestamp: number;
  metrics: ContractMetrics;
}

/** Metric keys, in the order the UI shows them. */
export const METRIC_KEYS = [
  'humSus',
  'humAmb',
  'temp',
  'uv',
  'tempSuelo',
  'ce',
  'phSuelo',
  'n',
  'p',
  'k',
] as const;

export type MetricKey = (typeof METRIC_KEYS)[number];

/**
 * Translates a reading in UI units to contract units, dropping absent metrics. Only `ce`
 * needs conversion; the rest travels as-is.
 */
export function toContractUnits(metrics: Partial<Record<MetricKey, number>>): ContractMetrics {
  const out: ContractMetrics = {};
  for (const key of METRIC_KEYS) {
    const value = metrics[key];
    if (value === undefined || value === null || Number.isNaN(value)) continue;
    if (key === 'ce') {
      out.ce = ceToMicroSPerCm(value);
    } else {
      out[key] = value;
    }
  }
  return out;
}
