import assert from 'node:assert/strict';
import { test } from 'node:test';
import { faultyDevicesOf, isMarkedFaulty, sectorsWithValve, sectorsWithoutValve, type HardwareDevice } from './hardwareCoverage';
import type { SectorRef } from './types';

const sector = (id: string, zonaId = 'MZ-1'): SectorRef => ({ id, zonaId, zonaName: `Macro-zona ${zonaId.slice(-1)}` });

const device = (tipo: string, sectorId: string | null, zonaId: string | null = null): HardwareDevice => ({
  id: `DEV-${tipo}-${sectorId ?? zonaId}`,
  serial: `${tipo}-${sectorId ?? zonaId}`,
  tipo,
  sectorId,
  zonaId,
});

test('a sector with its electrovalve registered is covered', () => {
  const sectors = [sector('MZ-1-001'), sector('MZ-1-002')];
  const devices = [device('electrovalvula', 'MZ-1-001')];

  assert.deepEqual(sectorsWithoutValve(sectors, devices).map((s) => s.id), ['MZ-1-002']);
});

test('a sector with no device at all lacks the valve too: that is most of the seeded nursery', () => {
  const sectors = [sector('MZ-1-001'), sector('MZ-1-002'), sector('MZ-1-003')];

  assert.deepEqual(sectorsWithoutValve(sectors, []).map((s) => s.id), ['MZ-1-001', 'MZ-1-002', 'MZ-1-003']);
});

test('a pump or a shade net does not stand in for the valve', () => {
  const sectors = [sector('MZ-1-001')];
  const devices = [device('bomba_peristaltica', 'MZ-1-001'), device('mediasombra', 'MZ-1-001')];

  assert.deepEqual(sectorsWithoutValve(sectors, devices).map((s) => s.id), ['MZ-1-001']);
});

test('the witness node of the zone does not stand in for a sector valve either', () => {
  const devices = [device('nodo_testigo', null, 'MZ-1')];

  assert.deepEqual(sectorsWithoutValve([sector('MZ-1-001')], devices).map((s) => s.id), ['MZ-1-001']);
});

test('a valve registered with a fault is still registered (reporting faults is another rule)', () => {
  const devices = [{ ...device('electrovalvula', 'MZ-1-001'), falla: 'Falla Hidráulica' }];

  assert.deepEqual(sectorsWithoutValve([sector('MZ-1-001')], devices), []);
});

test('only the sectors it is given are reported, in the order it is given', () => {
  const sectors = [sector('MZ-1-003'), sector('MZ-1-001')];
  // The valve of another zone's sector is irrelevant.
  const devices = [device('electrovalvula', 'MZ-2-001')];

  assert.deepEqual(sectorsWithoutValve(sectors, devices).map((s) => s.id), ['MZ-1-003', 'MZ-1-001']);
});

test('no sectors, nothing to report', () => {
  assert.deepEqual(sectorsWithoutValve([], [device('electrovalvula', 'MZ-1-001')]), []);
});

/* ------------------------------------------------------------------ S-05: valves to break, devices to repair */

test('sectorsWithValve is the counterpart of sectorsWithoutValve, in the order it is given', () => {
  const sectors = [sector('MZ-1-003'), sector('MZ-1-001'), sector('MZ-1-002')];
  const devices = [device('electrovalvula', 'MZ-1-001'), device('electrovalvula', 'MZ-1-003'), device('bomba_peristaltica', 'MZ-1-002')];

  assert.deepEqual(sectorsWithValve(sectors, devices).map((s) => s.id), ['MZ-1-003', 'MZ-1-001']);
  assert.deepEqual(sectorsWithoutValve(sectors, devices).map((s) => s.id), ['MZ-1-002']);
});

test('a fault is a non-blank falla', () => {
  assert.equal(isMarkedFaulty({ ...device('electrovalvula', 'MZ-1-001'), falla: 'SIN_CAUDAL' }), true);
  assert.equal(isMarkedFaulty({ ...device('electrovalvula', 'MZ-1-001'), falla: null }), false);
  assert.equal(isMarkedFaulty({ ...device('electrovalvula', 'MZ-1-001'), falla: '  ' }), false);
  assert.equal(isMarkedFaulty(device('electrovalvula', 'MZ-1-001')), false);
});

test('the faulty devices of a zone are those of its sectors, valves and shade nets alike', () => {
  const sectors = [sector('MZ-1-001'), sector('MZ-1-002'), sector('MZ-2-001', 'MZ-2')];
  const devices: HardwareDevice[] = [
    { ...device('electrovalvula', 'MZ-1-001'), falla: 'SIN_CAUDAL' },
    { ...device('mediasombra', 'MZ-1-002'), falla: 'MEDIASOMBRA_FIN_DE_CARRERA' },
    { ...device('electrovalvula', 'MZ-1-002'), falla: null },
    { ...device('electrovalvula', 'MZ-2-001'), falla: 'SIN_CAUDAL' },
  ];

  assert.deepEqual(
    faultyDevicesOf('MZ-1', sectors, devices).map((d) => d.id),
    ['DEV-electrovalvula-MZ-1-001', 'DEV-mediasombra-MZ-1-002'],
  );
  assert.deepEqual(faultyDevicesOf('MZ-3', sectors, devices), []);
});

test('the witness node of a zone is never repaired by the simulator: its state is not a fault', () => {
  const devices: HardwareDevice[] = [{ ...device('nodo_testigo', null, 'MZ-1'), falla: 'algo' }];

  assert.deepEqual(faultyDevicesOf('MZ-1', [sector('MZ-1-001')], devices), []);
});
