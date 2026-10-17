import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { ManualLockError } from '@/data/manualLockError';
import type { ManualLock } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const bloqueo: ManualLock = {
  id: 42,
  zonaId: 'MZ-2',
  sectorId: null,
  activadoPor: 'Ana',
  activadoTs: 1759514400000,
  motivo: 'mantenimiento',
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

describe('HttpRepository · bloqueo manual', () => {
  it('getManualLocks hace GET /bloqueos y devuelve la lista', async () => {
    const fetchFn = stubFetch(200, [bloqueo]);

    const locks = await new HttpRepository(BASE).getManualLocks();

    const [url] = fetchFn.mock.calls[0] as unknown as [string];
    expect(url.startsWith(`${BASE}/bloqueos?t=`)).toBe(true);
    expect(locks).toEqual([bloqueo]);
  });

  it('getManualLocks falla con el código si el backend no responde bien', async () => {
    stubFetch(500);
    await expect(new HttpRepository(BASE).getManualLocks()).rejects.toThrow(/500/);
  });

  it('activateManualLock hace POST /bloqueos con el cuerpo y devuelve el bloqueo creado', async () => {
    const fetchFn = stubFetch(201, bloqueo);

    const creado = await new HttpRepository(BASE).activateManualLock({
      zonaId: 'MZ-2',
      motivo: 'mantenimiento',
    });

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/bloqueos`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ zonaId: 'MZ-2', motivo: 'mantenimiento' });
    expect(creado).toEqual(bloqueo);
  });

  it.each([400, 409])(
    'activateManualLock convierte el %i del backend en ManualLockError con su mensaje',
    async (status) => {
      stubFetch(status, { error: 'La macro-zona MZ-2 ya tiene un bloqueo vigente.' });

      await expect(
        new HttpRepository(BASE).activateManualLock({ zonaId: 'MZ-2', motivo: 'x' }),
      ).rejects.toEqual(new ManualLockError('La macro-zona MZ-2 ya tiene un bloqueo vigente.'));
    },
  );

  it('releaseManualLock hace DELETE /bloqueos/{id}', async () => {
    const fetchFn = stubFetch(204);

    await new HttpRepository(BASE).releaseManualLock(42);

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/bloqueos/42`);
    expect(init.method).toBe('DELETE');
  });

  it('releaseManualLock convierte el 404 en ManualLockError', async () => {
    stubFetch(404, { error: 'No hay un bloqueo vigente con id 42.' });

    await expect(new HttpRepository(BASE).releaseManualLock(42)).rejects.toBeInstanceOf(
      ManualLockError,
    );
  });
});
