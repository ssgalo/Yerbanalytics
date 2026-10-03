import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import type { DiagnosisCard, NurseryData } from '@/types/domain';

/**
 * El backend devuelve las imágenes como ruta relativa a la raíz (`/api/capturas/...`), porque
 * no conoce su propia URL pública. El navegador la resolvería contra el origen de la página
 * —el servidor de Vite— y daría 404, con el síntoma engañoso de que el diagnóstico "no tiene
 * foto". Estos tests fijan esa resolución.
 */
describe('HttpRepository · resolución de imágenes', () => {
  const BASE = 'http://localhost:8000/api';

  const card = (id: string, imagenUrl: string | null): DiagnosisCard => ({
    id,
    sectorId: 'MZ-1-001',
    zonaName: 'Macro-zona 1',
    estado: 'Clorosis',
    conf: 92,
    sev: 'Alta',
    sevSoft: '#fff',
    sevInk: '#000',
    thumb: 'linear-gradient(#000,#fff)',
    time: 'recién',
    concluyente: true,
    imagenUrl,
  });

  function stubNursery(diagnoses: DiagnosisCard[]) {
    const data = {
      diagnoses,
      diagById: Object.fromEntries(diagnoses.map((d) => [d.id, d])),
      recentDiag: diagnoses,
    } as unknown as NurseryData;

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: true, status: 200, json: async () => data })),
    );
  }

  afterEach(() => vi.unstubAllGlobals());

  it('convierte la ruta relativa en absoluta contra el backend', async () => {
    stubNursery([card('DX-00001', '/api/capturas/CAP-000001/imagen')]);

    const nursery = await new HttpRepository(BASE).getNursery();

    expect(nursery.diagnoses[0].imagenUrl).toBe(
      'http://localhost:8000/api/capturas/CAP-000001/imagen',
    );
  });

  it('resuelve también en diagById y recentDiag, que alimentan el modal', async () => {
    stubNursery([card('DX-00001', '/api/capturas/CAP-000001/imagen')]);

    const nursery = await new HttpRepository(BASE).getNursery();

    // El modal se abre desde diagById: si ahí quedara la ruta relativa, la tarjeta mostraría
    // la foto y el modal no.
    expect(nursery.diagById['DX-00001'].imagenUrl).toMatch(/^http:\/\/localhost:8000\//);
    expect(nursery.recentDiag[0].imagenUrl).toMatch(/^http:\/\/localhost:8000\//);
  });

  it('deja intactos los diagnósticos sin imagen', async () => {
    stubNursery([card('DG-001', null)]);

    const nursery = await new HttpRepository(BASE).getNursery();

    expect(nursery.diagnoses[0].imagenUrl).toBeNull();
  });

  it('no toca las URLs que ya son absolutas', async () => {
    for (const url of [
      'https://cdn.example/foto.jpg',
      'data:image/svg+xml;utf8,<svg/>',
      'blob:http://localhost/abc',
    ]) {
      stubNursery([card('DX-1', url)]);
      const nursery = await new HttpRepository(BASE).getNursery();
      expect(nursery.diagnoses[0].imagenUrl).toBe(url);
    }
  });

  it('funciona con el backend en otro host, como lo ve la app de cámara', async () => {
    stubNursery([card('DX-00001', '/api/capturas/CAP-000009/imagen')]);

    const nursery = await new HttpRepository('https://192.168.1.56:8443/api').getNursery();

    expect(nursery.diagnoses[0].imagenUrl).toBe(
      'https://192.168.1.56:8443/api/capturas/CAP-000009/imagen',
    );
  });
});

/** Motor de reglas: catálogo de parámetros, esquema del DAG y traza de evaluación. */
describe('HttpRepository · motor de reglas', () => {
  const BASE = 'http://localhost:8000/api';

  interface Llamada {
    url: string;
    init?: RequestInit;
  }

  function stubFetch(respuesta: { status: number; body?: unknown; sinJson?: boolean }) {
    const llamadas: Llamada[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string, init?: RequestInit) => {
        llamadas.push({ url, init });
        return {
          ok: respuesta.status >= 200 && respuesta.status < 300,
          status: respuesta.status,
          json: async () => {
            if (respuesta.sinJson) throw new SyntaxError('Unexpected end of JSON input');
            return respuesta.body;
          },
        };
      }),
    );
    return llamadas;
  }

  afterEach(() => vi.unstubAllGlobals());

  it('getCatalogoReglas pide /rules/parametros y devuelve el cuerpo', async () => {
    const catalogo = { reglas: [], parametros: [] };
    const llamadas = stubFetch({ status: 200, body: catalogo });

    expect(await new HttpRepository(BASE).getCatalogoReglas()).toEqual(catalogo);
    expect(llamadas[0].url).toMatch(/^http:\/\/localhost:8000\/api\/rules\/parametros\?t=\d+$/);
  });

  it('saveParametros hace PUT con { cambios } y devuelve el catálogo', async () => {
    const catalogo = { reglas: [], parametros: [] };
    const llamadas = stubFetch({ status: 200, body: catalogo });
    const cambios = [
      { clave: 'riego.umbral-humedad', valor: '40' },
      { clave: 'riego.lluvia-probabilidad', valor: null },
    ];

    expect(await new HttpRepository(BASE).saveParametros(cambios)).toEqual(catalogo);

    expect(llamadas[0].url).toBe(`${BASE}/rules/parametros`);
    expect(llamadas[0].init?.method).toBe('PUT');
    expect(JSON.parse(String(llamadas[0].init?.body))).toEqual({ cambios });
  });

  it('saveParametros convierte un 400 con errores en ParametrosInvalidosError', async () => {
    const errores = [{ clave: 'riego.umbral-humedad', mensaje: 'Debe estar entre 35 y 60 %.' }];
    stubFetch({ status: 400, body: { errores } });

    const promesa = new HttpRepository(BASE).saveParametros([
      { clave: 'riego.umbral-humedad', valor: '99' },
    ]);

    await expect(promesa).rejects.toBeInstanceOf(ParametrosInvalidosError);
    await expect(promesa).rejects.toMatchObject({ errores });
  });

  it('saveParametros tolera un 400 sin `errores` y un cuerpo que no es JSON', async () => {
    stubFetch({ status: 400, body: { error: 'JSON inválido' } });
    await expect(new HttpRepository(BASE).saveParametros([])).rejects.toThrow('JSON inválido');

    stubFetch({ status: 400, sinJson: true });
    await expect(new HttpRepository(BASE).saveParametros([])).rejects.toThrow(/400/);
  });

  it('getRuleSchema pide /rules/schema', async () => {
    const schema = { nodes: [], edges: [] };
    const llamadas = stubFetch({ status: 200, body: schema });

    expect(await new HttpRepository(BASE).getRuleSchema()).toEqual(schema);
    expect(llamadas[0].url).toBe(`${BASE}/rules/schema`);
  });

  it('getTrazaEvaluacion devuelve el cuerpo en 200 y filtra por origen', async () => {
    const traza = { sectorId: 'MZ-1-001', reglas: [] };
    const llamadas = stubFetch({ status: 200, body: traza });

    expect(await new HttpRepository(BASE).getTrazaEvaluacion('MZ-1-001', 'BARRIDO')).toEqual(traza);
    expect(llamadas[0].url).toMatch(
      /^http:\/\/localhost:8000\/api\/rules\/evaluaciones\/MZ-1-001\?origen=BARRIDO&t=\d+$/,
    );
  });

  it('getTrazaEvaluacion sin origen no manda el parámetro', async () => {
    const llamadas = stubFetch({ status: 200, body: {} });
    await new HttpRepository(BASE).getTrazaEvaluacion('MZ-1-001');
    expect(llamadas[0].url).not.toContain('origen=');
  });

  it('getTrazaEvaluacion devuelve null en 204 (todavía sin evaluar)', async () => {
    stubFetch({ status: 204, sinJson: true });
    expect(await new HttpRepository(BASE).getTrazaEvaluacion('MZ-1-001')).toBeNull();
  });

  it('getTrazaEvaluacion falla en 404 (sector inexistente)', async () => {
    stubFetch({ status: 404, sinJson: true });
    await expect(new HttpRepository(BASE).getTrazaEvaluacion('NOPE')).rejects.toThrow(/404/);
  });
});
