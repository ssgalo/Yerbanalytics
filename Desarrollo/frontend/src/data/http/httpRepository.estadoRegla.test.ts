import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { EstadoReglaError } from '@/data/estadoReglaError';
import type { ReglaCatalogo } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const regla: ReglaCatalogo = {
  id: 'SustratoSaturadoRule',
  label: 'Sustrato saturado',
  etiquetaFabrica: 'Sustrato saturado',
  codigo: 'R-04',
  rama: 'RIEGO',
  objetivo: 'IRRIGATION',
  efecto: 'PROTECTS',
  prioridad: 4,
  parametros: ['riego.saturacion-bloqueo'],
  activa: false,
  activaPorDefecto: true,
  proteccion: 'Se riega con el sustrato saturado.',
  estado: { motivo: 'sonda en recalibración', updatedBy: 'Ana', updatedTs: 1759514400000 },
  eliminada: false,
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

describe('HttpRepository · estado de las reglas', () => {
  it('setEstadoRegla hace PUT /rules/{name}/estado con el cuerpo y devuelve la pieza actualizada', async () => {
    const fetchFn = stubFetch(200, regla);

    const res = await new HttpRepository(BASE).setEstadoRegla('SustratoSaturadoRule', {
      activa: false,
      motivo: 'sonda en recalibración',
      confirmaProteccion: true,
    });

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/rules/SustratoSaturadoRule/estado`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({
      activa: false,
      motivo: 'sonda en recalibración',
      confirmaProteccion: true,
    });
    expect(res).toEqual(regla);
  });

  it('MOTIVO_REQUERIDO se convierte en EstadoReglaError con su código y mensaje', async () => {
    stubFetch(400, {
      error: 'MOTIVO_REQUERIDO',
      mensaje: 'Para apagar una protección hay que decir el motivo.',
    });

    const promesa = new HttpRepository(BASE).setEstadoRegla('SustratoSaturadoRule', {
      activa: false,
    });

    await expect(promesa).rejects.toBeInstanceOf(EstadoReglaError);
    await expect(promesa).rejects.toMatchObject({
      codigo: 'MOTIVO_REQUERIDO',
      message: 'Para apagar una protección hay que decir el motivo.',
      consecuencia: null,
    });
  });

  it('CONFIRMACION_REQUERIDA trae la consecuencia de apagar la protección', async () => {
    stubFetch(400, {
      error: 'CONFIRMACION_REQUERIDA',
      mensaje: 'Confirmá que querés apagarla.',
      consecuencia: 'Se riega con el sustrato saturado.',
    });

    await expect(
      new HttpRepository(BASE).setEstadoRegla('SustratoSaturadoRule', {
        activa: false,
        motivo: 'x',
      }),
    ).rejects.toMatchObject({
      codigo: 'CONFIRMACION_REQUERIDA',
      consecuencia: 'Se riega con el sustrato saturado.',
    });
  });

  it('un 404 o un 403 fallan con un Error común (no con EstadoReglaError); el 403 es el mensaje de permiso de apiFetch', async () => {
    stubFetch(404, { error: 'La regla Foo no existe.' });
    const promesa = new HttpRepository(BASE).setEstadoRegla('Foo', { activa: true });
    await expect(promesa).rejects.toThrow('La regla Foo no existe.');
    await expect(promesa).rejects.not.toBeInstanceOf(EstadoReglaError);

    stubFetch(403);
    await expect(new HttpRepository(BASE).setEstadoRegla('Foo', { activa: true })).rejects.toThrow(
      /permiso/,
    );
  });
});

describe('HttpRepository · nombre, eliminar y restaurar', () => {
  const llamada = (fn: ReturnType<typeof stubFetch>) => fn.mock.calls[0] as unknown as [string, RequestInit];

  it('renombrarRegla hace PUT /rules/{name}/etiqueta con { etiqueta } (null restablece)', async () => {
    const fn = stubFetch(200, { ...regla, label: 'Sustrato encharcado' });

    const res = await new HttpRepository(BASE).renombrarRegla('SustratoSaturadoRule', 'Sustrato encharcado');

    const [url, init] = llamada(fn);
    expect(url).toBe(`${BASE}/rules/SustratoSaturadoRule/etiqueta`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({ etiqueta: 'Sustrato encharcado' });
    expect(res.label).toBe('Sustrato encharcado');

    const fn2 = stubFetch(200, regla);
    await new HttpRepository(BASE).renombrarRegla('SustratoSaturadoRule', null);
    expect(JSON.parse(llamada(fn2)[1].body as string)).toEqual({ etiqueta: null });
  });

  it('eliminarRegla hace DELETE /rules/{name} y restaurarRegla POST /rules/{name}/restaurar', async () => {
    const fn = stubFetch(200, { ...regla, eliminada: true });
    expect((await new HttpRepository(BASE).eliminarRegla('PosponerPorLluviaRule')).eliminada).toBe(true);
    expect(llamada(fn)[0]).toBe(`${BASE}/rules/PosponerPorLluviaRule`);
    expect(llamada(fn)[1].method).toBe('DELETE');

    const fn2 = stubFetch(200, regla);
    await new HttpRepository(BASE).restaurarRegla('PosponerPorLluviaRule');
    expect(llamada(fn2)[0]).toBe(`${BASE}/rules/PosponerPorLluviaRule/restaurar`);
    expect(llamada(fn2)[1].method).toBe('POST');
  });

  it('un 409 (eliminar una protección) falla con el mensaje del backend', async () => {
    stubFetch(409, { error: 'PROTECCION', mensaje: 'Una protección no se elimina.' });
    await expect(new HttpRepository(BASE).eliminarRegla('SustratoSaturadoRule')).rejects.toThrow(
      'Una protección no se elimina.',
    );
  });
});
