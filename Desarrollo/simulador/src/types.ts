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

/** Health of the simulator and of its two external dependencies. */
export interface SimulatorStatus {
  backend: { url: string; up: boolean };
  broker: { url: string; connected: boolean };
  emission: { active: boolean; intervalMs: number };
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
export interface SectorRef {
  id: string;
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
  conf: number;
  sev: string;
}

/** A persisted diagnosis. */
export interface RegisteredDiagnosis {
  id: string;
  imagenUrl: string;
}
