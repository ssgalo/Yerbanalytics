/* ============================================================
   What the hardware registry says about the sectors of a zone. Pure: no fetch, no React.

   The "Hardware incompleto" scenario (S-04) needs a sector that has no electrovalve registered, because the backend
   does not irrigate a sector it cannot actuate. The simulator never changes the registry (it only reads it): it looks
   for sectors that ALREADY lack their valve and tells the operator where to look.
   ============================================================ */
import type { SectorRef } from './types';

/** The part of a device of the registry (`GET /api/hardware`) this module reads. */
export interface HardwareDevice {
  id: string;
  serial: string;
  /** `nodo_testigo` | `electrovalvula` | `bomba_peristaltica` | `mediasombra`. */
  tipo: string;
  zonaId: string | null;
  sectorId: string | null;
  /**
   * The fault the backend marked (S-05): the `tipo` of the node error, `null` when healthy. A registered device with a
   * fault is still registered, so it still counts as mapped for S-04.
   */
  falla?: string | null;
}

/** Type of the irrigation valve, as the backend registers it (`HardwareService.ELECTROVALVULA`). */
export const VALVE_TYPE = 'electrovalvula';

/**
 * The sectors, of those given, that have no electrovalve registered (no device at all included: that is most of the
 * seeded nursery). Keeps the order it is given.
 */
export function sectorsWithoutValve(sectors: SectorRef[], devices: HardwareDevice[]): SectorRef[] {
  const withValve = new Set(devices.filter((d) => d.tipo === VALVE_TYPE && d.sectorId !== null).map((d) => d.sectorId));
  return sectors.filter((s) => !withValve.has(s.id));
}

/** The sectors, of those given, that have their electrovalve registered: where a hydraulic fault can be marked. */
export function sectorsWithValve(sectors: SectorRef[], devices: HardwareDevice[]): SectorRef[] {
  const without = new Set(sectorsWithoutValve(sectors, devices).map((s) => s.id));
  return sectors.filter((s) => !without.has(s.id));
}

/** `true` if the backend has the device marked as faulty (S-05). */
export function isMarkedFaulty(device: HardwareDevice): boolean {
  return typeof device.falla === 'string' && device.falla.trim() !== '';
}

/**
 * The devices of the zone's sectors that are marked as faulty (S-05): what the repair scenario registers a repair for.
 * Only sector actuators: the witness node is not repaired, it recovers by answering again.
 */
export function faultyDevicesOf(zonaId: string, sectors: SectorRef[], devices: HardwareDevice[]): HardwareDevice[] {
  const ofZone = new Set(sectors.filter((s) => s.zonaId === zonaId).map((s) => s.id));
  return devices.filter((d) => d.sectorId !== null && ofZone.has(d.sectorId) && isMarkedFaulty(d));
}
