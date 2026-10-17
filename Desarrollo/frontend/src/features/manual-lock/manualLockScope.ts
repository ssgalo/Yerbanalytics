/* ============================================================
   Qué bloqueo rige para una vista: el de la macro-zona (sin sector) o el del sector. Un sector
   también queda alcanzado por el bloqueo de su zona, pero ese se reanuda desde la zona.
   ============================================================ */
import type { ManualLock } from '@/types/domain';

export interface ManualLockScope {
  zonaId: string;
  /** Sin sector, la vista es la de la macro-zona. */
  sectorId?: string;
}

export interface ScopedLocks {
  /** El bloqueo de esta vista, si lo hay. */
  own: ManualLock | null;
  /** El de la macro-zona que alcanza a este sector (sólo para vistas de sector). */
  inheritedFromZone: ManualLock | null;
  /** Sectores de la zona bloqueados por separado (sólo para la vista de la zona). */
  lockedSectors: ManualLock[];
}

export function locksForScope(locks: readonly ManualLock[], scope: ManualLockScope): ScopedLocks {
  const zoneLock = locks.find((l) => l.zonaId === scope.zonaId && l.sectorId === null) ?? null;
  if (scope.sectorId) {
    return {
      own: locks.find((l) => l.sectorId === scope.sectorId) ?? null,
      inheritedFromZone: zoneLock,
      lockedSectors: [],
    };
  }
  return {
    own: zoneLock,
    inheritedFromZone: null,
    lockedSectors: locks.filter((l) => l.zonaId === scope.zonaId && l.sectorId !== null),
  };
}
