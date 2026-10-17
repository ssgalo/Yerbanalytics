/* ============================================================
   Contrato de un escenario: cómo se acciona UNA regla desde el simulador.

   Un escenario es una función pura (sin fetch, sin React): dados los umbrales vigentes devuelve
   qué telemetría, clima y diagnóstico previo hacen que la regla dispare en el siguiente pulso.
   Cada regla nueva agrega su archivo en este directorio y lo registra en `index.ts`.
   ============================================================ */
import type { NewDiagnosis, SimulatedWeather } from '../types';

/** Umbrales del catálogo del backend ya resueltos (con los valores de la v2 si el backend no responde). */
export interface ThresholdMap {
  umbralHumedad: number;
  umbralCritico: number;
  saturacionBloqueo: number;
  saturacionAlerta: number;
  lluviaProbabilidad: number;
  lluviaMm: number;
  uvUmbral: number;
  /** Minimum diagnosis confidence (%) for the diagnosis-driven rules to act. */
  confianzaMinima: number;
  /** Physical upper limit of a humidity reading (%): above it the backend discards the reading (S-03). */
  humedadMaxima: number;
  /** Rereads the backend asks for after an out-of-range value, before discarding the metric (S-03). */
  relecturasInvalidas: number;
}

/** What the AI diagnosis must say for the rule to act; the sector and capture are resolved at dispatch. */
export type DiagnosisPlan = Pick<NewDiagnosis, 'estado' | 'sev' | 'confidence' | 'conf'>;

export interface ScenarioContext {
  thresholds: ThresholdMap;
}

/** A telemetry value a scenario needs, with the line the operator reads in the preview. */
export interface TelemetryClaim {
  value: number;
  note: string;
}

/**
 * What a scenario asks for. The pieces are merged by `calculateConditions`, scenarios with a higher
 * `applyOrder` winning over the ones applied before when they claim the same metric.
 */
export interface ScenarioPatch {
  /** Metrics to publish, by key (`humSus`, `temp`…). */
  telemetry?: Record<string, TelemetryClaim>;
  /** Weather fields to inject. Whatever no scenario sets falls back to a mild day. */
  weather?: Partial<SimulatedWeather>;
  /** Diagnosis to register BEFORE the pulse. */
  diagnosis?: DiagnosisPlan;
  /** Preview lines that are not about a metric. */
  notes?: string[];
  /** The target zone stops answering LEER_AHORA (and no telemetry is published for it). */
  silenceZone?: boolean;
  /** Sensing pulses to request, one after the other. Defaults to one. */
  pulses?: number;
  /** A manual lock to create BEFORE the pulse, over the target zone (or the sector the operator picks). */
  manualLock?: { reason: string };
  /**
   * Metrics pinned on the target zone's readings: every reading it publishes (the answer to the pulse and to each
   * reread) carries them, until the operator releases the zone. They are NOT published as a reading of their own: the
   * answer to the pulse is what the engine decides on, so that is where they have to be.
   */
  pinnedReadings?: Record<string, TelemetryClaim>;
  /**
   * The target zone must have at least one sector with no electrovalve registered, because that is what the scenario
   * shows. The simulator never changes the hardware registry: it looks for sectors that already lack theirs.
   */
  needsSectorWithoutValve?: boolean;
}

export interface ScenarioConflict {
  /** Key of the other scenario this one cannot run with. */
  with: string;
  reason(ctx: ScenarioContext): string;
}

export interface RuleScenario {
  key: string;
  label: string;
  description: string;
  /** Rule branch, as the backend names it. */
  branch: string;
  /** Names (`GET /api/demo/rules`) of the backend rules this scenario makes fire or exercises. */
  backendRules: string[];
  /** Needs the cooldowns in 0 to fire at the next pulse. */
  needsCooldownReset: boolean;
  /** Decides with the sector's AI diagnosis, so one must exist (and be new) beforehand. */
  needsDiagnosis: boolean;
  /** Higher wins when two selected scenarios claim the same metric. */
  applyOrder: number;
  /** Declared on one side only: the detection is symmetric. */
  conflicts?: ScenarioConflict[];
  apply(ctx: ScenarioContext): ScenarioPatch;
}
