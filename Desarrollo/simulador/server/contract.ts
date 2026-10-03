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

/**
 * Longest valve opening, in seconds, the backend may ask for in a `valve ON` command's
 * `durationSec`. Mirrors CONTRATO_VALVULA_DURACION_MAX_SEG in contrato.h (and
 * ContratoNodo.DURACION_VALVULA_MAX_SEG on the backend). The real firmware's local limit is
 * the same value; a command above it means the three copies of the contract drifted apart.
 */
export const VALVE_MAX_DURATION_SEC = 1200;

/**
 * Warning for a command that asks the valve to stay open longer than the contract allows, or
 * `null` when it is fine (or is not a valve command). The simulator only logs it: it does not
 * reject anything, so the problem stays visible instead of being silently trimmed.
 */
export function valveDurationWarning(command: {
  actuador?: string;
  parametros?: Record<string, unknown>;
}): string | null {
  if (command.actuador !== 'valve') return null;
  const duration = command.parametros?.durationSec;
  if (typeof duration !== 'number' || duration <= VALVE_MAX_DURATION_SEC) return null;
  return (
    `valve durationSec=${duration} exceeds the contract maximum of ${VALVE_MAX_DURATION_SEC} s;` +
    ' the real firmware would trim it'
  );
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
  /**
   * Epoch in milliseconds here. The firmware publishes seconds; the backend normalizes
   * either one by value (ContratoNodo.timestampAMs), so this is documentation only.
   */
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

/* ============================================================
   Camera rail contract (add-pasada-riel, design.md §1).

   SOURCE OF TRUTH: the "Riel" section of `Desarrollo/embebido/comun/contrato.h`; also mirrored
   by `ContratoRiel.java` (backend) and the `RIEL_*` defines of `vivero_esp32_red.ino`.

   Constants and types only: the simulator does NOT simulate the rail. They live here so a
   future change (or a test) speaks the same vocabulary as the firmware.

   - Command (backend → ESP32): backend publishes QoS 1, no retain; the ESP32 subscribes QoS 1.
   - Event (ESP32 → backend): the ESP32 publishes QoS 0 (PubSubClient cannot publish QoS 1).
   - Idempotency on the firmware: same commandId as the running one → ignored; same as the last
     finished one → its final event is republished; seen among the last 4 → ignored; a different
     commandId while moving → the old one ends in ERROR REEMPLAZADO and the new one runs.
   ============================================================ */

/** Topic the backend publishes rail commands to. */
export const RAIL_COMMAND_TOPIC = 'nursery/rail/command';
/** Topic the rail publishes its events to. */
export const RAIL_EVENT_TOPIC = 'nursery/rail/event';

export const RAIL_ACTUATOR = 'rail';
export const RAIL_ACTIONS = ['IR_A', 'HOME'] as const;
export type RailAction = (typeof RAIL_ACTIONS)[number];

export const RAIL_STATUSES = ['ACEPTADO', 'LLEGO', 'ERROR'] as const;
export type RailStatus = (typeof RAIL_STATUSES)[number];

export const RAIL_ERROR_CODES = [
  'COMANDO_INVALIDO',
  'HOME_NO_ENCONTRADO',
  'FIN_DE_CARRERA',
  'REEMPLAZADO',
] as const;
export type RailErrorCode = (typeof RAIL_ERROR_CODES)[number];

/** Logical rail position: 1 and 2 are the two capture stops; the firmware maps them to steps. */
export type RailPosition = 1 | 2;

/** Command payload, exactly as the backend publishes it. */
export interface RailCommand {
  /** UUID v4, one per step. */
  commandId: string;
  actuador: typeof RAIL_ACTUATOR;
  accion: RailAction;
  /** `posicion` only for `IR_A`; `{}` for `HOME`. */
  parametros: { posicion?: RailPosition };
}

/** Event payload, exactly as the rail publishes it. */
export interface RailEvent {
  commandId: string;
  status: RailStatus;
  /** 0 home, 1, 2; null between positions or without a reference. */
  posicion: 0 | RailPosition | null;
  /** The firmware's step counter (diagnostics). */
  pasos: number;
  /** Only on ERROR. */
  codigo?: RailErrorCode;
  /** Only on ERROR; free ASCII text for the log. */
  detalle?: string;
}
