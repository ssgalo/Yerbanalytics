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

/**
 * Demo telemetry topic of a macro-zone. Separate tree from the real one, so the ESP32's readings and the
 * simulator's never race: with the backend in demo mode, the operator chooses which of the two it reads.
 * Mirrors `DemoTelemetryTopics` in the backend (it is NOT part of contrato.h).
 */
export function demoTelemetryTopic(zonaId: string): string {
  return `nursery/demo/zone/${zonaId}/telemetry`;
}

/** The topic to publish a zone's telemetry on: the demo one if the backend is in demo mode, else the regular one. */
export function telemetryTopicFor(zonaId: string, demoMode: boolean): string {
  return demoMode ? demoTelemetryTopic(zonaId) : telemetryTopic(zonaId);
}

/* ============================================================
   Actuator commands and acks (the "Ack" section of contrato.h).

   The backend publishes `valve ON {durationSec}`, `pump ON {ml}` and `shade SET {targetPct}` to
   nursery/zone/{zona}/sector/{sector}/command. The node answers on .../ack WHEN THE ACTION ENDS,
   and the backend raises an alert if no ack arrives in time (SeguimientoComandosService). The
   simulator answers the same way, so a demo without hardware does not fill the history with
   "no response" alerts.
   ============================================================ */

/** How long the firmware runs the input pump on a `pump ON` without parameters (INSUMO_SEG_POR_DEFECTO). */
export const INPUT_DEFAULT_SEC = 10;
/** The firmware's longest input pump run per command (LIMITE_INSUMO_SEG_MAX). */
export const INPUT_MAX_SEC = 60;
/** The firmware's input pump calibration (BOMBA_INSUMO_MS_POR_ML), in seconds per ml. */
export const INPUT_SEC_PER_ML = 1;
/** Opening % from which the shade rolls up (MEDIASOMBRA_UMBRAL_APERTURA_PCT). */
export const SHADE_OPEN_THRESHOLD_PCT = 50;
/** How long the simulated shade takes to reach its end stop. */
export const SHADE_SIMULATED_SEC = 3;

/** Command payload, exactly as the backend publishes it. */
export interface ActuatorCommand {
  commandId?: string;
  actuador?: string;
  accion?: string;
  parametros?: Record<string, unknown>;
}

/** Ack topic of a sector. Mirrors contrato.h :: contratoTopicAck. */
export function ackTopic(zonaId: string, sectorId: string): string {
  return `nursery/zone/${zonaId}/sector/${sectorId}/ack`;
}

/** Zone and sector of a command topic, or `null` if it is not one. */
export function parseCommandTopic(topic: string): { zonaId: string; sectorId: string } | null {
  const parts = topic.split('/');
  if (parts.length !== 6 || parts[0] !== 'nursery' || parts[1] !== 'zone' || parts[3] !== 'sector' || parts[5] !== 'command') {
    return null;
  }
  return { zonaId: parts[2], sectorId: parts[4] };
}

const positive = (v: unknown): number | null => (typeof v === 'number' && v > 0 ? v : null);

/**
 * The ack the firmware would send for a command, and after how long. Same rules as the
 * firmware (`act_riego`, `act_mediasombra`), minus the physical failures.
 */
export function simulatedAck(command: ActuatorCommand): { delayMs: number; ack: ActuatorAck } {
  const commandId = command.commandId;
  const accion = (command.accion ?? '').toUpperCase();
  const params = command.parametros ?? {};
  const now = (status: ActuatorAck['status'], tipo: AckDetailType) => ({ delayMs: 0, ack: { commandId, status, detalle: { tipo } } });

  if (command.actuador !== 'valve' && command.actuador !== 'pump' && command.actuador !== 'shade') {
    return now('ERROR', 'comando_invalido');
  }
  if (accion === 'OFF' || accion === 'CLOSE') {
    return now('SUCCESS', 'ok');
  }

  if (command.actuador === 'valve') {
    if (accion !== 'ON' && accion !== 'OPEN') return now('ERROR', 'accion_invalida');
    const requested = positive(params.durationSec);
    if (requested === null) return now('ERROR', 'duracion_invalida');
    const durationSec = Math.min(requested, VALVE_MAX_DURATION_SEC);
    const tipo: AckDetailType = requested > VALVE_MAX_DURATION_SEC ? 'limite_excedido' : 'ok';
    return { delayMs: durationSec * 1000, ack: { commandId, status: 'SUCCESS', detalle: { tipo, durationSec } } };
  }

  if (command.actuador === 'pump') {
    if (accion !== 'ON' && accion !== 'INJECT') return now('ERROR', 'accion_invalida');
    const ml = positive(params.ml);
    const requested = ml !== null ? ml * INPUT_SEC_PER_ML : (positive(params.durationSec) ?? INPUT_DEFAULT_SEC);
    const durationSec = Math.ceil(Math.min(requested, INPUT_MAX_SEC));
    const tipo: AckDetailType = requested > INPUT_MAX_SEC ? 'limite_excedido' : 'ok';
    const detalle: ActuatorAck['detalle'] = { tipo, durationSec };
    if (ml !== null) detalle.ml = ml;
    return { delayMs: durationSec * 1000, ack: { commandId, status: 'SUCCESS', detalle } };
  }

  // shade
  if (accion !== 'SET' && accion !== 'MOVE') return now('ERROR', 'accion_invalida');
  const target = params.targetPct;
  if (typeof target !== 'number' || target < 0) return now('ERROR', 'parametros_invalidos');
  const targetPct = target >= SHADE_OPEN_THRESHOLD_PCT ? 100 : 0;
  return { delayMs: SHADE_SIMULATED_SEC * 1000, ack: { commandId, status: 'SUCCESS', detalle: { tipo: 'ok', targetPct } } };
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

/* ============================================================
   Zone "read now" command and actuator ACK (add-secuencias-demo-expo, design §1.2 and §1.3).

   SOURCE OF TRUTH: the "Ack" and "Comando de zona" sections of
   `Desarrollo/embebido/comun/contrato.h`; also mirrored by `ContratoNodo.java` (backend) and
   the sketch `vivero_esp32_red.ino`.

   The simulator answers the zone command for the zones it simulates (backend-driven sensing):
   it replies with one telemetry round, like the firmware does.

   - Zone command (backend → zone's witness node): QoS 1 both ways, no retain. The node answers
     with the usual telemetry on the zone telemetry topic, without a commandId and without ACK.
   - ACK (node → backend): one per actuator command, published when it finishes. The ESP32
     publishes QoS 0; the backend subscribes QoS 1.
   ============================================================ */

/** Topic the backend publishes the zone command to. */
export function zoneCommandTopic(zonaId: string): string {
  return `nursery/zone/${zonaId}/command`;
}

export const READ_NOW_ACTION = 'LEER_AHORA';

/** Zone of a zone-level command topic (`nursery/zone/{id}/command`), or `null` if it is not one. */
export function parseZoneCommandTopic(topic: string): { zonaId: string } | null {
  const parts = topic.split('/');
  if (parts.length !== 4) return null;
  const [root, zone, zonaId, leaf] = parts;
  if (root !== 'nursery' || zone !== 'zone' || leaf !== 'command' || !zonaId) return null;
  return { zonaId };
}

/** Index `n` of a macro-zone id `MZ-n` (n >= 1), or `null` for any other id. */
export function macroZoneIndex(zonaId: string): number | null {
  const m = /^MZ-(\d+)$/.exec(zonaId);
  const n = m ? Number(m[1]) : 0;
  return n >= 1 ? n : null;
}

/** `true` only for a parsed payload whose `accion` is exactly `LEER_AHORA`. */
export function isReadNow(payload: unknown): boolean {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) return false;
  return (payload as { accion?: unknown }).accion === READ_NOW_ACTION;
}

/** Zone command payload, exactly as the backend publishes it. No `actuador`: it is not an actuator. */
export interface ZoneCommand {
  /** UUID v4. */
  commandId: string;
  accion: typeof READ_NOW_ACTION;
  parametros: Record<string, never>;
}

export const ACK_STATUSES = ['SUCCESS', 'ERROR'] as const;
export type AckStatus = (typeof ACK_STATUSES)[number];

/** Union of the `detalle.tipo` values the firmware emits (see the "Ack" section of contrato.h). */
export const ACK_DETAIL_TYPES = [
  'ok',
  'sin_cambio',
  'limite_excedido',
  'comando_invalido',
  'accion_invalida',
  'sector_invalido',
  'parametros_invalidos',
  'duracion_invalida',
  'actuador_desconocido',
  'falla_mecanica',
  'reemplazado',
  'cancelado',
] as const;
export type AckDetailType = (typeof ACK_DETAIL_TYPES)[number];

/**
 * Actuator ACK payload on `nursery/zone/{zonaId}/sector/{sectorId}/ack`, exactly as the node publishes
 * it. `commandId` is optional because the simulator may answer an unparseable command without one.
 * `detalle` is an object (in the rail event it is a string); extra keys may come along.
 */
export interface ActuatorAck {
  commandId?: string;
  status: AckStatus;
  detalle: { tipo: AckDetailType; durationSec?: number; ml?: number; targetPct?: number } & Record<string, unknown>;
}

/* ============================================================
   Node errors (add-fallas-esp32, S-05 of reglas_v2).

   SOURCE OF TRUTH: the "Errores del nodo" section of `Desarrollo/embebido/comun/contrato.h`;
   also mirrored by `ContratoNodo.java` / `NodeErrorType.java` on the backend.

   The real node detects the failure, cuts the actuator on its own and publishes ONE error per
   event on the zone's `errores` topic (QoS 1, no retain). The simulator publishes the same
   payload so the backend reacts exactly as it would to a real node; it does not simulate the
   hardware behind it.
   ============================================================ */

/** Errors topic of a macro-zone. Mirrors contrato.h :: contratoTopicErrores. */
export function errorsTopic(zonaId: string): string {
  return `nursery/zone/${zonaId}/errores`;
}

/** The `tipo` values of the contract: two hydraulic (per sector) and one of the shade mesh (per macro-zone). */
export const NODE_ERROR_TYPES = ['SIN_CAUDAL', 'CAUDAL_EXCESIVO', 'MEDIASOMBRA_FIN_DE_CARRERA'] as const;
export type NodeErrorType = (typeof NODE_ERROR_TYPES)[number];

/** Which shade limit switch failed (the `valor` of a `MEDIASOMBRA_FIN_DE_CARRERA`). */
export const SHADE_LIMIT_SWITCHES = ['CERRADA', 'ABIERTA'] as const;
export type ShadeLimitSwitch = (typeof SHADE_LIMIT_SWITCHES)[number];

/** Error payload, exactly as a node publishes it. */
export interface NodeErrorPayload {
  /** Serial/MAC of the node that reports. */
  nodo: string;
  /** Sector id; empty on a shade error (the mesh is the macro-zone's). */
  sector: string;
  tipo: NodeErrorType;
  /** Measured flow in L/h for the flow errors; the failed limit switch for the shade one. */
  valor: number | ShadeLimitSwitch;
  timestamp: number;
}

/** What the caller decides; the contract decides the rest (the shade error never names a sector). */
export interface NodeErrorInput {
  nodo: string;
  sector: string;
  tipo: NodeErrorType;
  valor: number | ShadeLimitSwitch;
}

/** The payload for an error, with the shade error's sector emptied as the contract requires. */
export function buildNodeError(input: NodeErrorInput, nowMs: number): NodeErrorPayload {
  return {
    nodo: input.nodo,
    sector: input.tipo === 'MEDIASOMBRA_FIN_DE_CARRERA' ? '' : input.sector,
    tipo: input.tipo,
    valor: input.valor,
    timestamp: nowMs,
  };
}
