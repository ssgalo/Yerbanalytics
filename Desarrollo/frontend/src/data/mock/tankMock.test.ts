import { describe, expect, it } from 'vitest';
import { MockRepository } from './mockRepository';
import { TankLoadError } from '@/data/tankError';

describe('MockRepository · tanques de insumo', () => {
  it('arranca sin tanques registrados', async () => {
    expect(await new MockRepository(1).getTanks()).toEqual([]);
  });

  it('la primera carga registra el tanque con su capacidad', async () => {
    const repo = new MockRepository(1);

    const tank = await repo.loadTank('MZ-1', 'FERTILIZER', { liters: 20, capacityLiters: 50 });

    expect(tank).toMatchObject({
      zoneId: 'MZ-1',
      supply: 'FERTILIZER',
      supplyLabel: 'Fertilizante',
      capacityMl: 50_000,
      levelMl: 20_000,
      levelPct: 40,
      low: false,
    });
    expect(await repo.getTanks()).toEqual([tank]);
  });

  it('las cargas siguientes suman al nivel', async () => {
    const repo = new MockRepository(1);
    await repo.loadTank('MZ-1', 'FUNGICIDE', { liters: 10, capacityLiters: 40 });

    const tank = await repo.loadTank('MZ-1', 'FUNGICIDE', { liters: 5 });

    expect(tank.levelMl).toBe(15_000);
    expect(tank.capacityMl).toBe(40_000);
  });

  it('un tanque bajo el 20 % se marca para reponer', async () => {
    const tank = await new MockRepository(1).loadTank('MZ-1', 'FERTILIZER', { liters: 5, capacityLiters: 50 });
    expect(tank.low).toBe(true);
  });

  it('sin capacidad, el primer registro se rechaza', async () => {
    await expect(new MockRepository(1).loadTank('MZ-1', 'FERTILIZER', { liters: 5 })).rejects.toThrow(TankLoadError);
  });

  it('una carga que desborda el tanque se rechaza y no cambia el nivel', async () => {
    const repo = new MockRepository(1);
    await repo.loadTank('MZ-1', 'FERTILIZER', { liters: 40, capacityLiters: 50 });

    await expect(repo.loadTank('MZ-1', 'FERTILIZER', { liters: 20 })).rejects.toThrow(/no entra/);
    expect((await repo.getTanks())[0].levelMl).toBe(40_000);
  });

  it('los litros deben ser mayores que cero', async () => {
    const repo = new MockRepository(1);
    for (const liters of [0, -1, Number.NaN]) {
      await expect(repo.loadTank('MZ-1', 'FERTILIZER', { liters, capacityLiters: 50 })).rejects.toThrow(TankLoadError);
    }
  });

  it('lista los tanques ordenados por zona e insumo', async () => {
    const repo = new MockRepository(1);
    await repo.loadTank('MZ-2', 'FERTILIZER', { liters: 1, capacityLiters: 5 });
    await repo.loadTank('MZ-1', 'FUNGICIDE', { liters: 1, capacityLiters: 5 });
    await repo.loadTank('MZ-1', 'FERTILIZER', { liters: 1, capacityLiters: 5 });

    expect((await repo.getTanks()).map((t) => `${t.zoneId}/${t.supply}`)).toEqual([
      'MZ-1/FERTILIZER',
      'MZ-1/FUNGICIDE',
      'MZ-2/FERTILIZER',
    ]);
  });
});
