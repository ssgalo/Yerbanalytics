import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { TankLoadError } from '@/data/tankError';
import type { TankLevel } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const tank: TankLevel = {
  zoneId: 'MZ-1',
  supply: 'FERTILIZER',
  supplyLabel: 'Fertilizante',
  capacityMl: 50_000,
  levelMl: 20_000,
  levelPct: 40,
  low: false,
  updatedTs: 1759514400000,
  updatedBy: 'Ana',
};

function stubFetch(status: number, body?: unknown) {
  const fn = vi.fn(async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined) throw new Error('sin cuerpo');
      return body;
    },
  }));
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe('HttpRepository · tanques de insumo', () => {
  it('getTanks hace GET /tanques y devuelve la lista', async () => {
    const fetchFn = stubFetch(200, [tank]);

    const tanks = await new HttpRepository(BASE).getTanks();

    const [url] = fetchFn.mock.calls[0] as unknown as [string];
    expect(url.startsWith(`${BASE}/tanques?t=`)).toBe(true);
    expect(tanks).toEqual([tank]);
  });

  it('getTanks falla con el código si el backend no responde bien', async () => {
    stubFetch(500);
    await expect(new HttpRepository(BASE).getTanks()).rejects.toThrow(/500/);
  });

  it('loadTank hace POST /tanques/{zona}/{insumo}/recarga con la carga y devuelve el tanque', async () => {
    const fetchFn = stubFetch(200, tank);

    const result = await new HttpRepository(BASE).loadTank('MZ-1', 'FERTILIZER', { liters: 20, capacityLiters: 50 });

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/tanques/MZ-1/FERTILIZER/recarga`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ liters: 20, capacityLiters: 50 });
    expect(result).toEqual(tank);
  });

  it('un 400 se traduce a TankLoadError con el mensaje del backend', async () => {
    stubFetch(400, { error: 'La carga no entra.' });
    await expect(new HttpRepository(BASE).loadTank('MZ-1', 'FERTILIZER', { liters: 99 })).rejects.toThrow(
      new TankLoadError('La carga no entra.'),
    );
  });

  it('un 404 (macro-zona inexistente) también es un rechazo esperable', async () => {
    stubFetch(404, { error: 'La macro-zona MZ-9 no existe.' });
    await expect(new HttpRepository(BASE).loadTank('MZ-9', 'FERTILIZER', { liters: 1 })).rejects.toBeInstanceOf(
      TankLoadError,
    );
  });

  it('otro error es una falla, no un rechazo', async () => {
    stubFetch(500);
    const rejection = new HttpRepository(BASE).loadTank('MZ-1', 'FERTILIZER', { liters: 1 });
    await expect(rejection).rejects.toThrow(/500/);
    await expect(rejection).rejects.not.toBeInstanceOf(TankLoadError);
  });
});
