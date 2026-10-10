// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';

const demo = vi.hoisted(() => ({ visible: false }));

vi.mock('@/hooks/NurseryContext', () => ({
  useNurseryDataOpcional: () => ({ stats: { diagCount: 3 } }),
}));
vi.mock('@/hooks/DemoExpoContext', () => ({
  useDemoExpo: () => ({ visible: demo.visible, cargando: false, cambiar: vi.fn() }),
}));

afterEach(cleanup);

const montar = (rol: Rol = 'ADMINISTRADOR', permisos?: Permiso[]) =>
  render(
    <MemoryRouter>
      <ConPermisos rol={rol} permisos={permisos}>
        <Sidebar />
      </ConPermisos>
    </MemoryRouter>,
  );

const enlacesDe = (grupo: string) =>
  within(screen.getByRole('navigation', { name: grupo }))
    .getAllByRole('link')
    .map((a) => a.textContent?.replace(/\d+$/, '').trim());

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

describe('Sidebar · permisos (7.3)', () => {
  it('el Operario con la matriz por defecto ve en Gestión sólo Historial', () => {
    demo.visible = false;
    montar('OPERARIO');

    expect(enlacesDe('Gestión')).toEqual(['Historial']);
    expect(screen.queryByRole('link', { name: /Usuarios/ })).toBeNull();
  });

  it('quien tiene usuarios.gestionar ve "Usuarios" en Gestión', () => {
    demo.visible = false;
    montar('ADMINISTRADOR');

    expect(enlacesDe('Gestión')).toEqual([
      'Historial',
      'Configuración',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Usuarios',
    ]);
    expect(screen.getByRole('link', { name: /Usuarios/ }).getAttribute('href')).toBe('/usuarios');
  });

  it('el Ingeniero Agrónomo no ve Usuarios', () => {
    demo.visible = false;
    montar('INGENIERO_AGRONOMO');

    expect(enlacesDe('Gestión')).not.toContain('Usuarios');
  });

  it('un grupo sin ítems visibles no se muestra', () => {
    demo.visible = true;
    montar('OPERARIO', ['historial.ver']);

    expect(screen.queryByText('Principal')).toBeNull();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).toBeNull();
    expect(enlacesDe('Gestión')).toEqual(['Historial']);
  });

  it('Demo Expo encendida no aparece sin pasadas.ver', () => {
    demo.visible = true;
    montar('OPERARIO', ['vivero.ver', 'diagnosticos.ver', 'historial.ver']);

    expect(screen.queryByRole('link', { name: /Demo Expo/ })).toBeNull();
  });
});
