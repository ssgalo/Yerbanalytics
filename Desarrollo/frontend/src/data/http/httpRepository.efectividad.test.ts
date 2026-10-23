import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';

const BASE = 'http://localhost:8000/api';

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

describe('HttpRepository · revisión de una acción sin efectividad (S-06)', () => {
  it('registerEffectivenessReview hace POST /efectividad/{id}/revision con el detalle y devuelve la revisión', async () => {
    const revision = { eventId: 'ev-1', kind: 'IRRIGATION', clearedEvents: 2, reviewedTs: 1000 };
    const fetchMock = stubFetch(200, revision);

    const res = await new HttpRepository(BASE).registerEffectivenessReview('ev-1', 'Destapé el emisor');

    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/efectividad/ev-1/revision`);
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toEqual({ detail: 'Destapé el emisor' });
    expect(res).toEqual(revision);
  });

  it('el id viaja codificado', async () => {
    const fetchMock = stubFetch(200, { eventId: 'a/b', kind: 'FUNGICIDE', clearedEvents: 1, reviewedTs: 1 });

    await new HttpRepository(BASE).registerEffectivenessReview('a/b', 'x');

    expect((fetchMock.mock.calls[0] as unknown as [string])[0]).toBe(`${BASE}/efectividad/a%2Fb/revision`);
  });

  it('un rechazo del backend se propaga con su motivo', async () => {
    stubFetch(409, { error: 'El evento ev-1 no es una acción sin efectividad que siga bloqueando.' });

    await expect(new HttpRepository(BASE).registerEffectivenessReview('ev-1', 'x')).rejects.toThrow(/siga bloqueando/);
  });

  it('una falla sin cuerpo explica qué se intentaba hacer', async () => {
    stubFetch(500);

    await expect(new HttpRepository(BASE).registerEffectivenessReview('ev-1', 'x')).rejects.toThrow(/500.*revisión/);
  });
});
