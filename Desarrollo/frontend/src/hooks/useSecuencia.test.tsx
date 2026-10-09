// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { useSecuencia } from './useSecuencia';
import * as data from '@/data';
import { SecuenciaRechazadaError, type DataRepository } from '@/data';
import type { EstadoPasada, Secuencia } from '@/types/domain';

beforeEach(() => vi.useFakeTimers({ now: 1_000_000_000 }));
afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

const secuencia = (estado: EstadoPasada, id = 's'): Secuencia => ({
  id,
  tipo: 'RIEGO',
  estado,
  zonaId: 'MZ-1',
  sectorId: 'MZ-1-001',
  parametros: { duracionSeg: 10, esperaSeg: null },
  iniciadaEn: 1,
  finalizadaEn: estado === 'EN_CURSO' ? null : 2,
  cancelacionSolicitada: false,
  error: null,
  lectura: null,
  pasos: [],
});

function repoCon(parcial: Partial<DataRepository>) {
  vi.spyOn(data, 'getRepository').mockReturnValue(parcial as DataRepository);
}

/** Avanza de a 1 s con un `act` por paso: React aplica los renders (y reprograma el timer) entre pasos. */
const avanzar = async (ms: number) => {
  if (ms === 0) return act(async () => vi.advanceTimersByTimeAsync(0));
  for (let t = 0; t < ms; t += 1000) {
    await act(async () => vi.advanceTimersByTimeAsync(Math.min(1000, ms - t)));
  }
};
const montar = async () => {
  const r = renderHook(() => useSecuencia());
  await avanzar(0);
  return r;
};

describe('useSecuencia', () => {
  it('al montar pide la secuencia actual una vez', async () => {
    const get = vi.fn().mockResolvedValue(null);
    repoCon({ getSecuenciaActual: get });

    const { result } = renderHook(() => useSecuencia());
    expect(result.current.cargando).toBe(true);
    await avanzar(0);

    expect(get).toHaveBeenCalledTimes(1);
    expect(result.current.cargando).toBe(false);
    expect(result.current.secuencia).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('sin secuencia en curso no vuelve a pedir', async () => {
    const get = vi.fn().mockResolvedValue(secuencia('COMPLETADA'));
    repoCon({ getSecuenciaActual: get });
    await montar();

    await avanzar(10_000);

    expect(get).toHaveBeenCalledTimes(1);
  });

  it('mientras está en curso consulta cada 1 s', async () => {
    const get = vi.fn().mockResolvedValue(secuencia('EN_CURSO'));
    repoCon({ getSecuenciaActual: get });
    await montar();

    await avanzar(3_000);

    expect(get).toHaveBeenCalledTimes(4);
  });

  it('se detiene cuando la secuencia termina', async () => {
    const get = vi
      .fn()
      .mockResolvedValueOnce(secuencia('EN_CURSO'))
      .mockResolvedValueOnce(secuencia('EN_CURSO'))
      .mockResolvedValue(secuencia('COMPLETADA'));
    const { result } = await (async () => {
      repoCon({ getSecuenciaActual: get });
      return montar();
    })();

    await avanzar(10_000);

    expect(get).toHaveBeenCalledTimes(3);
    expect(result.current.secuencia?.estado).toBe('COMPLETADA');
  });

  it('limpia el timer al desmontar', async () => {
    const get = vi.fn().mockResolvedValue(secuencia('EN_CURSO'));
    repoCon({ getSecuenciaActual: get });
    const { unmount } = await montar();
    await avanzar(1_000);
    const antes = get.mock.calls.length;

    unmount();
    await avanzar(10_000);

    expect(get.mock.calls.length).toBe(antes);
  });

  it('iniciar manda tipo y parámetros, muestra la secuencia devuelta y arranca el polling', async () => {
    const get = vi.fn().mockResolvedValue(null);
    const iniciar = vi.fn().mockResolvedValue(secuencia('EN_CURSO', 'nueva'));
    repoCon({ getSecuenciaActual: get, iniciarSecuencia: iniciar });
    const { result } = await montar();
    get.mockResolvedValue(secuencia('EN_CURSO', 'nueva'));

    await act(async () => result.current.iniciar('RIEGO', { duracionSeg: 15 }));

    expect(iniciar).toHaveBeenCalledWith('RIEGO', { duracionSeg: 15 });
    expect(result.current.secuencia?.id).toBe('nueva');
    expect(result.current.iniciando).toBe(false);
    await avanzar(2_000);
    expect(get.mock.calls.length).toBeGreaterThanOrEqual(3);
  });

  it('un rechazo al iniciar muestra el mensaje del backend y no cambia la secuencia mostrada', async () => {
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(secuencia('COMPLETADA', 'previa')),
      iniciarSecuencia: vi
        .fn()
        .mockRejectedValue(new SecuenciaRechazadaError('Hay una pasada del riel en curso.')),
    });
    const { result } = await montar();

    await act(async () => result.current.iniciar('RIEGO'));

    expect(result.current.error).toBe('Hay una pasada del riel en curso.');
    expect(result.current.secuencia?.id).toBe('previa');
  });

  it('otro error al iniciar dice que no se pudo contactar al backend', async () => {
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(null),
      iniciarSecuencia: vi.fn().mockRejectedValue(new Error('Failed to fetch')),
    });
    const { result } = await montar();

    await act(async () => result.current.iniciar('LECTURA'));

    expect(result.current.error).toBe('No se pudo contactar al backend');
  });

  it('el error de un intento se limpia al volver a iniciar', async () => {
    const iniciar = vi
      .fn()
      .mockRejectedValueOnce(new SecuenciaRechazadaError('Ya hay una secuencia en curso.'))
      .mockResolvedValue(secuencia('EN_CURSO'));
    repoCon({ getSecuenciaActual: vi.fn().mockResolvedValue(null), iniciarSecuencia: iniciar });
    const { result } = await montar();

    await act(async () => result.current.iniciar('RIEGO'));
    expect(result.current.error).toBe('Ya hay una secuencia en curso.');
    await act(async () => result.current.iniciar('RIEGO'));

    expect(result.current.error).toBeNull();
  });

  it('cancelar muestra la secuencia devuelta', async () => {
    const cancelada = { ...secuencia('EN_CURSO'), cancelacionSolicitada: true };
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(secuencia('EN_CURSO')),
      cancelarSecuencia: vi.fn().mockResolvedValue(cancelada),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());

    expect(result.current.secuencia?.cancelacionSolicitada).toBe(true);
  });

  it('un rechazo al cancelar muestra el mensaje del backend', async () => {
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(secuencia('EN_CURSO')),
      cancelarSecuencia: vi
        .fn()
        .mockRejectedValue(new SecuenciaRechazadaError('No hay una secuencia en curso.')),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());

    expect(result.current.error).toBe('No hay una secuencia en curso.');
  });

  it('si falla la consulta inicial dice que no se pudo contactar al backend', async () => {
    repoCon({ getSecuenciaActual: vi.fn().mockRejectedValue(new Error('boom')) });

    const { result } = await montar();

    expect(result.current.error).toBe('No se pudo contactar al backend');
    expect(result.current.cargando).toBe(false);
  });

  it('descartarError limpia el error de un intento', async () => {
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(null),
      iniciarSecuencia: vi.fn().mockRejectedValue(new SecuenciaRechazadaError('Hay una pasada del riel en curso.')),
    });
    const { result } = await montar();
    await act(async () => result.current.iniciar('RIEGO'));
    expect(result.current.error).toBe('Hay una pasada del riel en curso.');

    act(() => result.current.descartarError());

    expect(result.current.error).toBeNull();
  });

  it('cancelar rechazada porque la secuencia ya terminó: re-consulta y no muestra el error', async () => {
    const get = vi
      .fn()
      .mockResolvedValueOnce(secuencia('EN_CURSO'))
      .mockResolvedValue(secuencia('COMPLETADA'));
    repoCon({
      getSecuenciaActual: get,
      cancelarSecuencia: vi.fn().mockRejectedValue(new SecuenciaRechazadaError('No hay una secuencia en curso.')),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());
    await avanzar(0);

    expect(get).toHaveBeenCalledTimes(2);
    expect(result.current.secuencia?.estado).toBe('COMPLETADA');
    expect(result.current.error).toBeNull();
  });

  it('cancelar rechazada con la secuencia todavía en curso sí muestra el error', async () => {
    repoCon({
      getSecuenciaActual: vi.fn().mockResolvedValue(secuencia('EN_CURSO')),
      cancelarSecuencia: vi.fn().mockRejectedValue(new SecuenciaRechazadaError('La secuencia ya se está cancelando.')),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());
    await avanzar(0);

    expect(result.current.error).toBe('La secuencia ya se está cancelando.');
  });
});
