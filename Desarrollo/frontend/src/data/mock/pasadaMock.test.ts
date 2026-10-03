import { describe, expect, it } from 'vitest';
import { simularPasada } from './pasadaMock';
import { capturasDemo } from './capturasDemo';
import { MockRepository } from './mockRepository';
import { PasadaRechazadaError } from '@/data/pasadaError';
import type { EstadoPaso } from '@/types/domain';

const T0 = 1_000_000;
const seg = (s: number) => T0 + s * 1000;
const estados = (s: number, cancelada: number | null = null) =>
  simularPasada(T0, seg(s), cancelada === null ? null : seg(cancelada)).pasos.map((p) => p.estado);

describe('simularPasada', () => {
  it('en t=0 el primer movimiento está en curso y el resto pendiente', () => {
    const p = simularPasada(T0, T0, null);

    expect(p.estado).toBe('EN_CURSO');
    expect(p.iniciadaEn).toBe(T0);
    expect(p.finalizadaEn).toBeNull();
    expect(p.pasos).toHaveLength(5);
    expect(p.pasos.map((x) => x.tipo)).toEqual(['MOVER', 'CAPTURAR', 'MOVER', 'CAPTURAR', 'HOME']);
    expect(p.pasos.map((x) => x.posicion)).toEqual([1, 1, 2, 2, 0]);
    expect(p.pasos.map((x) => x.sectorId)).toEqual([null, 'MZ-1-001', null, 'MZ-1-002', null]);
    expect(estados(0)).toEqual<EstadoPaso[]>(['EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE']);
    expect(p.pasos[0].iniciadoEn).toBe(T0);
    expect(p.pasos[0].commandId).not.toBeNull();
  });

  it('a los 5 s el riel ya llegó y el celular está sacando la primera foto', () => {
    const p = simularPasada(T0, seg(5), null);

    expect(estados(5)).toEqual<EstadoPaso[]>(['OK', 'EN_CURSO', 'PENDIENTE', 'PENDIENTE', 'PENDIENTE']);
    expect(p.pasos[0].terminadoEn).toBe(seg(4));
    expect(p.pasos[1].estadoOrden).toBe('ENTREGADA');
    expect(p.pasos[1].imagenUrl).toBeNull();
    expect(p.pasos[1].capturaId).toBeNull();
  });

  it('a los 9 s llegó la primera foto (con miniatura) y se mueve a la posición 2', () => {
    const p = simularPasada(T0, seg(9), null);

    expect(estados(9)).toEqual<EstadoPaso[]>(['OK', 'OK', 'EN_CURSO', 'PENDIENTE', 'PENDIENTE']);
    expect(p.pasos[1].estadoOrden).toBe('RECIBIDA');
    expect(p.pasos[1].capturaId).not.toBeNull();
    expect(p.pasos[1].imagenUrl).toBe(capturasDemo['Clorosis'][0]);
    expect(p.pasos[1].diagnostico).toBeNull();
  });

  it('a los 20 s la pasada está completada, sin diagnósticos todavía', () => {
    const p = simularPasada(T0, seg(20), null);

    expect(p.estado).toBe('COMPLETADA');
    expect(p.finalizadaEn).toBe(seg(20));
    expect(p.pasos.every((x) => x.estado === 'OK')).toBe(true);
    expect(p.pasos[3].imagenUrl).toBe(capturasDemo['Estrés solar'][0]);
    expect(p.pasos[1].diagnostico).toBeNull();
    expect(p.pasos[3].diagnostico).toBeNull();
  });

  it('5 s después de terminar aparecen los diagnósticos de las dos capturas', () => {
    const p = simularPasada(T0, seg(25), null);

    expect(p.pasos[1].diagnostico).toMatchObject({ estado: 'Clorosis' });
    expect(p.pasos[3].diagnostico).toMatchObject({ estado: 'Estrés solar' });
    expect(p.pasos[1].diagnostico?.conf).toBeGreaterThan(0);
    expect(p.pasos[1].diagnostico?.conf).toBeLessThanOrEqual(100);
    expect(p.pasos[0].diagnostico).toBeNull();
  });

  it('cancelada a mitad de un tramo: omite lo pendiente, vuelve a home y termina CANCELADA', () => {
    // a los 2 s está en el primer movimiento; cancela
    const durante = simularPasada(T0, seg(3), seg(2));
    expect(durante.estado).toBe('EN_CURSO');
    expect(durante.cancelacionSolicitada).toBe(true);
    expect(durante.pasos.map((x) => x.estado)).toEqual<EstadoPaso[]>([
      'OMITIDO',
      'OMITIDO',
      'OMITIDO',
      'OMITIDO',
      'EN_CURSO',
    ]);
    expect(durante.pasos[0].detalle).toBe('Cancelada por el operador');

    const fin = simularPasada(T0, seg(9), seg(2));
    expect(fin.estado).toBe('CANCELADA');
    expect(fin.finalizadaEn).toBe(seg(8));
    expect(fin.pasos[4].estado).toBe('OK');
  });

  it('cancelada tras sacar la primera foto conserva esa foto', () => {
    const p = simularPasada(T0, seg(30), seg(9));

    expect(p.estado).toBe('CANCELADA');
    expect(p.pasos[1].estado).toBe('OK');
    expect(p.pasos[1].imagenUrl).toBe(capturasDemo['Clorosis'][0]);
    expect(p.pasos[2].estado).toBe('OMITIDO');
    expect(p.pasos[3].estado).toBe('OMITIDO');
    expect(p.pasos[3].imagenUrl).toBeNull();
  });

  it('cancelar después de que terminó no cambia nada', () => {
    expect(simularPasada(T0, seg(30), seg(25)).estado).toBe('COMPLETADA');
  });
});

describe('MockRepository · pasada y Demo Expo', () => {
  const nuevo = () => new MockRepository(20260613);

  it('Demo Expo arranca oculto y se guarda en memoria', async () => {
    const repo = nuevo();
    expect(await repo.getDemoExpo()).toBe(false);
    expect(await repo.setDemoExpo(true)).toBe(true);
    expect(await repo.getDemoExpo()).toBe(true);
  });

  it('sin pasadas getPasadaActual devuelve null', async () => {
    expect(await nuevo().getPasadaActual()).toBeNull();
  });

  it('iniciarPasada devuelve una pasada en curso que getPasadaActual ve', async () => {
    const repo = nuevo();
    const p = await repo.iniciarPasada();
    expect(p.estado).toBe('EN_CURSO');
    expect((await repo.getPasadaActual())?.id).toBe(p.id);
  });

  it('una segunda iniciarPasada en curso rechaza con el mensaje del backend', async () => {
    const repo = nuevo();
    await repo.iniciarPasada();
    const segunda = repo.iniciarPasada();
    await expect(segunda).rejects.toBeInstanceOf(PasadaRechazadaError);
    await expect(segunda).rejects.toThrow('Ya hay una pasada en curso.');
  });

  it('cancelarPasada sin pasada en curso rechaza', async () => {
    await expect(nuevo().cancelarPasada()).rejects.toThrow('No hay una pasada en curso.');
  });

  it('cancelarPasada marca la cancelación y la pasada termina CANCELADA', async () => {
    const repo = nuevo();
    await repo.iniciarPasada();
    const c = await repo.cancelarPasada();
    expect(c.cancelacionSolicitada).toBe(true);
    expect(c.estado).toBe('EN_CURSO');
  });
});
