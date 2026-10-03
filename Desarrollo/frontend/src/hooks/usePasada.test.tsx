// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { usePasada } from './usePasada';
import * as data from '@/data';
import { PasadaRechazadaError, type DataRepository } from '@/data';
import type { EstadoPasada, Pasada, PasoPasada } from '@/types/domain';

beforeEach(() => vi.useFakeTimers({ now: 1_000_000_000 }));
afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

const captura = (n: number, conDiag: boolean): PasoPasada => ({
  n,
  tipo: 'CAPTURAR',
  posicion: 1,
  sectorId: 'MZ-1-001',
  estado: 'OK',
  codigoError: null,
  detalle: null,
  commandId: null,
  ordenId: 'o',
  estadoOrden: 'RECIBIDA',
  capturaId: 'CAP-1',
  imagenUrl: '/x.jpg',
  diagnostico: conDiag ? { estado: 'Clorosis', conf: 80, sev: 'Media', creadoEn: 1 } : null,
  iniciadoEn: 1,
  terminadoEn: 2,
});

const pasada = (
  estado: EstadoPasada,
  opts: { diag?: boolean; finalizadaEn?: number | null; id?: string } = {},
): Pasada => ({
  id: opts.id ?? 'p',
  estado,
  iniciadaEn: 1,
  finalizadaEn: estado === 'EN_CURSO' ? null : (opts.finalizadaEn ?? Date.now()),
  cancelacionSolicitada: false,
  error: null,
  pasos: [captura(2, opts.diag ?? false)],
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
  const r = renderHook(() => usePasada());
  await avanzar(0);
  return r;
};

describe('usePasada', () => {
  it('al montar pide la pasada actual una vez', async () => {
    const get = vi.fn().mockResolvedValue(null);
    repoCon({ getPasadaActual: get });

    const { result } = renderHook(() => usePasada());
    expect(result.current.cargando).toBe(true);
    await avanzar(0);

    expect(get).toHaveBeenCalledTimes(1);
    expect(result.current.cargando).toBe(false);
    expect(result.current.pasada).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('sin pasada en curso ni diagnósticos pendientes no vuelve a pedir', async () => {
    const get = vi.fn().mockResolvedValue(null);
    repoCon({ getPasadaActual: get });
    await montar();

    await avanzar(10_000);

    expect(get).toHaveBeenCalledTimes(1);
  });

  it('mientras está en curso consulta cada 1 s', async () => {
    const get = vi.fn().mockResolvedValue(pasada('EN_CURSO'));
    repoCon({ getPasadaActual: get });
    await montar();

    await avanzar(3_000);

    expect(get).toHaveBeenCalledTimes(4);
  });

  it('terminada pero esperando un diagnóstico consulta cada 3 s', async () => {
    const get = vi.fn().mockResolvedValue(pasada('COMPLETADA'));
    repoCon({ getPasadaActual: get });
    await montar();

    await avanzar(6_000);

    expect(get).toHaveBeenCalledTimes(3);
  });

  it('se detiene cuando llegaron todos los diagnósticos', async () => {
    const get = vi
      .fn()
      .mockResolvedValueOnce(pasada('COMPLETADA'))
      .mockResolvedValue(pasada('COMPLETADA', { diag: true }));
    repoCon({ getPasadaActual: get });
    const { result } = await montar();

    await avanzar(30_000);

    expect(get).toHaveBeenCalledTimes(2);
    expect(result.current.pasada?.pasos[0].diagnostico).not.toBeNull();
  });

  it('se detiene a los 5 min de terminada aunque falte el diagnóstico', async () => {
    const get = vi.fn().mockResolvedValue(pasada('COMPLETADA'));
    repoCon({ getPasadaActual: get });
    await montar();

    await avanzar(5 * 60_000 + 3_000);
    const llamadas = get.mock.calls.length;
    await avanzar(30_000);

    expect(get.mock.calls.length).toBe(llamadas);
    expect(llamadas).toBeLessThan(110);
  });

  it('no consulta si la pasada terminó hace más de 5 min', async () => {
    const vieja = pasada('COMPLETADA', { finalizadaEn: Date.now() - 6 * 60_000 });
    const get = vi.fn().mockResolvedValue(vieja);
    repoCon({ getPasadaActual: get });
    await montar();

    await avanzar(10_000);

    expect(get).toHaveBeenCalledTimes(1);
  });

  it('limpia el timer al desmontar', async () => {
    const get = vi.fn().mockResolvedValue(pasada('EN_CURSO'));
    repoCon({ getPasadaActual: get });
    const { unmount } = await montar();
    await avanzar(1_000);
    const antes = get.mock.calls.length;

    unmount();
    await avanzar(10_000);

    expect(get.mock.calls.length).toBe(antes);
  });

  it('iniciar muestra la pasada devuelta y arranca el polling', async () => {
    const get = vi.fn().mockResolvedValue(null);
    const iniciar = vi.fn().mockResolvedValue(pasada('EN_CURSO', { id: 'nueva' }));
    repoCon({ getPasadaActual: get, iniciarPasada: iniciar });
    const { result } = await montar();
    get.mockResolvedValue(pasada('EN_CURSO', { id: 'nueva' }));

    await act(async () => result.current.iniciar());

    expect(result.current.pasada?.id).toBe('nueva');
    expect(result.current.iniciando).toBe(false);
    await avanzar(2_000);
    expect(get.mock.calls.length).toBeGreaterThanOrEqual(3);
  });

  it('un 409 al iniciar muestra el mensaje del backend y no cambia la pasada mostrada', async () => {
    const previa = pasada('COMPLETADA', { diag: true, id: 'previa' });
    repoCon({
      getPasadaActual: vi.fn().mockResolvedValue(previa),
      iniciarPasada: vi.fn().mockRejectedValue(new PasadaRechazadaError('No hay ningún dispositivo de captura conectado.')),
    });
    const { result } = await montar();

    await act(async () => result.current.iniciar());

    expect(result.current.error).toBe('No hay ningún dispositivo de captura conectado.');
    expect(result.current.pasada?.id).toBe('previa');
  });

  it('otro error al iniciar dice que no se pudo contactar al backend', async () => {
    repoCon({
      getPasadaActual: vi.fn().mockResolvedValue(null),
      iniciarPasada: vi.fn().mockRejectedValue(new Error('Failed to fetch')),
    });
    const { result } = await montar();

    await act(async () => result.current.iniciar());

    expect(result.current.error).toBe('No se pudo contactar al backend');
  });

  it('el error de un intento se limpia al volver a iniciar', async () => {
    const iniciar = vi
      .fn()
      .mockRejectedValueOnce(new PasadaRechazadaError('Ya hay una pasada en curso.'))
      .mockResolvedValue(pasada('EN_CURSO'));
    repoCon({ getPasadaActual: vi.fn().mockResolvedValue(null), iniciarPasada: iniciar });
    const { result } = await montar();
    await act(async () => result.current.iniciar());
    expect(result.current.error).not.toBeNull();

    await act(async () => result.current.iniciar());

    expect(result.current.error).toBeNull();
  });

  it('cancelar actualiza la pasada con la respuesta', async () => {
    const cancelada = { ...pasada('EN_CURSO'), cancelacionSolicitada: true };
    repoCon({
      getPasadaActual: vi.fn().mockResolvedValue(pasada('EN_CURSO')),
      cancelarPasada: vi.fn().mockResolvedValue(cancelada),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());

    expect(result.current.pasada?.cancelacionSolicitada).toBe(true);
  });

  it('cancelar rechazada muestra el mensaje del backend', async () => {
    repoCon({
      getPasadaActual: vi.fn().mockResolvedValue(pasada('EN_CURSO')),
      cancelarPasada: vi.fn().mockRejectedValue(new PasadaRechazadaError('No hay una pasada en curso.')),
    });
    const { result } = await montar();

    await act(async () => result.current.cancelar());

    expect(result.current.error).toBe('No hay una pasada en curso.');
  });

  it('si falla la consulta lo informa, y se recupera cuando vuelve', async () => {
    const get = vi
      .fn()
      .mockResolvedValueOnce(pasada('EN_CURSO'))
      .mockRejectedValueOnce(new Error('caído'))
      .mockResolvedValue(pasada('EN_CURSO'));
    repoCon({ getPasadaActual: get });
    const { result } = await montar();

    await avanzar(1_000);
    expect(result.current.error).toBe('No se pudo contactar al backend');

    await avanzar(1_000);
    expect(result.current.error).toBeNull();
  });

  it('una respuesta lenta vieja no pisa a una más nueva', async () => {
    let soltarVieja: (p: Pasada) => void = () => undefined;
    const vieja = new Promise<Pasada>((r) => (soltarVieja = r));
    const get = vi
      .fn()
      .mockReturnValueOnce(Promise.resolve(pasada('EN_CURSO', { id: 'a' })))
      .mockReturnValueOnce(vieja)
      .mockResolvedValue(pasada('EN_CURSO', { id: 'c' }));
    repoCon({ getPasadaActual: get });
    const { result } = await montar();

    await avanzar(2_000); // sale el 2.º pedido (colgado) y el 3.º (responde)
    await act(async () => soltarVieja(pasada('EN_CURSO', { id: 'b' })));

    expect(result.current.pasada?.id).toBe('c');
  });
});
