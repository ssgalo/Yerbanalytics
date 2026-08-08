/* ============================================================
   Automatic telemetry emission: periodic noise to every macro-zone, to give the nursery some
   background activity without entering values by hand.

   It ALWAYS starts off. Once on it overwrites manually entered values, so the user has to
   ask for it explicitly. The ranges are the ones the simulator that used to live inside the
   backend used, including the low-battery macro-zone, which exists to exercise the
   "Batería Baja" alert (HU-21 CA-02).
   ============================================================ */
import { config } from './config.ts';
import type { ContractMetrics, TelemetryPayload } from './contract.ts';
import { publish } from './mqtt.ts';
import { fetchFromBackend } from './proxy.ts';

/** Macro-zones to emit to when the backend's topology cannot be read. */
const DEFAULT_MACRO_ZONES = 6;

/** Macro-zone reporting low battery, so the alert always has a case. */
const LOW_BATTERY_ZONE = 2;

let timer: NodeJS.Timeout | null = null;

const around = (base: number, span: number) => base + Math.random() * span;

/** Macro-zones of the current topology. Falls back to the seed's if the backend is down. */
async function currentMacroZones(): Promise<number> {
  const topology = await fetchFromBackend<{ macroZonas?: number }>('/api/topologia');
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

async function emitRound(): Promise<void> {
  const zones = await currentMacroZones();
  for (let i = 1; i <= zones; i++) {
    const zonaId = `MZ-${i}`;
    const payload: TelemetryPayload = {
      // A distinct MAC per macro-zone, so each witness node refreshes its own row.
      mac: `A4:CF:12:9A:00:${String(i).padStart(2, '0')}`,
      battery: i === LOW_BATTERY_ZONE ? Math.floor(around(12, 8)) : Math.floor(around(70, 30)),
      signal: Math.floor(-55 - Math.random() * 35), // dBm: -55 to -89
      timestamp: Date.now(),
      metrics: randomMetrics(),
    };
    try {
      await publish(zonaId, payload);
    } catch (e) {
      // A failed round does not turn emission off: the broker may return on the next one.
      console.error(`[simulator] auto emission: ${e instanceof Error ? e.message : String(e)}`);
      return; // With the broker down there is no point insisting on the remaining zones.
    }
  }
}

export function isActive(): boolean {
  return timer !== null;
}

export function intervalMs(): number {
  return config.autoEmissionIntervalMs;
}

/** Turns automatic emission on or off. Idempotent. */
export function setActive(on: boolean): void {
  if (on && !timer) {
    void emitRound(); // First round without waiting: the effect is visible right away.
    timer = setInterval(() => void emitRound(), config.autoEmissionIntervalMs);
    console.log(`[simulator] auto emission on (every ${config.autoEmissionIntervalMs} ms)`);
  } else if (!on && timer) {
    clearInterval(timer);
    timer = null;
    console.log('[simulator] auto emission off');
  }
}

/** Stops emission on shutdown. */
export function stop(): void {
  setActive(false);
}
