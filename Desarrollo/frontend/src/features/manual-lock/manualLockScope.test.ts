import { describe, expect, it } from 'vitest';
import { locksForScope } from './manualLockScope';
import type { ManualLock } from '@/types/domain';

const lock = (id: number, zonaId: string, sectorId: string | null): ManualLock => ({
  id,
  zonaId,
  sectorId,
  activadoPor: 'Ana',
  activadoTs: 1,
  motivo: 'x',
});

const zone1 = lock(1, 'MZ-1', null);
const sector1 = lock(2, 'MZ-1', 'MZ-1-001');
const zone2 = lock(3, 'MZ-2', null);
const all = [zone1, sector1, zone2];

describe('locksForScope', () => {
  it('en la vista de la zona, el propio es el de la zona y lista los sectores bloqueados aparte', () => {
    expect(locksForScope(all, { zonaId: 'MZ-1' })).toEqual({
      own: zone1,
      inheritedFromZone: null,
      lockedSectors: [sector1],
    });
  });

  it('en la vista del sector, el propio es el del sector y el de la zona se informa como heredado', () => {
    expect(locksForScope(all, { zonaId: 'MZ-1', sectorId: 'MZ-1-001' })).toEqual({
      own: sector1,
      inheritedFromZone: zone1,
      lockedSectors: [],
    });
  });

  it('un sector sin bloqueo propio en una zona libre no tiene nada', () => {
    expect(locksForScope([zone2], { zonaId: 'MZ-1', sectorId: 'MZ-1-002' })).toEqual({
      own: null,
      inheritedFromZone: null,
      lockedSectors: [],
    });
  });

  it('el bloqueo de otra zona no cuenta', () => {
    expect(locksForScope([zone2], { zonaId: 'MZ-1' }).own).toBeNull();
  });
});
