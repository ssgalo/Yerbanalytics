/* ============================================================
   Persisted simulator state: the simulated sensors.

   Lives in `data/simulator.json`, inside this same folder. No database on purpose: it is a
   short list only the simulator reads and writes, and keeping it here means deleting the
   folder also deletes its data — which is exactly the point.

   What is NOT persisted: the automatic emission on/off switch. It always starts off, so
   that launching the simulator never begins overwriting values on its own.

   User-facing note: validation messages are shown verbatim in the UI, so they are written
   in Spanish like the rest of the operator-facing text.
   ============================================================ */
import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { config } from './config.ts';

/** A simulated telemetry emitter: serial/MAC plus the macro-zone it reports for. */
export interface SimulatedSensor {
  /** `serial` and `zonaId` keep the platform's wire vocabulary. */
  serial: string;
  zonaId: string;
}

interface State {
  sensors: SimulatedSensor[];
}

/** Tolerant read: a missing or corrupt file starts from scratch instead of breaking startup. */
function read(): State {
  let raw: string;
  try {
    raw = readFileSync(config.dataFile, 'utf8');
  } catch {
    return { sensors: [] };
  }
  try {
    const parsed = JSON.parse(raw) as Partial<State>;
    const sensors = Array.isArray(parsed.sensors) ? parsed.sensors : [];
    return {
      sensors: sensors.filter(
        (s): s is SimulatedSensor =>
          !!s && typeof s.serial === 'string' && typeof s.zonaId === 'string',
      ),
    };
  } catch {
    console.warn(`[simulator] ${config.dataFile} is corrupt; starting with an empty list.`);
    return { sensors: [] };
  }
}

/** Atomic write: write to a temp file and rename, so a crash never leaves half a JSON. */
function write(state: State): void {
  mkdirSync(dirname(config.dataFile), { recursive: true });
  const temp = `${config.dataFile}.tmp`;
  writeFileSync(temp, JSON.stringify(state, null, 2), 'utf8');
  renameSync(temp, config.dataFile);
}

let state = read();

/** Trims an input string, returning `null` when it ends up empty. */
function norm(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  const t = value.trim();
  return t === '' ? null : t;
}

/** Simulator validation error. The server maps it to a 400. */
export class InvalidInput extends Error {}

/** Simulated sensors, in creation order. */
export function listSensors(): SimulatedSensor[] {
  return [...state.sensors];
}

/**
 * Registers a simulated sensor. It does not touch the system's hardware registry: they are
 * separate lists on purpose, and for a node to also refresh its technical status it has to
 * be registered there with the same serial/MAC.
 */
export function createSensor(rawSerial: unknown, rawZone: unknown): SimulatedSensor {
  const serial = norm(rawSerial);
  const zonaId = norm(rawZone);
  if (!serial) throw new InvalidInput('El serial/MAC del sensor es obligatorio.');
  if (!zonaId) throw new InvalidInput('La macro-zona del sensor es obligatoria.');

  const key = serial.toLowerCase();
  if (state.sensors.some((s) => s.serial.toLowerCase() === key)) {
    throw new InvalidInput(`Ya existe un sensor simulado con el serial/MAC «${serial}».`);
  }

  const sensor: SimulatedSensor = { serial, zonaId };
  state = { ...state, sensors: [...state.sensors, sensor] };
  write(state);
  return sensor;
}

/** Removes a simulated sensor by serial/MAC. Returns `true` if it existed. */
export function deleteSensor(rawSerial: unknown): boolean {
  const serial = norm(rawSerial);
  if (!serial) return false;
  const key = serial.toLowerCase();
  const remaining = state.sensors.filter((s) => s.serial.toLowerCase() !== key);
  if (remaining.length === state.sensors.length) return false;
  state = { ...state, sensors: remaining };
  write(state);
  return true;
}
