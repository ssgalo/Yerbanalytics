import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import type { HardwareData } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const flota: HardwareData = {
  dispositivos: [],
  total: 0,
  operativos: 0,
  bateriaBaja: 0,
  fueraDeServicio: 0,
  averiados: 0,
  incompletos: [],
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

describe('HttpRepository · reparación de hardware (S-05)', () => {
  it('registerRepair hace POST /hardware/{id}/reparacion con el detalle y devuelve la flota actualizada', async () => {
    const fetchFn = stubFetch(200, flota);

    const data = await new HttpRepository(BASE).registerRepair('DEV-101', 'Se destapó el emisor');

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/hardware/DEV-101/reparacion`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ detalle: 'Se destapó el emisor' });
    expect(data).toEqual(flota);
  });

  it('el id viaja codificado en la ruta', async () => {
    const fetchFn = stubFetch(200, flota);

    await new HttpRepository(BASE).registerRepair('DEV 1/2', 'x');

    const [url] = fetchFn.mock.calls[0] as unknown as [string];
    expect(url).toBe(`${BASE}/hardware/DEV%201%2F2/reparacion`);
  });

  it('un rechazo del backend (400 o 409) llega con su motivo', async () => {
    stubFetch(409, { error: 'El dispositivo «DEV-101» no tiene una falla registrada.' });

    await expect(new HttpRepository(BASE).registerRepair('DEV-101', 'x')).rejects.toThrow(/no tiene una falla/);
  });

  it('una falla sin cuerpo explica qué se intentaba hacer', async () => {
    stubFetch(500);

    await expect(new HttpRepository(BASE).registerRepair('DEV-101', 'x')).rejects.toThrow(/500.*reparación/);
  });
});
