/* ============================================================
   Simulator types.

   The ones describing backend responses declare ONLY the fields the simulator uses, on
   purpose: they are not a mirror of the system's domain but the minimal view this tool
   needs. Less declared surface, fewer things that break when the backend grows.

   Field names keep the platform's wire vocabulary (`zonaId`, `sectorId`, `imagenUrl`…):
   they are the JSON contract, not names we get to choose.
   ============================================================ */

/** A simulated telemetry emitter. */
export interface SimulatedSensor {
  serial: string;
  zonaId: string;
}

/**
 * Whether the backend accepts the simulator's service account. Anything but `ok` with the
 * backend reachable means it answers but rejects the requests.
 */
export type BackendAccess =
  | 'ok'
  | 'unreachable'
  | 'no-credentials'
  | 'rejected'
  | 'temporary-password'
  | 'forbidden'
  | 'error';

/** Health of the simulator and of its two external dependencies. */
export interface SimulatorStatus {
  backend: { url: string; up: boolean; access: BackendAccess; user: string | null };
  broker: { url: string; connected: boolean };
  emission: { active: boolean };
}

/** A reading to publish. Metrics are in UI units; the server converts `ce`. */
export interface TelemetryInput {
  serial: string;
  zonaId: string;
  battery?: number | null;
  signal?: number | null;
  /** Epoch ms. When omitted, it is stamped with the current time. */
  timestamp?: number | null;
  metrics: Record<string, number>;
}

/** Summary of the nursery topology. */
export interface NurseryTopology {
  macroZonas: number;
  sectoresPorMacroZona: number;
  totalSectores: number;
  generada: boolean;
}

/** A sector of the current topology, to choose where to capture. */
/** The `tipo` of an error a node publishes (contrato.h, "Errores del nodo"; mirrored by `server/contract.ts`). */
export type NodeErrorType = 'SIN_CAUDAL' | 'CAUDAL_EXCESIVO' | 'MEDIASOMBRA_FIN_DE_CARRERA';

/** Which shade limit switch failed: the `valor` of a `MEDIASOMBRA_FIN_DE_CARRERA`. */
export type ShadeLimitSwitch = 'CERRADA' | 'ABIERTA';

/** An error a scenario asks the simulator to publish for the target zone (the sector is picked by the operator). */
export interface NodeErrorPlan {
  tipo: NodeErrorType;
  /** Measured flow in L/h for the flow errors; the failed limit switch for the shade one. */
  valor: number | ShadeLimitSwitch;
}

export interface SectorRef {
  id: string;
  /** Macro-zone id (`MZ-1`…): the key sensors and telemetry use. */
  zonaId: string;
  zonaName: string;
}

export type CaptureOrderState =
  | 'PENDIENTE'
  | 'ENTREGADA'
  | 'RECIBIDA'
  | 'FALLIDA'
  | 'VENCIDA'
  | 'ERROR';

/** A capture order and its progress. */
export interface CaptureOrder {
  ordenId: string;
  sectorId: string;
  zonaId: string;
  posicionRiel: number;
  estado: CaptureOrderState;
  intentos: number;
  motivoFallo: string | null;
  capturaId: string | null;
  imagenUrl: string | null;
}

/** Technical status of a capture device. */
export interface CameraDevice {
  id: string;
  nombre: string;
  estadoLabel: string;
  estadoSoft: string;
  estadoInk: string;
  ultimoHeartbeatAgo: string;
  capturasOk: number;
  capturasError: number;
}

/** Single-use pairing code. */
export interface PairingCode {
  codigo: string;
  expiraEn: number;
}

/** Registering a diagnosis: the same operation the inference service will perform. */
export interface NewDiagnosis {
  capturaId: string;
  sectorId?: string;
  zonaId?: string;
  estado: string;
  /** Mean score of the diagnosed state, 0-100. Absent in "No concluyente". */
  conf?: number;
  /** Severity level. Only anomalous states carry it. */
  sev?: string;
  /** Confidence level. Absent in "No concluyente". */
  confidence?: string;
}

/** A persisted diagnosis. */
export interface RegisteredDiagnosis {
  id: string;
  imagenUrl: string;
}

/* ----------------------------------------------------------------
   Demo mode — rule overrides and simulated weather
   ---------------------------------------------------------------- */

/** Current state of one rule as returned by GET /api/demo/rules. */
export interface DemoRule {
  name: string;
  label: string;
  branch: string;
  priority: number;
  enabled: boolean;
  /** Cooldown keys this rule declares (one numeric input per key). */
  cooldownKeys: string[];
  /** Bypass keys this rule declares (one toggle per key). */
  bypassKeys: string[];
  /** Current cooldown overrides, by key. Empty = use production values. */
  cooldownMinutes: Record<string, number>;
  /** Bypass keys currently active. */
  bypassed: string[];
}

/** Body for PUT /api/demo/rules/{ruleName}. All fields optional. */
export interface DemoRuleUpdate {
  enabled?: boolean;
  cooldownMinutes?: Record<string, number>;
  bypassed?: string[];
}

/** One hourly mark to override on the simulated forecast. Absent fields keep the flat value. */
export interface SimulatedWeatherHour {
  /** Hour of the day (0-23, nursery local time); the mark that ENDS that hour, so 13 = 12:00-13:00. */
  hora: number;
  probLluviaPct?: number;
  precipMm?: number;
  tempC?: number;
}

/**
 * Simulated weather forecast. The five base fields describe the current hour and are repeated, flat,
 * in the next 24 hourly marks; `uvMaxDia` and `horas` refine that.
 */
export interface SimulatedWeather {
  tempC: number;
  humedadRelPct: number;
  probLluviaPct: number;
  /** Millimetres per hour. */
  precipMm: number;
  uvIndex: number;
  /** Maximum UV of the day; the backend uses `uvIndex` if absent. */
  uvMaxDia?: number;
  horas?: SimulatedWeatherHour[];
}

/* ----------------------------------------------------------------
   Pasada del riel (Demo Expo)
   ---------------------------------------------------------------- */

export type EstadoPasada = 'EN_CURSO' | 'COMPLETADA' | 'FALLIDA' | 'CANCELADA';
export type TipoPaso = 'MOVER' | 'CAPTURAR' | 'HOME';
export type EstadoPaso = 'PENDIENTE' | 'EN_CURSO' | 'OK' | 'ERROR' | 'OMITIDO';

export interface DiagnosticoPaso {
  estado: string;
  conf: number;
  sev: string;
  creadoEn: number;
}

export interface PasoPasada {
  n: number;
  tipo: TipoPaso;
  posicion: number;
  sectorId: string | null;
  estado: EstadoPaso;
  codigoError: string | null;
  detalle: string | null;
  commandId: string | null;
  ordenId: string | null;
  estadoOrden: string | null;
  capturaId: string | null;
  imagenUrl: string | null;
  diagnostico: DiagnosticoPaso | null;
  iniciadoEn: number | null;
  terminadoEn: number | null;
}

export interface Pasada {
  id: string;
  estado: EstadoPasada;
  iniciadaEn: number;
  finalizadaEn: number | null;
  cancelacionSolicitada: boolean;
  error: string | null;
  pasos: PasoPasada[];
}

/* ----------------------------------------------------------------
   Secuencias de actuadores (Demo Expo)
   ---------------------------------------------------------------- */

export type TipoSecuencia =
  | 'RIEGO'
  | 'MEDIASOMBRA'
  | 'LECTURA'
  | 'MEDIASOMBRA_ABRIR'
  | 'MEDIASOMBRA_CERRAR';
export type TipoPasoSecuencia =
  | 'ABRIR'
  | 'ESPERAR'
  | 'CERRAR'
  | 'DESPLEGAR'
  | 'ENROLLAR'
  | 'PEDIR'
  | 'ESPERAR_TELEMETRIA'
  | 'MOSTRAR';

export interface ParametrosSecuencia {
  duracionSeg?: number | null;
  esperaSeg?: number | null;
  ejecutarMotor?: boolean | null;
}

export interface PasoSecuencia {
  n: number;
  tipo: TipoPasoSecuencia;
  estado: EstadoPaso;
  codigoError: string | null;
  detalle: string | null;
  commandId: string | null;
  esperaHasta: number | null;
  iniciadoEn: number | null;
  terminadoEn: number | null;
}

export interface LecturaSecuencia {
  recibidaEn: number;
  metricas: Record<string, number | null>;
}

export interface Secuencia {
  id: string;
  tipo: TipoSecuencia;
  estado: EstadoPasada;
  zonaId: string;
  sectorId: string | null;
  parametros: ParametrosSecuencia;
  iniciadaEn: number;
  finalizadaEn: number | null;
  cancelacionSolicitada: boolean;
  error: string | null;
  lectura: LecturaSecuencia | null;
  pasos: PasoSecuencia[];
}

/* ----------------------------------------------------------------
   Rule parameters catalog
   ---------------------------------------------------------------- */

export interface ParametroRegla {
  clave: string;
  etiqueta: string;
  valor: string;
  fabrica: string;
  unidad: string;
}

/* ----------------------------------------------------------------
   Hardware registration
   ---------------------------------------------------------------- */

export interface NuevoHardware {
  serial: string;
  tipo: string;
  zonaId: string | null;
  sectorId: string | null;
}

/** A manual lock of the platform (`GET /api/bloqueos`). `sectorId` is null when it covers the whole zone. */
export interface ManualLock {
  id: number;
  zonaId: string;
  sectorId: string | null;
  activadoPor: string;
  activadoTs: number;
  motivo: string;
}

/** Input supply, as the platform names it. */
export type Supply = 'FERTILIZER' | 'FUNGICIDE';

/** An input tank of the platform (`GET /api/tanques`): one per supply and macro-zone. */
export interface TankLevel {
  zoneId: string;
  supply: Supply;
  supplyLabel: string;
  capacityMl: number;
  levelMl: number;
  levelPct: number;
  low: boolean;
  updatedTs: number;
  updatedBy: string | null;
}

/* ----------------------------------------------------------------
   Rule engine trace (GET /api/rules/evaluaciones/{sectorId}) — only what the happy path reads
   ---------------------------------------------------------------- */

export interface TracedAction {
  tipo: string;
  motivo: string;
}

export interface TracedRule {
  ruleId: string;
  /** Rule branch as the backend names it (`RIEGO`, `INSUMO`, `MEDIASOMBRA`…). */
  rama: string;
  estado: string;
  acciones: TracedAction[];
  bloqueadaPor: string | null;
  error: string | null;
}

export interface EvaluationTrace {
  sectorId: string;
  zonaId: string;
  /** Instant of the evaluation: ISO string, or epoch number depending on the backend's serializer. */
  ts: string | number;
  reglas: TracedRule[];
}
