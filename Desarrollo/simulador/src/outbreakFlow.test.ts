import assert from 'node:assert/strict';
import { test } from 'node:test';
import { pickOutbreakSectors } from './outbreakFlow';
import type { SectorRef } from './types';

/** A zone of `count` sectors, numbered like the backend does (`MZ-1-001`…). */
function zone(count: number, zoneId = 'MZ-1'): SectorRef[] {
  return Array.from({ length: count }, (_, i) => ({
    id: `${zoneId}-${String(i + 1).padStart(3, '0')}`,
    zonaId: zoneId,
    zonaName: `Macro-zona ${zoneId}`,
  }));
}

test('takes adjacent sectors from the middle of the zone, so they have neighbors all around', () => {
  const picked = pickOutbreakSectors(zone(100), 3);

  assert.deepEqual(picked.map((s) => s.id), ['MZ-1-051', 'MZ-1-052', 'MZ-1-053']);
});

test('does not depend on the order the sectors arrive in', () => {
  const shuffled = [...zone(100)].reverse();

  assert.deepEqual(pickOutbreakSectors(shuffled, 3).map((s) => s.id), ['MZ-1-051', 'MZ-1-052', 'MZ-1-053']);
});

test('in a small zone it still returns the requested sectors, as far from the end as it can', () => {
  assert.deepEqual(pickOutbreakSectors(zone(4), 3).map((s) => s.id), ['MZ-1-002', 'MZ-1-003', 'MZ-1-004']);
  assert.deepEqual(pickOutbreakSectors(zone(3), 3).map((s) => s.id), ['MZ-1-001', 'MZ-1-002', 'MZ-1-003']);
});

test('a zone with fewer sectors than asked for gives none: there is no outbreak to show', () => {
  assert.deepEqual(pickOutbreakSectors(zone(2), 3), []);
  assert.deepEqual(pickOutbreakSectors([], 3), []);
});

test('asking for no sectors gives none', () => {
  assert.deepEqual(pickOutbreakSectors(zone(100), 0), []);
});
