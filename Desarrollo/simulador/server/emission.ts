/* ============================================================
   Automatic telemetry emission: random readings for every macro-zone, to give the nursery some
   background activity without entering values by hand.

   Sensing is backend-driven (pure pull), like the real firmware: there are no timers here.
   Turning emission on sends one boot round; while it is on, each LEER_AHORA the backend
   publishes for a macro-zone is answered with one reading for that zone.

   It ALWAYS starts off. Once on it overwrites manually entered values, so the user has to
   ask for it explicitly. The ranges are the ones the simulator that used to live inside the
   backend used, including the low-battery macro-zone, which exists to exercise the
   "Batería Baja" alert (HU-21 CA-02).
   ============================================================ */
import { macroZoneIndex, toContractUnits, type ContractMetrics, type MetricKey, type TelemetryPayload } from './contract.ts';
import { publish } from './mqtt.ts';
import { fetchFromBackend } from './proxy.ts';

/** Macro-zones to emit to when the backend's topology cannot be read. */
const DEFAULT_MACRO_ZONES = 6;

/** Macro-zone reporting low battery, so the alert always has a case. */
const LOW_BATTERY_ZONE = 2;

let active = false;

/**
 * Zones that do not answer LEER_AHORA: a node that went silent. In memory, like `active`: it always starts
 * empty, so launching the simulator never leaves a zone mute from a previous session.
 */
const silenced = new Set<string>();

/** Metrics in UI units (`ce` in dS/m) that a zone's node answers with, whatever the random reading says. */
export type PinnedMetrics = Partial<Record<MetricKey, number>>;

/**
 * Pinned readings: macro-zone → the metrics every reading of that zone carries over the random ones, however many times
 * the backend asks again. A scenario needs them because the answer to a pulse is what the engine decides on, and a
 * reading published beforehand is overwritten by it ("Lectura inválida" pins an impossible value, "Hardware incompleto"
 * dry soil). In memory, like `silenced`: it always starts empty. Values are kept in UI units (what the operator typed)
 * and converted to contract units when published.
 */
const pinned = new Map<string, PinnedMetrics>();

/** Collaborators, replaceable in tests (see setDependencies). Defaults are the real ones. */
interface Dependencies {
  publish: (zoneId: string, payload: TelemetryPayload) => Promise<void>;
  fetchTopology: () => Promise<{ macroZonas?: number } | null | undefined>;
}

const defaultDependencies: Dependencies = {
  publish: (zoneId, payload) => publish(zoneId, payload),
  fetchTopology: () => fetchFromBackend<{ macroZonas?: number }>('/api/topologia'),
};

let deps: Dependencies = defaultDependencies;

/** Test seam: replaces the broker publisher and the topology fetcher. Call with no argument to restore. */
export function setDependencies(overrides?: Partial<Dependencies>): void {
  deps = { ...defaultDependencies, ...overrides };
}

const around = (base: number, span: number) => base + Math.random() * span;

/** Macro-zones of the current topology. Falls back to the seed's if the backend is down. */
async function currentMacroZones(): Promise<number> {
  const topology = await deps.fetchTopology();
  const count = topology?.macroZonas ?? 0;
  return count > 0 ? count : DEFAULT_MACRO_ZONES;
}

/** Values in CONTRACT units, as a real node would publish them (ce in µS/cm, uv in %). */
function randomMetrics(): ContractMetrics {
  return {
    humSus: around(38, 25), // ideal 42-68 %
    humAmb: around(58, 22), // ideal 62-84 %
    temp: around(17, 11), // ideal 18-27 °C
    tempSuelo: around(15, 10), // ideal 16-24 °C
    uv: around(30, 50), // ideal 35-70 %
    ce: around(900, 900), // ideal 1.0-1.9 dS/m
    phSuelo: around(4.8, 1.4), // ideal 5.0-6.0
    n: around(90, 120), // ideal 100-200 mg/kg
    p: around(25, 40), // ideal 30-60 mg/kg
    k: around(110, 140), // ideal 120-240 mg/kg
  };
}

/** Publishes one reading for macro-zone `i` (1-based). Throws if the broker is unavailable. */
async function emitZone(i: number): Promise<void> {
  const payload: TelemetryPayload = {
    // A distinct MAC per macro-zone, so each witness node refreshes its own row.
    mac: `A4:CF:12:9A:00:${String(i).padStart(2, '0')}`,
    battery: i === LOW_BATTERY_ZONE ? Math.floor(around(12, 8)) : Math.floor(around(70, 30)),
    signal: Math.floor(-55 - Math.random() * 35), // dBm: -55 to -89
    timestamp: Date.now(),
    metrics: { ...randomMetrics(), ...toContractUnits(pinned.get(`MZ-${i}`) ?? {}) },
  };
  await deps.publish(`MZ-${i}`, payload);
}

/** Boot round: one reading for every macro-zone, as a node does when it powers up. */
async function emitRound(): Promise<void> {
  const zones = await currentMacroZones();
  for (let i = 1; i <= zones; i++) {
    if (silenced.has(`MZ-${i}`)) continue;
    try {
      await emitZone(i);
    } catch (e) {
      // A failed round does not turn emission off: the broker may return on the next one.
      console.error(`[simulator] auto emission: ${e instanceof Error ? e.message : String(e)}`);
      return; // With the broker down there is no point insisting on the remaining zones.
    }
  }
}

/**
 * Answers a LEER_AHORA for `zonaId` with one reading. Ignored while emission is off, and for
 * zones that are not simulated macro-zones (those belong to a real node).
 */
export async function answerReadNow(zonaId: string): Promise<void> {
  if (!active) return;
  if (silenced.has(zonaId)) {
    console.log(`[simulator] LEER_AHORA zone=${zonaId} ignored (zone silenced)`);
    return;
  }
  const index = macroZoneIndex(zonaId);
  if (index === null || index > (await currentMacroZones())) return;
  try {
    await emitZone(index);
    console.log(`[simulator] → TELEMETRY zone=${zonaId} (answer to LEER_AHORA)`);
  } catch (e) {
    console.error(`[simulator] LEER_AHORA ${zonaId}: ${e instanceof Error ? e.message : String(e)}`);
  }
}

/** Makes a zone stop (or resume) answering LEER_AHORA. Idempotent. */
export function setSilenced(zonaId: string, on: boolean): void {
  if (on) silenced.add(zonaId);
  else silenced.delete(zonaId);
}

/**
 * Pins `metrics` (UI units) on the readings of a zone until they are released: every reading it publishes carries them.
 * Replaces what was pinned for the zone; `null` or an empty set releases it. Idempotent.
 */
export function setPinnedMetrics(zonaId: string, metrics: PinnedMetrics | null): void {
  if (metrics === null || Object.keys(metrics).length === 0) pinned.delete(zonaId);
  else pinned.set(zonaId, { ...metrics });
}

/** Pinned readings by zone, in UI units. */
export function pinnedMetrics(): Record<string, PinnedMetrics> {
  return Object.fromEntries([...pinned].map(([zone, metrics]) => [zone, { ...metrics }]));
}

export function isSilenced(zonaId: string): boolean {
  return silenced.has(zonaId);
}

export function silencedZones(): string[] {
  return [...silenced];
}

export function isActive(): boolean {
  return active;
}

/** Turns automatic emission on or off. Idempotent. */
export function setActive(on: boolean): void {
  if (on && !active) {
    active = true;
    void emitRound(); // Boot round without waiting: the effect is visible right away.
    console.log('[simulator] auto emission on (answers LEER_AHORA from the backend)');
  } else if (!on && active) {
    active = false;
    console.log('[simulator] auto emission off');
  }
}

/** Stops emission on shutdown. */
export function stop(): void {
  setActive(false);
}
