import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ensureTankStock, planTankLoads, type TankStockDeps } from './tankStock';
import type { Supply, TankLevel } from './types';

function tank(zoneId: string, supply: Supply, levelMl: number, capacityMl = 20_000): TankLevel {
  return {
    zoneId, supply, supplyLabel: supply, capacityMl, levelMl, levelPct: (levelMl * 100) / capacityMl,
    low: false, updatedTs: 0, updatedBy: null,
  };
}

test('a zone without tanks gets both registered full', () => {
  assert.deepEqual(planTankLoads([], 'MZ-1'), [
    { supply: 'FERTILIZER', liters: 20, capacityLiters: 20 },
    { supply: 'FUNGICIDE', liters: 20, capacityLiters: 20 },
  ]);
});

test('a tank under half its capacity is topped up to full, without sending the capacity again', () => {
  const plans = planTankLoads([tank('MZ-1', 'FERTILIZER', 4_000), tank('MZ-1', 'FUNGICIDE', 15_000)], 'MZ-1');

  assert.deepEqual(plans, [{ supply: 'FERTILIZER', liters: 16 }]);
});

test('tanks with enough stock, or of another zone, are left alone', () => {
  const plans = planTankLoads(
    [tank('MZ-1', 'FERTILIZER', 10_000), tank('MZ-1', 'FUNGICIDE', 20_000), tank('MZ-2', 'FERTILIZER', 0)],
    'MZ-1',
  );

  assert.deepEqual(plans, []);
});

test('only the missing tank is registered when the other one exists', () => {
  const plans = planTankLoads([tank('MZ-3', 'FUNGICIDE', 20_000)], 'MZ-3');

  assert.deepEqual(plans, [{ supply: 'FERTILIZER', liters: 20, capacityLiters: 20 }]);
});

test('ensureTankStock loads what is missing and says what it did', async () => {
  const loads: unknown[] = [];
  const deps: TankStockDeps = {
    getTanks: async () => [tank('MZ-1', 'FUNGICIDE', 2_000)],
    loadTank: async (zoneId, supply, load) => {
      loads.push([zoneId, supply, load]);
    },
  };

  const lines = await ensureTankStock('MZ-1', deps);

  assert.deepEqual(loads, [
    ['MZ-1', 'FERTILIZER', { liters: 20, capacityLiters: 20 }],
    ['MZ-1', 'FUNGICIDE', { liters: 18 }],
  ]);
  assert.match(lines[0], /fertilizante.*registrado con 20 L/);
  assert.match(lines[1], /fitosanitario.*repuesto \(\+18 L\)/);
});

test('with stock everywhere it loads nothing', async () => {
  let loaded = 0;
  const deps: TankStockDeps = {
    getTanks: async () => [tank('MZ-1', 'FERTILIZER', 20_000), tank('MZ-1', 'FUNGICIDE', 20_000)],
    loadTank: async () => {
      loaded++;
    },
  };

  const lines = await ensureTankStock('MZ-1', deps);

  assert.equal(loaded, 0);
  assert.match(lines[0], /ya tienen stock/);
});

test('a rejected load aborts the preparation instead of pretending there is stock', async () => {
  const deps: TankStockDeps = {
    getTanks: async () => [],
    loadTank: async () => {
      throw new Error('403');
    },
  };

  await assert.rejects(ensureTankStock('MZ-1', deps), /403/);
});
