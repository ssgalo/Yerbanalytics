// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
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

const montar = (rol: Rol = 'ADMINISTRADOR', permisos?: Permiso[], ruta = '/') =>
  render(
    <MemoryRouter initialEntries={[ruta]}>
      <ConPermisos rol={rol} permisos={permisos}>
        <Sidebar />
      </ConPermisos>
    </MemoryRouter>,
  );

/** Enlaces de una sección del menú; el grupo Configuración se incluye si está desplegado. */
const enlacesDe = (grupo: string, rol: 'navigation' | 'group' = 'navigation') =>
  within(screen.getByRole(rol, { name: grupo }))
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

  it('quien tiene usuarios.gestionar ve "Usuarios" dentro de Configuración', () => {
    demo.visible = false;
    montar('ADMINISTRADOR');
    fireEvent.click(grupoConfiguracion());

    expect(enlacesDe('Gestión')).toEqual([
      'Historial',
      'Umbrales',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Usuarios',
      'Preferencias',
    ]);
    expect(screen.getByRole('link', { name: /Usuarios/ }).getAttribute('href')).toBe('/usuarios');
  });

  it('el Ingeniero Agrónomo no ve Usuarios', () => {
    demo.visible = false;
    montar('INGENIERO_AGRONOMO');
    fireEvent.click(grupoConfiguracion());

    expect(enlacesDe('Gestión')).not.toContain('Usuarios');
  });

  it('sin ninguna vista de Configuración el grupo no se muestra', () => {
    demo.visible = false;
    montar('OPERARIO');

    expect(screen.queryByRole('button', { name: 'Configuración' })).toBeNull();
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

const grupoConfiguracion = () => screen.getByRole('button', { name: 'Configuración' });

describe('Sidebar · grupo Configuración', () => {
  it('arranca colapsado y un clic lo despliega; otro lo colapsa', () => {
    demo.visible = false;
    montar('ADMINISTRADOR');

    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('link', { name: /Umbrales/ })).toBeNull();

    fireEvent.click(grupoConfiguracion());
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
    expect(enlacesDe('Configuración', 'group')).toEqual([
      'Umbrales',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Usuarios',
      'Preferencias',
    ]);
    expect(screen.getByRole('link', { name: /Umbrales/ }).getAttribute('href')).toBe('/configuracion');
    expect(screen.getByRole('link', { name: /Preferencias/ }).getAttribute('href')).toBe('/preferencias');

    fireEvent.click(grupoConfiguracion());
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('link', { name: /Umbrales/ })).toBeNull();
  });

  it('se abre solo si la vista actual es una de las suyas', () => {
    demo.visible = false;
    montar('ADMINISTRADOR', undefined, '/hardware');

    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByRole('link', { name: /Hardware/ }).getAttribute('aria-current')).toBe('page');
  });
});
