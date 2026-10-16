// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { SidebarProvider } from '@/hooks/SidebarContext';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';

vi.mock('@/hooks/NurseryContext', () => ({
  useNurseryDataOpcional: () => ({ stats: { diagCount: 3 } }),
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

describe('Sidebar · permisos (7.3)', () => {
  it('el Operario con la matriz por defecto ve en Gestión sólo Historial', () => {
    montar('OPERARIO');

    expect(enlacesDe('Gestión')).toEqual(['Historial']);
    expect(screen.queryByRole('link', { name: /Usuarios/ })).toBeNull();
  });

  it('quien tiene usuarios.gestionar ve "Usuarios" dentro de Configuración', () => {
    montar('ADMINISTRADOR');
    fireEvent.click(grupoConfiguracion());

    expect(enlacesDe('Gestión')).toEqual([
      'Historial',
      'Umbrales',
      'Criterio de diagnóstico',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Usuarios',
      'Preferencias',
    ]);
    expect(screen.getByRole('link', { name: /Usuarios/ }).getAttribute('href')).toBe('/usuarios');
  });

  it('el Ingeniero Agrónomo no ve Usuarios', () => {
    montar('INGENIERO_AGRONOMO');
    fireEvent.click(grupoConfiguracion());

    expect(enlacesDe('Gestión')).not.toContain('Usuarios');
  });

  it('sin ninguna vista de Configuración el grupo no se muestra', () => {
    montar('OPERARIO');

    expect(screen.queryByRole('button', { name: 'Configuración' })).toBeNull();
  });

  it('un grupo sin ítems visibles no se muestra', () => {
    montar('OPERARIO', ['historial.ver']);

    expect(screen.queryByText('Principal')).toBeNull();
    expect(screen.queryByRole('navigation', { name: 'Principal' })).toBeNull();
    expect(enlacesDe('Gestión')).toEqual(['Historial']);
  });

  it('no muestra Demo Expo en el menú principal', () => {
    montar('ADMINISTRADOR');

    expect(screen.queryByRole('link', { name: /Demo Expo/ })).toBeNull();
  });
});

const grupoConfiguracion = () => screen.getByRole('button', { name: 'Configuración' });

describe('Sidebar · grupo Configuración', () => {
  it('arranca colapsado y un clic lo despliega; otro lo colapsa', () => {
    montar('ADMINISTRADOR');

    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('link', { name: /Umbrales/ })).toBeNull();

    fireEvent.click(grupoConfiguracion());
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
    expect(enlacesDe('Configuración', 'group')).toEqual([
      'Umbrales',
      'Criterio de diagnóstico',
      'Motor de reglas',
      'Hardware',
      'Topología',
      'Usuarios',
      'Preferencias',
    ]);
    expect(screen.getByRole('link', { name: /Umbrales/ }).getAttribute('href')).toBe(
      '/configuracion',
    );
    expect(screen.getByRole('link', { name: /Preferencias/ }).getAttribute('href')).toBe(
      '/preferencias',
    );

    fireEvent.click(grupoConfiguracion());
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('link', { name: /Umbrales/ })).toBeNull();
  });

  it('"Criterio de diagnóstico" lleva a su vista y pide también diagnosticos.ver', () => {
    montar('ADMINISTRADOR', undefined, '/criterio-diagnostico');

    // Se abre solo: la vista actual es del grupo
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
    const link = screen.getByRole('link', { name: /Criterio de diagnóstico/ });
    expect(link.getAttribute('href')).toBe('/criterio-diagnostico');
    expect(link.getAttribute('aria-current')).toBe('page');

    cleanup();
    montar('INGENIERO_AGRONOMO', ['configuracion.ver', 'vivero.ver'], '/configuracion');
    expect(screen.queryByRole('link', { name: /Criterio de diagnóstico/ })).toBeNull();
    expect(screen.getByRole('link', { name: /Umbrales/ })).toBeTruthy();
  });

  it('se abre solo si la vista actual es una de las suyas', () => {
    montar('ADMINISTRADOR', undefined, '/hardware');

    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
    expect(screen.getByRole('link', { name: /Hardware/ }).getAttribute('aria-current')).toBe(
      'page',
    );
  });
});

describe('Sidebar · colapsable a íconos', () => {
  const montarConProvider = (ruta = '/') =>
    render(
      <MemoryRouter initialEntries={[ruta]}>
        <ConPermisos rol="ADMINISTRADOR">
          <SidebarProvider>
            <Sidebar />
          </SidebarProvider>
        </ConPermisos>
      </MemoryRouter>,
    );
  const sidebar = () => document.querySelector('aside')!;

  afterEach(() => localStorage.clear());

  it('Ctrl+B colapsa y expande, y se recuerda', () => {
    demo.visible = false;
    montarConProvider();
    expect(sidebar().dataset.collapsed).toBe('false');

    fireEvent.keyDown(window, { key: 'b', ctrlKey: true });
    expect(sidebar().dataset.collapsed).toBe('true');
    expect(screen.getByText('Principal').getAttribute('aria-hidden')).toBe('true');
    expect(localStorage.getItem('yerbanalytics.sidebar.collapsed')).toBe('true');

    fireEvent.keyDown(window, { key: 'b', ctrlKey: true });
    expect(sidebar().dataset.collapsed).toBe('false');
  });

  it('colapsada, el clic en Configuración la expande con el grupo abierto', () => {
    demo.visible = false;
    montarConProvider();
    fireEvent.click(screen.getByRole('button', { name: 'Colapsar menú' }));

    fireEvent.click(grupoConfiguracion());
    expect(sidebar().dataset.collapsed).toBe('false');
    expect(grupoConfiguracion().getAttribute('aria-expanded')).toBe('true');
  });

  it('colapsada, pasar el mouse por un ítem muestra su nombre', () => {
    demo.visible = false;
    montarConProvider();
    fireEvent.keyDown(window, { key: 'b', ctrlKey: true });

    fireEvent.mouseEnter(screen.getByRole('link', { name: /Historial/ }));
    expect(screen.getByRole('tooltip').textContent).toBe('Historial');
  });
});
