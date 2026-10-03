// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { DemoExpoProvider, useDemoExpo } from './DemoExpoContext';
import * as data from '@/data';
import type { DataRepository } from '@/data';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function repoCon(parcial: Partial<DataRepository>) {
  vi.spyOn(data, 'getRepository').mockReturnValue(parcial as DataRepository);
}
const wrapper = ({ children }: { children: ReactNode }) => <DemoExpoProvider>{children}</DemoExpoProvider>;

describe('DemoExpoContext', () => {
  it('lee la visibilidad al montar', async () => {
    repoCon({ getDemoExpo: vi.fn().mockResolvedValue(true) });

    const { result } = renderHook(() => useDemoExpo(), { wrapper });

    expect(result.current.cargando).toBe(true);
    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.visible).toBe(true);
  });

  it('si la lectura falla queda oculto, sin romper', async () => {
    repoCon({ getDemoExpo: vi.fn().mockRejectedValue(new Error('backend caído')) });

    const { result } = renderHook(() => useDemoExpo(), { wrapper });

    await waitFor(() => expect(result.current.cargando).toBe(false));
    expect(result.current.visible).toBe(false);
  });

  it('cambiar guarda y actualiza con lo que devuelve el repositorio', async () => {
    const set = vi.fn().mockResolvedValue(true);
    repoCon({ getDemoExpo: vi.fn().mockResolvedValue(false), setDemoExpo: set });
    const { result } = renderHook(() => useDemoExpo(), { wrapper });
    await waitFor(() => expect(result.current.cargando).toBe(false));

    await act(async () => result.current.cambiar(true));

    expect(set).toHaveBeenCalledWith(true);
    expect(result.current.visible).toBe(true);
  });

  it('si guardar falla, rechaza y conserva el valor anterior', async () => {
    repoCon({
      getDemoExpo: vi.fn().mockResolvedValue(false),
      setDemoExpo: vi.fn().mockRejectedValue(new Error('no se pudo')),
    });
    const { result } = renderHook(() => useDemoExpo(), { wrapper });
    await waitFor(() => expect(result.current.cargando).toBe(false));

    await act(async () => {
      await expect(result.current.cambiar(true)).rejects.toThrow('no se pudo');
    });

    expect(result.current.visible).toBe(false);
  });

  it('fuera del provider lanza', () => {
    expect(() => renderHook(() => useDemoExpo())).toThrow(/DemoExpoProvider/);
  });
});
