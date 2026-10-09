import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { SecuenciaRechazadaError } from '@/data/secuenciaError';
import type { Secuencia } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const secuencia: Secuencia = {
  id: 's-1',
  tipo: 'RIEGO',
  estado: 'EN_CURSO',
  zonaId: 'MZ-1',
  sectorId: 'MZ-1-001',
  parametros: { duracionSeg: 15, esperaSeg: null },
  iniciadaEn: 1,
  finalizadaEn: null,
  cancelacionSolicitada: false,
  error: null,
  lectura: null,
  pasos: [],
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

const llamada = (fn: ReturnType<typeof stubFetch>) =>
  fn.mock.calls[0] as unknown as [string, RequestInit];

afterEach(() => vi.unstubAllGlobals());

describe('HttpRepository · secuencias', () => {
  it('iniciarSecuencia hace POST /secuencias con {tipo, parametros}', async () => {
    const fetchFn = stubFetch(202, secuencia);

    const s = await new HttpRepository(BASE).iniciarSecuencia('RIEGO', { duracionSeg: 15 });

    const [url, init] = llamada(fetchFn);
    expect(url).toBe(`${BASE}/secuencias`);
    expect(init.method).toBe('POST');
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual({
      tipo: 'RIEGO',
      parametros: { duracionSeg: 15 },
    });
    expect(s.id).toBe('s-1');
  });

  it('iniciarSecuencia sin parámetros manda parametros vacío', async () => {
    const fetchFn = stubFetch(202, secuencia);
    await new HttpRepository(BASE).iniciarSecuencia('LECTURA');
    expect(JSON.parse(llamada(fetchFn)[1].body as string)).toEqual({
      tipo: 'LECTURA',
      parametros: {},
    });
  });

  it.each([400, 409])(
    'iniciarSecuencia con %i rechaza con SecuenciaRechazadaError y el mensaje del backend',
    async (status) => {
      stubFetch(status, { error: 'Hay una pasada del riel en curso.' });

      const intento = new HttpRepository(BASE).iniciarSecuencia('RIEGO');

      await expect(intento).rejects.toBeInstanceOf(SecuenciaRechazadaError);
      await expect(intento).rejects.toThrow('Hay una pasada del riel en curso.');
    },
  );

  it('iniciarSecuencia con otro error de servidor rechaza con un Error común', async () => {
    stubFetch(500);
    const intento = new HttpRepository(BASE).iniciarSecuencia('RIEGO');
    await expect(intento).rejects.not.toBeInstanceOf(SecuenciaRechazadaError);
    await expect(intento).rejects.toThrow(/500/);
  });

  it('getSecuenciaActual lee /secuencias/actual', async () => {
    const fetchFn = stubFetch(200, secuencia);

    const s = await new HttpRepository(BASE).getSecuenciaActual();

    expect(llamada(fetchFn)[0]).toMatch(/^http:\/\/localhost:8000\/api\/secuencias\/actual/);
    expect(s?.tipo).toBe('RIEGO');
  });

  it('getSecuenciaActual con 204 devuelve null', async () => {
    stubFetch(204);
    expect(await new HttpRepository(BASE).getSecuenciaActual()).toBeNull();
  });

  it('getSecuenciaActual con error de servidor rechaza', async () => {
    stubFetch(503);
    await expect(new HttpRepository(BASE).getSecuenciaActual()).rejects.toThrow(/503/);
  });

  it('cancelarSecuencia hace POST /secuencias/actual/cancelar', async () => {
    const fetchFn = stubFetch(200, secuencia);

    await new HttpRepository(BASE).cancelarSecuencia();

    const [url, init] = llamada(fetchFn);
    expect(url).toBe(`${BASE}/secuencias/actual/cancelar`);
    expect(init.method).toBe('POST');
  });

  it('cancelarSecuencia con 409 rechaza con SecuenciaRechazadaError', async () => {
    stubFetch(409, { error: 'No hay una secuencia en curso.' });
    await expect(new HttpRepository(BASE).cancelarSecuencia()).rejects.toThrow(
      SecuenciaRechazadaError,
    );
  });
});
