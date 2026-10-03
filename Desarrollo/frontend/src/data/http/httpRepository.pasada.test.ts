import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { PasadaRechazadaError } from '@/data/pasadaError';
import type { Pasada, PasoPasada } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const paso = (n: number, imagenUrl: string | null): PasoPasada => ({
  n,
  tipo: 'CAPTURAR',
  posicion: 1,
  sectorId: 'MZ-1-001',
  estado: 'OK',
  codigoError: null,
  detalle: null,
  commandId: null,
  ordenId: 'o-1',
  estadoOrden: 'RECIBIDA',
  capturaId: 'CAP-000042',
  imagenUrl,
  diagnostico: null,
  iniciadoEn: 1,
  terminadoEn: 2,
});

const pasada = (pasos: PasoPasada[]): Pasada => ({
  id: 'p-1',
  estado: 'EN_CURSO',
  iniciadaEn: 1,
  finalizadaEn: null,
  cancelacionSolicitada: false,
  error: null,
  pasos,
});

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

describe('HttpRepository · pasada del riel', () => {
  it('iniciarPasada hace POST /pasadas sin body y absolutiza cada imagenUrl', async () => {
    const fetchFn = stubFetch(202, pasada([paso(1, '/api/capturas/CAP-000042/imagen'), paso(2, null)]));

    const p = await new HttpRepository(BASE).iniciarPasada();

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/pasadas`);
    expect(init.method).toBe('POST');
    expect(init.body).toBeUndefined();
    expect(p.pasos[0].imagenUrl).toBe('http://localhost:8000/api/capturas/CAP-000042/imagen');
    expect(p.pasos[1].imagenUrl).toBeNull();
  });

  it('iniciarPasada con 409 rechaza con PasadaRechazadaError y el mensaje del backend', async () => {
    stubFetch(409, { error: 'No hay ningún dispositivo de captura conectado.' });

    const intento = new HttpRepository(BASE).iniciarPasada();

    await expect(intento).rejects.toBeInstanceOf(PasadaRechazadaError);
    await expect(intento).rejects.toThrow('No hay ningún dispositivo de captura conectado.');
  });

  it('iniciarPasada con otro error de servidor rechaza con un Error común', async () => {
    stubFetch(500);

    const intento = new HttpRepository(BASE).iniciarPasada();

    await expect(intento).rejects.not.toBeInstanceOf(PasadaRechazadaError);
    await expect(intento).rejects.toThrow(/500/);
  });

  it('getPasadaActual lee /pasadas/actual', async () => {
    const fetchFn = stubFetch(200, pasada([paso(1, '/api/capturas/CAP-000042/imagen')]));

    const p = await new HttpRepository(BASE).getPasadaActual();

    expect((fetchFn.mock.calls[0] as unknown as [string])[0]).toMatch(/^http:\/\/localhost:8000\/api\/pasadas\/actual/);
    expect(p?.pasos[0].imagenUrl).toBe('http://localhost:8000/api/capturas/CAP-000042/imagen');
  });

  it('getPasadaActual con 204 devuelve null', async () => {
    stubFetch(204);
    expect(await new HttpRepository(BASE).getPasadaActual()).toBeNull();
  });

  it('getPasadaActual con error de servidor rechaza', async () => {
    stubFetch(503);
    await expect(new HttpRepository(BASE).getPasadaActual()).rejects.toThrow(/503/);
  });

  it('cancelarPasada hace POST /pasadas/actual/cancelar', async () => {
    const fetchFn = stubFetch(200, pasada([]));

    await new HttpRepository(BASE).cancelarPasada();

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/pasadas/actual/cancelar`);
    expect(init.method).toBe('POST');
  });

  it('cancelarPasada con 409 rechaza con PasadaRechazadaError', async () => {
    stubFetch(409, { error: 'No hay una pasada en curso.' });
    await expect(new HttpRepository(BASE).cancelarPasada()).rejects.toThrow(PasadaRechazadaError);
  });

  it('getDemoExpo lee /configuracion/demo-expo y devuelve el booleano', async () => {
    const fetchFn = stubFetch(200, { visible: true });

    expect(await new HttpRepository(BASE).getDemoExpo()).toBe(true);
    expect((fetchFn.mock.calls[0] as unknown as [string])[0]).toMatch(/\/configuracion\/demo-expo/);
  });

  it('setDemoExpo hace PUT con {visible} y devuelve lo guardado', async () => {
    const fetchFn = stubFetch(200, { visible: false });

    const r = await new HttpRepository(BASE).setDemoExpo(false);

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/configuracion/demo-expo`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({ visible: false });
    expect(r).toBe(false);
  });

  it('setDemoExpo con error rechaza', async () => {
    stubFetch(400, { error: 'falta visible' });
    await expect(new HttpRepository(BASE).setDemoExpo(true)).rejects.toThrow('falta visible');
  });
});
