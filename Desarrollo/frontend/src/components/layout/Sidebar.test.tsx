// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from './Sidebar';

const demo = vi.hoisted(() => ({ visible: false }));

vi.mock('@/hooks/NurseryContext', () => ({
  useNurseryData: () => ({ stats: { diagCount: 3 } }),
}));
vi.mock('@/hooks/DemoExpoContext', () => ({
  useDemoExpo: () => ({ visible: demo.visible, cargando: false, cambiar: vi.fn() }),
}));

afterEach(cleanup);

const montar = () =>
  render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>,
  );

describe('Sidebar · Demo Expo', () => {
  it('con el interruptor encendido muestra "Demo Expo" justo después de "Diagnósticos de IA"', () => {
    demo.visible = true;
    montar();

    const links = screen.getAllByRole('link').map((a) => a.textContent ?? '');
    const iDiag = links.findIndex((t) => t.includes('Diagnósticos de IA'));
    expect(links[iDiag + 1]).toContain('Demo Expo');
    expect(screen.getByRole('link', { name: /Demo Expo/ }).getAttribute('href')).toBe('/demo-expo');
  });

  it('con el interruptor apagado no la muestra', () => {
    demo.visible = false;
    montar();

    expect(screen.queryByRole('link', { name: /Demo Expo/ })).toBeNull();
  });
});
