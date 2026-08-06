import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
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
