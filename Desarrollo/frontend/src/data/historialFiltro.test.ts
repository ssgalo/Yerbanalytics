import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './http/httpRepository';
import { MockRepository } from './mock/mockRepository';

/**
 * El historial real acumula cientos de miles de eventos `Info`: bajarlo entero colgaba la pestaña y el
 * backend. El recorte (filtros y tope) lo resuelve el origen de datos, nunca la vista.
 */
describe('getHistory · recorte en el origen', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('http: manda sector, tipo, fechas y tope como parámetros de la consulta', async () => {
    const fetch = vi.fn(async () => ({ ok: true, status: 200, json: async () => [] }));
    vi.stubGlobal('fetch', fetch);

    await new HttpRepository('http://localhost:8000/api').getHistory({
      sector: 'MZ-2-014',
      tipo: 'Riego',
      desde: 100,
      hasta: 200,
      limit: 5,
    });

    const url = new URL(String((fetch.mock.calls[0] as unknown[])[0]));
    expect(url.pathname).toBe('/api/historial');
    expect(Object.fromEntries(url.searchParams)).toMatchObject({
      sector: 'MZ-2-014',
      tipo: 'Riego',
      desde: '100',
      hasta: '200',
      limit: '5',
    });
  });

  it('mock: aplica sector, fechas y tope, y devuelve los más recientes', async () => {
    const repo = new MockRepository(20260613);
    const todos = await repo.getHistory();
    const sector = todos.find((r) => r.sectorId !== '—')!.sectorId;

    const delSector = await repo.getHistory({ sector });
    expect(delSector.length).toBeGreaterThan(0);
    expect(delSector.every((r) => r.sectorId === sector)).toBe(true);

    const corte = todos[Math.floor(todos.length / 2)].ts;
    const hasta = await repo.getHistory({ hasta: corte });
    expect(hasta.every((r) => r.ts <= corte)).toBe(true);

    const tres = await repo.getHistory({ limit: 3 });
    expect(tres).toEqual(todos.slice(0, 3));
  });
});
