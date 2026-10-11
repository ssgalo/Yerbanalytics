// @vitest-environment jsdom
/* ============================================================
   La sesión de punta a punta en el dashboard, con el repositorio mock: login, guardas, avisos
   de cierre, permisos por rol, topbar y señal de actividad (6.6 / 7.x).
   ============================================================ */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import * as data from '@/data';
import { getRepository } from '@/data';
import { HttpSeguridadRepository } from '@/data/http/httpSeguridadRepository';
import { avisarPermisoDenegado, avisarSesionCerrada } from '@/data/sesionEventos';
import { montarApp } from '@/test/app';
import { seguridadDemo } from '@/test/sesion';

beforeEach(() => {
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

async function ingresar(usuario: string, clave = 'demo') {
  fireEvent.change(await screen.findByLabelText('Usuario'), { target: { value: usuario } });
  fireEvent.change(screen.getByLabelText('Contraseña'), { target: { value: clave } });
  fireEvent.click(screen.getByRole('button', { name: 'Ingresar' }));
}

const enlacesDe = (grupo: string) =>
  within(screen.getByRole('navigation', { name: grupo }))
    .getAllByRole('link')
    .map((a) => a.textContent?.replace(/\d+$/, '').trim());

describe('Login (7.1)', () => {
  it('sin sesión, una ruta protegida lleva al login y al entrar vuelve a la ruta pedida', async () => {
    await seguridadDemo();
    const ubicacion = montarApp('/reglas');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
    expect(ubicacion.actual?.pathname).toBe('/login');

    await ingresar('agronomo');

    expect(await screen.findByRole('heading', { level: 1, name: 'Motor de reglas' })).toBeTruthy();
    expect(ubicacion.actual?.pathname).toBe('/reglas');
  });

  it('credenciales incorrectas: mensaje genérico, sin marcar campos y con la clave limpia', async () => {
    await seguridadDemo();
    montarApp('/');

    await ingresar('agronomo', 'equivocada');

    expect((await screen.findByRole('alert')).textContent).toBe('Credenciales incorrectas');
    const clave = screen.getByLabelText('Contraseña') as HTMLInputElement;
    const usuario = screen.getByLabelText('Usuario') as HTMLInputElement;
    expect(clave.value).toBe('');
    expect(usuario.value).toBe('agronomo');
    expect(usuario.getAttribute('aria-invalid')).toBeNull();
    expect(clave.getAttribute('aria-invalid')).toBeNull();
  });

  it('un usuario inexistente recibe exactamente el mismo mensaje', async () => {
    await seguridadDemo();
    montarApp('/');

    await ingresar('nadie', 'demo');

    expect((await screen.findByRole('alert')).textContent).toBe('Credenciales incorrectas');
  });

  it('en modo mock lista los usuarios demo y elegir uno completa el formulario', async () => {
    await seguridadDemo();
    montarApp('/login');

    fireEvent.click(await screen.findByRole('button', { name: /Ana Benítez/ }));

    expect((screen.getByLabelText('Usuario') as HTMLInputElement).value).toBe('agronomo');
    expect((screen.getByLabelText('Contraseña') as HTMLInputElement).value).toBe('demo');
    expect(screen.getByText('Usuarios de la demo')).toBeTruthy();
  });

  it('con el repositorio http el login no menciona usuarios demo', async () => {
    vi.spyOn(data, 'getSeguridadRepository').mockReturnValue(new HttpSeguridadRepository('http://x/api'));
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: false, status: 401, json: async () => ({ error: 'x', motivo: 'SIN_SESION' }) })),
    );
    montarApp('/');

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
    expect(screen.queryByText(/Usuarios de la demo/)).toBeNull();
  });

  it('con sesión vigente, /login redirige al Panel general', async () => {
    await seguridadDemo('agronomo');
    const ubicacion = montarApp('/login');

    await waitFor(() => expect(ubicacion.actual?.pathname).toBe('/'));
    expect(await screen.findByRole('heading', { level: 1, name: 'Panel general' })).toBeTruthy();
  });
});

describe('Cierre de sesión por 401 (6.6)', () => {
  it.each([
    ['SESION_EXPIRADA', 'Tu sesión se cerró por inactividad'],
    ['SESION_REVOCADA', 'Tu cuenta cambió; volvé a iniciar sesión'],
  ] as const)('%s lleva al login con el aviso "%s" y al reingresar vuelve a la vista', async (motivo, aviso) => {
    await seguridadDemo('agronomo');
    const ubicacion = montarApp('/historial');
    await screen.findByRole('heading', { level: 1, name: /Historial/ });

    act(() => avisarSesionCerrada(motivo));

    expect((await screen.findByRole('status')).textContent).toBe(aviso);
    expect(ubicacion.actual?.pathname).toBe('/login');

    await ingresar('agronomo');
    await waitFor(() => expect(ubicacion.actual?.pathname).toBe('/historial'));
  });

  it('SIN_SESION lleva al login sin aviso', async () => {
    await seguridadDemo('agronomo');
    montarApp('/historial');
    await screen.findByRole('heading', { level: 1, name: /Historial/ });

    act(() => avisarSesionCerrada('SIN_SESION'));

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
    expect(screen.queryByText(/inactividad|Tu cuenta cambió/)).toBeNull();
  });

  it('un 403 inesperado recarga el perfil', async () => {
    const repo = await seguridadDemo('agronomo');
    montarApp('/historial');
    await screen.findByRole('heading', { level: 1, name: /Historial/ });
    const perfil = vi.spyOn(repo, 'getPerfil');

    act(() => avisarPermisoDenegado('reglas.editar'));

    await waitFor(() => expect(perfil).toHaveBeenCalledTimes(1));
  });
});

describe('Permisos en el dashboard (6.6 / 7.2)', () => {
  it('el Operario demo ve en Gestión sólo Historial', async () => {
    await seguridadDemo('operario');
    montarApp('/');

    await screen.findByRole('heading', { level: 1, name: 'Panel general' });
    expect(enlacesDe('Gestión')).toEqual(['Historial']);
  });

  it('el Operario que abre /topologia por URL ve el aviso y no se consulta la topología', async () => {
    await seguridadDemo('operario');
    const topologia = vi.spyOn(getRepository(), 'getTopologia');
    montarApp('/topologia');

    expect(await screen.findByText('No tenés permiso para ver esta sección')).toBeTruthy();
    expect(topologia).not.toHaveBeenCalled();
  });

  it('un rol sin ninguna vista ve "Tu rol no tiene secciones habilitadas" y puede cerrar sesión', async () => {
    const repo = await seguridadDemo('admin');
    await repo.guardarPermisosRol('OPERARIO', []);
    await repo.login('operario', 'demo');
    montarApp('/');

    expect(await screen.findByRole('heading', { name: 'Tu rol no tiene secciones habilitadas' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }));
    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
  });

  it('con clave temporal sólo se ve el cambio de contraseña; al cambiarla entra al Panel', async () => {
    const repo = await seguridadDemo('admin');
    await repo.crearUsuario({ username: 'jperez', nombre: 'Juan Pérez', rol: 'OPERARIO', clave: 'temporal1' });
    await repo.logout();
    const ubicacion = montarApp('/');

    await ingresar('jperez', 'temporal1');
    expect(await screen.findByRole('heading', { name: 'Cambiar contraseña' })).toBeTruthy();
    expect(ubicacion.actual?.pathname).toBe('/cambiar-clave');
    expect(screen.queryByRole('button', { name: 'Volver' })).toBeNull();

    fireEvent.change(screen.getByLabelText('Contraseña temporal'), { target: { value: 'temporal1' } });
    fireEvent.change(screen.getByLabelText('Contraseña nueva'), { target: { value: 'definitiva1' } });
    fireEvent.change(screen.getByLabelText('Repetí la contraseña nueva'), { target: { value: 'definitiva1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar contraseña' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Panel general' })).toBeTruthy();
  });
});

describe('Topbar (7.4)', () => {
  it('muestra el usuario real y su menú cierra la sesión', async () => {
    await seguridadDemo('agronomo');
    montarApp('/');

    const boton = await screen.findByRole('button', { name: 'Menú de Ana Benítez' });
    expect(within(boton).getByText('Ana Benítez')).toBeTruthy();
    expect(within(boton).getByText('Ingeniero Agrónomo')).toBeTruthy();
    expect(within(boton).getByText('AB')).toBeTruthy();

    fireEvent.click(boton);
    expect(screen.getByRole('menuitem', { name: /Cambiar contraseña/ })).toBeTruthy();
    fireEvent.click(screen.getByRole('menuitem', { name: /Cerrar sesión/ }));

    expect(await screen.findByRole('heading', { name: 'Iniciar sesión' })).toBeTruthy();
    expect(screen.queryByText(/inactividad/)).toBeNull();
  });
});

describe('Actividad e inactividad en la app (6.6)', () => {
  /** Monta con relojes falsos y un snapshot fijo (el sondeo sigue corriendo, pero no regenera el vivero). */
  async function montarConRelojFalso(usuario: string, ruta: string) {
    const snapshot = await getRepository().getNursery();
    const repo = await seguridadDemo(usuario);
    const sondeo = vi.spyOn(getRepository(), 'getNursery').mockResolvedValue(snapshot);
    const ping = vi.spyOn(repo, 'registrarActividad');
    vi.useFakeTimers();
    const ubicacion = montarApp(ruta);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    return { repo, sondeo, ping, ubicacion };
  }

  it('el sondeo del vivero no dispara señales de actividad; la interacción sí', async () => {
    const { sondeo, ping } = await montarConRelojFalso('agronomo', '/');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(sondeo.mock.calls.length).toBeGreaterThanOrEqual(6);
    expect(ping).not.toHaveBeenCalled();

    fireEvent.pointerDown(document.body);
    expect(ping).toHaveBeenCalledTimes(1);
  });

  it('sin interacción, el temporizador local cierra la sesión y el login avisa por qué', async () => {
    const { ubicacion } = await montarConRelojFalso('agronomo', '/historial');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(59 * 60_000);
    });
    expect(screen.getByRole('alertdialog', { name: 'Aviso de inactividad' })).toBeTruthy();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(61_000);
    });
    expect(ubicacion.actual?.pathname).toBe('/login');
    expect(screen.getByRole('status').textContent).toBe('Tu sesión se cerró por inactividad');
  });
});
