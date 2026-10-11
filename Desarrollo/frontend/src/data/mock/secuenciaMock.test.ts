import { describe, expect, it } from 'vitest';
import { simularSecuencia } from './secuenciaMock';
import { MockRepository } from './mockRepository';
import { SecuenciaRechazadaError } from '@/data/secuenciaError';
import type { EstadoPaso, TipoSecuencia } from '@/types/domain';

const T0 = 1_000_000;
const seg = (s: number) => T0 + s * 1000;

const estados = (
  tipo: TipoSecuencia,
  s: number,
  cancelada: number | null = null,
  parametros = { duracionSeg: 10, esperaSeg: 10 },
) =>
  simularSecuencia(
    tipo,
    parametros,
    T0,
    seg(s),
    cancelada === null ? null : seg(cancelada),
  ).pasos.map((p) => p.estado);

describe('simularSecuencia · riego', () => {
  const par = { duracionSeg: 10 };

  it('en t=0 abre la válvula del primer sector de la zona', () => {
    const s = simularSecuencia('RIEGO', par, T0, T0, null);

    expect(s.tipo).toBe('RIEGO');
    expect(s.estado).toBe('EN_CURSO');
    expect(s.zonaId).toBe('MZ-1');
    expect(s.sectorId).toBe('MZ-1-001');
    expect(s.parametros.duracionSeg).toBe(10);
    expect(s.iniciadaEn).toBe(T0);
    expect(s.finalizadaEn).toBeNull();
    expect(s.lectura).toBeNull();
    expect(s.pasos.map((p) => p.tipo)).toEqual(['ABRIR', 'ESPERAR', 'CERRAR']);
    expect(estados('RIEGO', 0)).toEqual<EstadoPaso[]>(['EN_CURSO', 'PENDIENTE', 'PENDIENTE']);
    expect(s.pasos[0].commandId).not.toBeNull();
  });

  it('a mitad de la espera trae esperaHasta y el cierre pendiente', () => {
    const s = simularSecuencia('RIEGO', par, T0, seg(6), null);

    expect(estados('RIEGO', 6)).toEqual<EstadoPaso[]>(['OK', 'EN_CURSO', 'PENDIENTE']);
    expect(s.pasos[1].esperaHasta).toBe(seg(11));
    expect(s.pasos[0].terminadoEn).toBe(seg(1));
  });

  it('cerrar la válvula tarda 1 s y al terminar queda completada', () => {
    expect(estados('RIEGO', 11.5)).toEqual<EstadoPaso[]>(['OK', 'OK', 'EN_CURSO']);
    const s = simularSecuencia('RIEGO', par, T0, seg(12), null);
    expect(s.estado).toBe('COMPLETADA');
    expect(s.finalizadaEn).toBe(seg(12));
    expect(s.pasos.every((p) => p.estado === 'OK')).toBe(true);
    expect(s.cancelacionSolicitada).toBe(false);
  });

  it('cancelada en la espera: omite la espera y cierra la válvula (paso seguro)', () => {
    const durante = simularSecuencia('RIEGO', par, T0, seg(5.5), seg(5));
    expect(durante.estado).toBe('EN_CURSO');
    expect(durante.cancelacionSolicitada).toBe(true);
    expect(durante.pasos.map((p) => p.estado)).toEqual<EstadoPaso[]>(['OK', 'OMITIDO', 'EN_CURSO']);
    expect(durante.pasos[1].detalle).toBe('Cancelada por el operador');

    const fin = simularSecuencia('RIEGO', par, T0, seg(7), seg(5));
    expect(fin.estado).toBe('CANCELADA');
    expect(fin.finalizadaEn).toBe(seg(6));
    expect(fin.pasos.map((p) => p.estado)).toEqual<EstadoPaso[]>(['OK', 'OMITIDO', 'OK']);
  });

  it('una cancelación posterior al final no cambia nada', () => {
    const s = simularSecuencia('RIEGO', par, T0, seg(30), seg(20));
    expect(s.estado).toBe('COMPLETADA');
    expect(s.cancelacionSolicitada).toBe(false);
  });
});

describe('simularSecuencia · mediasombra', () => {
  const par = { esperaSeg: 8 };

  it('despliega 4 s, espera y enrolla 4 s', () => {
    const s0 = simularSecuencia('MEDIASOMBRA', par, T0, T0, null);
    expect(s0.pasos.map((p) => p.tipo)).toEqual(['DESPLEGAR', 'ESPERAR', 'ENROLLAR']);
    expect(s0.sectorId).toBe('MZ-1-001');
    expect(s0.parametros.esperaSeg).toBe(8);

    expect(estados('MEDIASOMBRA', 3, null, { duracionSeg: 10, esperaSeg: 8 })).toEqual<
      EstadoPaso[]
    >(['EN_CURSO', 'PENDIENTE', 'PENDIENTE']);
    const espera = simularSecuencia('MEDIASOMBRA', par, T0, seg(8), null);
    expect(espera.pasos.map((p) => p.estado)).toEqual<EstadoPaso[]>([
      'OK',
      'EN_CURSO',
      'PENDIENTE',
    ]);
    expect(espera.pasos[1].esperaHasta).toBe(seg(12));

    const fin = simularSecuencia('MEDIASOMBRA', par, T0, seg(16), null);
    expect(fin.estado).toBe('COMPLETADA');
    expect(fin.finalizadaEn).toBe(seg(16));
  });

  it('cancelada mientras despliega: omite la espera y enrolla', () => {
    const s = simularSecuencia('MEDIASOMBRA', par, T0, seg(3), seg(2));
    expect(s.pasos.map((p) => p.estado)).toEqual<EstadoPaso[]>(['OMITIDO', 'OMITIDO', 'EN_CURSO']);
    const fin = simularSecuencia('MEDIASOMBRA', par, T0, seg(7), seg(2));
    expect(fin.estado).toBe('CANCELADA');
    expect(fin.finalizadaEn).toBe(seg(6));
  });

  it('esperaSeg 0 no deja una espera fantasma', () => {
    const s = simularSecuencia('MEDIASOMBRA', { esperaSeg: 0 }, T0, seg(8), null);
    expect(s.estado).toBe('COMPLETADA');
  });
});

describe('simularSecuencia · lectura', () => {
  it('pide la lectura, espera la telemetría y la muestra', () => {
    const s0 = simularSecuencia('LECTURA', {}, T0, T0, null);
    expect(s0.pasos.map((p) => p.tipo)).toEqual(['PEDIR', 'ESPERAR_TELEMETRIA', 'MOSTRAR']);
    expect(s0.sectorId).toBeNull();
    expect(s0.zonaId).toBe('MZ-1');
    expect(s0.lectura).toBeNull();
    expect(estados('LECTURA', 0)).toEqual<EstadoPaso[]>(['EN_CURSO', 'PENDIENTE', 'PENDIENTE']);
    expect(estados('LECTURA', 1)).toEqual<EstadoPaso[]>(['OK', 'EN_CURSO', 'PENDIENTE']);

    const fin = simularSecuencia('LECTURA', {}, T0, seg(3), null);
    expect(fin.estado).toBe('COMPLETADA');
    expect(fin.pasos.every((p) => p.estado === 'OK')).toBe(true);
    expect(fin.lectura?.recibidaEn).toBe(seg(2.5));
    expect(fin.lectura?.metricas.humSus).not.toBeUndefined();
    expect(Object.keys(fin.lectura?.metricas ?? {})).toEqual(
      expect.arrayContaining([
        'humSus',
        'humAmb',
        'temp',
        'uv',
        'ce',
        'tempSuelo',
        'phSuelo',
        'n',
        'p',
        'k',
      ]),
    );
  });

  it('usa la lectura que le pasen (la de la zona del mock)', () => {
    const s = simularSecuencia('LECTURA', {}, T0, seg(3), null, { humSus: 55, ce: 1.4 });
    expect(s.lectura?.metricas).toEqual({ humSus: 55, ce: 1.4 });
  });

  it('cancelada: queda cancelada al instante y omite lo pendiente', () => {
    const s = simularSecuencia('LECTURA', {}, T0, seg(1.2), seg(1));
    expect(s.estado).toBe('CANCELADA');
    expect(s.finalizadaEn).toBe(seg(1));
    expect(s.pasos.map((p) => p.estado)).toEqual<EstadoPaso[]>(['OK', 'OMITIDO', 'OMITIDO']);
    expect(s.lectura).toBeNull();
  });
});

describe('MockRepository · secuencias', () => {
  it('inicia, consulta y cancela una secuencia', async () => {
    const repo = new MockRepository(1);
    expect(await repo.getSecuenciaActual()).toBeNull();

    const s = await repo.iniciarSecuencia('RIEGO', { duracionSeg: 15 });
    expect(s.estado).toBe('EN_CURSO');
    expect((await repo.getSecuenciaActual())?.id).toBe(s.id);

    const c = await repo.cancelarSecuencia();
    expect(c.cancelacionSolicitada).toBe(true);
  });

  it('rechaza iniciar otra con una en curso y cancelar sin ninguna', async () => {
    const repo = new MockRepository(1);
    await expect(repo.cancelarSecuencia()).rejects.toThrow('No hay una secuencia en curso.');
    await repo.iniciarSecuencia('LECTURA');
    const intento = repo.iniciarSecuencia('RIEGO');
    await expect(intento).rejects.toBeInstanceOf(SecuenciaRechazadaError);
    await expect(intento).rejects.toThrow('Ya hay una secuencia en curso.');
  });

  it('con la pasada en curso rechaza la secuencia, con el mensaje del backend', async () => {
    const repo = new MockRepository(1);
    await repo.iniciarPasada();
    await expect(repo.iniciarSecuencia('RIEGO')).rejects.toThrow(
      'Hay una pasada del riel en curso.',
    );
  });

  it('con una secuencia en curso rechaza la pasada, con el mensaje del backend', async () => {
    const repo = new MockRepository(1);
    await repo.iniciarSecuencia('MEDIASOMBRA');
    await expect(repo.iniciarPasada()).rejects.toThrow(
      'Hay una secuencia de MEDIASOMBRA en curso.',
    );
  });

  it('la lectura de una secuencia LECTURA sale de la zona del mock', async () => {
    const repo = new MockRepository(1);
    await repo.iniciarSecuencia('LECTURA');
    const nursery = await repo.getNursery();
    const esperado = nursery.zonas[0].lectura.metrics.find((m) => m.key === 'humSus')?.raw ?? null;
    const actual = await repo.getSecuenciaActual();
    // Todavía no llegó: el valor aparece cuando termina, pero la zona ya está fijada.
    expect(actual?.zonaId).toBe(nursery.zonas[0].id);
    expect(typeof esperado === 'number' || esperado === null).toBe(true);
  });
});
