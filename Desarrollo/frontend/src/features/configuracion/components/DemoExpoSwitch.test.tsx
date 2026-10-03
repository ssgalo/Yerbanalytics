// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { DemoExpoSwitch } from './DemoExpoSwitch';
import { DemoExpoProvider } from '@/hooks/DemoExpoContext';
import * as data from '@/data';
import type { DataRepository } from '@/data';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function montar(repo: Partial<DataRepository>) {
  vi.spyOn(data, 'getRepository').mockReturnValue(repo as DataRepository);
  render(
    <DemoExpoProvider>
      <DemoExpoSwitch />
    </DemoExpoProvider>,
  );
  return screen.getByRole('switch', { name: /Mostrar Demo Expo/ }) as HTMLInputElement;
}

describe('DemoExpoSwitch', () => {
  it('refleja el valor guardado', async () => {
    const sw = montar({ getDemoExpo: vi.fn().mockResolvedValue(true) });
    await waitFor(() => expect(sw.checked).toBe(true));
  });

  it('guarda al instante, sin botón Guardar', async () => {
    const set = vi.fn().mockResolvedValue(true);
    const sw = montar({ getDemoExpo: vi.fn().mockResolvedValue(false), setDemoExpo: set });
    await waitFor(() => expect(sw.disabled).toBe(false));

    fireEvent.click(sw);

    expect(set).toHaveBeenCalledWith(true);
    await waitFor(() => expect(sw.checked).toBe(true));
    expect(screen.queryByRole('button', { name: /Guardar/ })).toBeNull();
  });

  it('queda deshabilitado mientras guarda', async () => {
    let soltar: (v: boolean) => void = () => undefined;
    const set = vi.fn().mockReturnValue(new Promise<boolean>((r) => (soltar = r)));
    const sw = montar({ getDemoExpo: vi.fn().mockResolvedValue(false), setDemoExpo: set });
    await waitFor(() => expect(sw.disabled).toBe(false));

    fireEvent.click(sw);
    await waitFor(() => expect(sw.disabled).toBe(true));

    soltar(true);
    await waitFor(() => expect(sw.disabled).toBe(false));
  });

  it('si falla, vuelve al valor previo y muestra el error', async () => {
    const sw = montar({
      getDemoExpo: vi.fn().mockResolvedValue(false),
      setDemoExpo: vi.fn().mockRejectedValue(new Error('El backend no responde')),
    });
    await waitFor(() => expect(sw.disabled).toBe(false));

    fireEvent.click(sw);

    expect((await screen.findByRole('alert')).textContent).toContain('El backend no responde');
    expect(sw.checked).toBe(false);
  });
});
