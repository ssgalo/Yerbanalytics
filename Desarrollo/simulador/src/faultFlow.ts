/* ============================================================
   Waiting for the backend to mark a fault (S-05). Pure: the registry read and the wait are injected.

   The node error travels by MQTT and the backend marks the device when it processes it, a moment after the broker
   accepted the publish. The pulse that must find the fault already marked is requested by HTTP, so a scenario waits
   until the registry shows the mark before asking for it. If the mark never shows up (the receiver of errors is switched
   off in the rules panel, or the device is not registered) the run goes on and says so: it is exactly the case the
   operator may want to look at.
   ============================================================ */
import { isMarkedFaulty, VALVE_TYPE, type HardwareDevice } from './hardwareCoverage';
import type { SectorRef } from './types';

/** Type of the shade net, as the backend registers it (`HardwareService.MEDIASOMBRA`). */
const SHADE_TYPE = 'mediasombra';

/** `true` if the electrovalve of the sector is marked as faulty. */
export function valveMarked(devices: HardwareDevice[], sectorId: string): boolean {
  return devices.some((d) => d.tipo === VALVE_TYPE && d.sectorId === sectorId && isMarkedFaulty(d));
}

/**
 * `true` if the shade net of the zone is marked as faulty. The mesh belongs to the macro-zone and is registered with
 * it, like the witness node; a device still hanging from one of its sectors (a base not yet migrated) also counts.
 */
export function shadeMarked(devices: HardwareDevice[], zonaId: string, sectors: SectorRef[]): boolean {
  const ofZone = new Set(sectors.filter((s) => s.zonaId === zonaId).map((s) => s.id));
  return devices.some(
    (d) =>
      d.tipo === SHADE_TYPE &&
      ((d.sectorId === null && d.zonaId === zonaId) || (d.sectorId !== null && ofZone.has(d.sectorId))) &&
      isMarkedFaulty(d),
  );
}

export interface WaitDeps {
  sleep: (ms: number) => Promise<void>;
  /** Milliseconds between two checks. */
  waitMs?: number;
  /** Checks before giving up. */
  attempts?: number;
}

const DEFAULT_WAIT_MS = 500;
const DEFAULT_ATTEMPTS = 8;

/**
 * Checks until it passes or the attempts run out; returns whether it passed. A check that throws counts as "not yet":
 * a backend that does not answer for a second must not break the run.
 */
export async function waitUntil(check: () => Promise<boolean>, deps: WaitDeps): Promise<boolean> {
  const waitMs = deps.waitMs ?? DEFAULT_WAIT_MS;
  const attempts = deps.attempts ?? DEFAULT_ATTEMPTS;
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      if (await check()) return true;
    } catch {
      // not yet
    }
    if (attempt < attempts) await deps.sleep(waitMs);
  }
  return false;
}
