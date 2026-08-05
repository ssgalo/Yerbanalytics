import { describe, expect, it } from 'vitest';
import { MockRepository } from './mockRepository';

/**
 * El mock existe para desarrollar el panel sin backend, pero sólo sirve si se comporta como
 * el backend: mismas validaciones, mismos estados, mismos campos. Si divergen, las
 * diferencias se descubren recién contra el backend real, que es el peor momento.
 */
describe('captura en el repositorio mock', () => {
  const repo = () => new MockRepository(20260613);

  async function primerSector(r: MockRepository) {
    const nursery = await r.getNursery();
    return nursery.sectors[0].id;
  }

  it('emite la orden en PENDIENTE y la resuelve con el paso del tiempo', async () => {
    const r = repo();
    const sectorId = await primerSector(r);

    const orden = await r.emitirOrdenCaptura({ sectorId, posicionRiel: 1200 });
    expect(orden.estado).toBe('PENDIENTE');
    expect(orden.capturaId).toBeNull();
    expect(orden.intentos).toBe(1);

    // El mock avanza por tiempo transcurrido, no por llamadas.
    await new Promise((res) => setTimeout(res, 1700));
    const resuelta = await r.getOrdenCaptura(orden.ordenId);

    expect(resuelta.estado).toBe('RECIBIDA');
    expect(resuelta.capturaId).toBeTruthy();
    expect(resuelta.imagenUrl).toContain('data:image/svg+xml');
  });

  it('rechaza una orden sobre un sector inexistente', async () => {
    const r = repo();

    await expect(r.emitirOrdenCaptura({ sectorId: 'MZ-99-999', posicionRiel: 1 })).rejects.toThrow(
      /no existe/i,
    );
  });

  it('no admite un diagnóstico sin captura', async () => {
    const r = repo();

    await expect(
      r.crearDiagnostico({ capturaId: '', estado: 'Clorosis', conf: 90, sev: 'Alta' }),
    ).rejects.toThrow(/captura es obligatoria/i);
  });

  it('no admite una captura inexistente', async () => {
    const r = repo();

    await expect(
      r.crearDiagnostico({ capturaId: 'CAP-999999', estado: 'Clorosis', conf: 90, sev: 'Alta' }),
    ).rejects.toThrow(/no existe/i);
  });

  it('valida el rango de confianza y la taxonomía, igual que el backend', async () => {
    const r = repo();
    const sectorId = await primerSector(r);
    const orden = await r.emitirOrdenCaptura({ sectorId, posicionRiel: 10 });
    await new Promise((res) => setTimeout(res, 1700));
    const capturaId = (await r.getOrdenCaptura(orden.ordenId)).capturaId!;

    await expect(
      r.crearDiagnostico({ capturaId, estado: 'Clorosis', conf: 140, sev: 'Alta' }),
    ).rejects.toThrow(/entre 0 y 100/i);

    await expect(
      r.crearDiagnostico({ capturaId, estado: 'Se ve feo', conf: 90, sev: 'Alta' }),
    ).rejects.toThrow(/taxonomía/i);
  });

  it('el diagnóstico cargado aparece en el snapshot con su imagen', async () => {
    const r = repo();
    const sectorId = await primerSector(r);
    const orden = await r.emitirOrdenCaptura({ sectorId, posicionRiel: 900 });
    await new Promise((res) => setTimeout(res, 1700));
    const capturaId = (await r.getOrdenCaptura(orden.ordenId)).capturaId!;

    const antes = (await r.getNursery()).diagnoses.length;
    const creado = await r.crearDiagnostico({
      capturaId,
      estado: 'Clorosis',
      conf: 92,
      sev: 'Alta',
    });

    const nursery = await r.getNursery();
    const card = nursery.diagnoses.find((d) => d.id === creado.id);

    expect(nursery.diagnoses.length).toBe(antes + 1);
    expect(card).toBeDefined();
    expect(card?.imagenUrl).toBe(creado.imagenUrl);
    expect(card?.conf).toBe(92);
    // Aparece primero: es el más reciente y el único con fotografía.
    expect(nursery.diagnoses[0].id).toBe(creado.id);
    // Y el índice por id lo resuelve, sin colisionar con las tarjetas sintéticas DG-###.
    expect(nursery.diagById[creado.id]).toBeDefined();
    expect(nursery.stats.diagCount).toBe(nursery.diagnoses.length);
  });

  it('marca no concluyente por debajo del umbral, igual que el backend', async () => {
    const r = repo();
    const sectorId = await primerSector(r);
    const orden = await r.emitirOrdenCaptura({ sectorId, posicionRiel: 50 });
    await new Promise((res) => setTimeout(res, 1700));
    const capturaId = (await r.getOrdenCaptura(orden.ordenId)).capturaId!;

    const bajo = await r.crearDiagnostico({ capturaId, estado: 'Daño biótico', conf: 60, sev: 'Baja' });

    expect(bajo.concluyente).toBe(false);
  });

  it('el código de vinculación no trae caracteres ambiguos', async () => {
    const r = repo();

    for (let i = 0; i < 20; i++) {
      const { codigo, expiraEn } = await r.generarCodigoVinculacion();
      expect(codigo).toMatch(/^[2-9A-Z]{4}-[2-9A-Z]{4}$/);
      expect(codigo).not.toMatch(/[01OI]/);
      expect(expiraEn).toBeGreaterThan(Date.now());
    }
  });
});
