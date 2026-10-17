import assert from 'node:assert/strict';
import { test } from 'node:test';
import { shadeMarked, valveMarked, waitUntil } from './faultFlow';
import type { HardwareDevice } from './hardwareCoverage';
import type { SectorRef } from './types';

const device = (tipo: string, sectorId: string, falla: string | null = null): HardwareDevice => ({
  id: `DEV-${tipo}-${sectorId}`,
  serial: `${tipo}-${sectorId}`,
  tipo,
  sectorId,
  zonaId: null,
  falla,
});

const sector = (id: string, zonaId = 'MZ-1'): SectorRef => ({ id, zonaId, zonaName: zonaId });

test('the valve of a sector is marked when the backend put a fault on it', () => {
  const devices = [device('electrovalvula', 'MZ-1-012', 'SIN_CAUDAL'), device('electrovalvula', 'MZ-1-013')];

  assert.equal(valveMarked(devices, 'MZ-1-012'), true);
  assert.equal(valveMarked(devices, 'MZ-1-013'), false);
  assert.equal(valveMarked(devices, 'MZ-1-099'), false);
});

test('a fault on the pump or the shade net of the sector is not a fault of its valve', () => {
  const devices = [device('bomba_peristaltica', 'MZ-1-012', 'x'), device('mediasombra', 'MZ-1-012', 'y')];

  assert.equal(valveMarked(devices, 'MZ-1-012'), false);
});

test('the shade net is marked for the whole zone when any of its sectors has it marked', () => {
  const sectors = [sector('MZ-1-001'), sector('MZ-1-002'), sector('MZ-2-001', 'MZ-2')];
  const devices = [device('mediasombra', 'MZ-1-002', 'MEDIASOMBRA_FIN_DE_CARRERA')];

  assert.equal(shadeMarked(devices, 'MZ-1', sectors), true);
  assert.equal(shadeMarked(devices, 'MZ-2', sectors), false);
});

test('waitUntil returns as soon as the check passes, without waiting more', async () => {
  const waits: number[] = [];
  let calls = 0;

  const ok = await waitUntil(async () => ++calls === 3, { sleep: async (ms) => void waits.push(ms), waitMs: 100, attempts: 10 });

  assert.equal(ok, true);
  assert.equal(calls, 3);
  assert.deepEqual(waits, [100, 100]);
});

test('waitUntil gives up after the attempts and says the fault never showed up', async () => {
  let calls = 0;

  const ok = await waitUntil(async () => (calls++, false), { sleep: async () => undefined, waitMs: 1, attempts: 4 });

  assert.equal(ok, false);
  assert.equal(calls, 4);
});

test('a check that fails (backend unreachable) counts as "not yet" and does not break the run', async () => {
  let calls = 0;

  const ok = await waitUntil(
    async () => {
      if (++calls < 2) throw new Error('backend caído');
      return true;
    },
    { sleep: async () => undefined, waitMs: 1, attempts: 5 },
  );

  assert.equal(ok, true);
});
