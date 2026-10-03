// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook, waitFor } from '@testing-library/react';
import { useTrazaEvaluacion } from './useTrazaEvaluacion';
import * as data from '@/data';
import type { DataRepository } from '@/data';
import type { TrazaEvaluacion } from '@/types/domain';

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

const traza = (sectorId: string, hash = 'h1'): TrazaEvaluacion => ({
  sectorId,
  zonaId: 'MZ-1',
  origen: 'TELEMETRIA',
  ts: '2026-06-13T12:00:00.000Z',
  parametrosHash: hash,
  reglas: [],
});

function repoCon(get: ReturnType<typeof vi.fn>) {
  vi.spyOn(data, 'getRepository').mockReturnValue({
    getTrazaEvaluacion: get,
  } as unknown as DataRepository);
  return get;
}

describe('useTrazaEvaluacion', () => {
  it('pide la traza del sector y origen elegidos', async () => {
    const get = repoCon(vi.fn().mockResolvedValue(traza('MZ-1-001')));

    const { result } = renderHook(() => useTrazaEvaluacion('MZ-1-001', 'BARRIDO', false));

    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(get).toHaveBeenCalledWith('MZ-1-001', 'BARRIDO');
    expect(result.current.traza?.sectorId).toBe('MZ-1-001');
    expect(result.current.error).toBeNull();
  });

  it('si todavía no se evaluó (null) deja la traza en null, sin error', async () => {
    repoCon(vi.fn().mockResolvedValue(null));

    const { result } = renderHook(() => useTrazaEvaluacion('MZ-1-001', 'TELEMETRIA', false));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.traza).toBeNull();
    expect(result.current.error).toBeNull();
  });

  it('expone el error del repositorio', async () => {
    repoCon(vi.fn().mockRejectedValue(new Error('Error 404: el sector NOPE no existe')));

    const { result } = renderHook(() => useTrazaEvaluacion('NOPE', 'TELEMETRIA', false));

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error?.message).toMatch(/404/);
    expect(result.current.traza).toBeNull();
  });

  it('recarga al cambiar de sector, sin mostrar la traza del anterior', async () => {
    const get = repoCon(vi.fn().mockImplementation(async (id: string) => traza(id)));
    const { result, rerender } = renderHook(
      ({ id }) => useTrazaEvaluacion(id, 'TELEMETRIA', false),
      {
        initialProps: { id: 'MZ-1-001' },
      },
    );
    await waitFor(() => expect(result.current.traza?.sectorId).toBe('MZ-1-001'));

    rerender({ id: 'MZ-2-005' });

    await waitFor(() => expect(result.current.traza?.sectorId).toBe('MZ-2-005'));
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('al cambiar de sector limpia la traza anterior de inmediato, sin esperar la respuesta', async () => {
    const pendiente = new Promise<TrazaEvaluacion>(() => undefined);
    repoCon(vi.fn().mockImplementation((id: string) => (id === 'MZ-1-001' ? Promise.resolve(traza(id)) : pendiente)));
    const { result, rerender } = renderHook(({ id }) => useTrazaEvaluacion(id, 'TELEMETRIA', false), {
      initialProps: { id: 'MZ-1-001' },
    });
    await waitFor(() => expect(result.current.traza?.sectorId).toBe('MZ-1-001'));

    rerender({ id: 'MZ-2-005' });

    expect(result.current.traza).toBeNull();
    expect(result.current.loading).toBe(true);
  });

  it('al cambiar de origen limpia la traza del origen anterior', async () => {
    const pendiente = new Promise<TrazaEvaluacion>(() => undefined);
    repoCon(vi.fn().mockImplementation((_id: string, o: string) => (o === 'TELEMETRIA' ? Promise.resolve(traza('MZ-1-001')) : pendiente)));
    const { result, rerender } = renderHook(
      ({ o }: { o: 'TELEMETRIA' | 'BARRIDO' }) => useTrazaEvaluacion('MZ-1-001', o, false),
      { initialProps: { o: 'TELEMETRIA' } },
    );
    await waitFor(() => expect(result.current.traza).not.toBeNull());

    rerender({ o: 'BARRIDO' });

    expect(result.current.traza).toBeNull();
  });

  it('al cambiar de sector limpia el error anterior: no queda pegado al sector nuevo', async () => {
    const pendiente = new Promise<TrazaEvaluacion>(() => undefined);
    repoCon(
      vi.fn().mockImplementation((id: string) => (id === 'NOPE' ? Promise.reject(new Error('Error 404')) : pendiente)),
    );
    const { result, rerender } = renderHook(({ id }) => useTrazaEvaluacion(id, 'TELEMETRIA', false), {
      initialProps: { id: 'NOPE' },
    });
    await waitFor(() => expect(result.current.error).not.toBeNull());

    rerender({ id: 'MZ-2-005' });

    expect(result.current.error).toBeNull();
  });

  it('acepta una traza sin macro-zona (zonaId null)', async () => {
    repoCon(vi.fn().mockResolvedValue({ ...traza('MZ-1-001'), zonaId: null }));
    const { result } = renderHook(() => useTrazaEvaluacion('MZ-1-001', 'TELEMETRIA', false));
    await waitFor(() => expect(result.current.traza?.zonaId).toBeNull());
  });

  it('descarta la respuesta de un sector viejo si ya se eligió otro', async () => {
    let soltarViejo: (t: TrazaEvaluacion) => void = () => undefined;
    const viejo = new Promise<TrazaEvaluacion>((res) => (soltarViejo = res));
    repoCon(
      vi
        .fn()
        .mockImplementation((id: string) =>
          id === 'MZ-1-001' ? viejo : Promise.resolve(traza(id)),
        ),
    );

    const { result, rerender } = renderHook(
      ({ id }) => useTrazaEvaluacion(id, 'TELEMETRIA', false),
      {
        initialProps: { id: 'MZ-1-001' },
      },
    );
    rerender({ id: 'MZ-2-005' });
    await waitFor(() => expect(result.current.traza?.sectorId).toBe('MZ-2-005'));

    await act(async () => soltarViejo(traza('MZ-1-001')));

    expect(result.current.traza?.sectorId).toBe('MZ-2-005');
  });

  it('refrescar vuelve a pedir la traza', async () => {
    const get = repoCon(
      vi
        .fn()
        .mockResolvedValueOnce(traza('MZ-1-001', 'a'))
        .mockResolvedValueOnce(traza('MZ-1-001', 'b')),
    );
    const { result } = renderHook(() => useTrazaEvaluacion('MZ-1-001', 'TELEMETRIA', false));
    await waitFor(() => expect(result.current.traza?.parametrosHash).toBe('a'));

    act(() => result.current.refrescar());

    await waitFor(() => expect(result.current.traza?.parametrosHash).toBe('b'));
    expect(get).toHaveBeenCalledTimes(2);
  });

  it('con auto-refresco vuelve a pedir cada 5 s, y sin él no', async () => {
    vi.useFakeTimers();
    const get = repoCon(vi.fn().mockResolvedValue(traza('MZ-1-001')));
    const { rerender } = renderHook(
      ({ auto }) => useTrazaEvaluacion('MZ-1-001', 'TELEMETRIA', auto),
      {
        initialProps: { auto: false },
      },
    );
    await act(async () => vi.advanceTimersByTimeAsync(11_000));
    expect(get).toHaveBeenCalledTimes(1);

    rerender({ auto: true });
    await act(async () => vi.advanceTimersByTimeAsync(10_500));
    expect(get.mock.calls.length).toBeGreaterThanOrEqual(3);
  });
});
