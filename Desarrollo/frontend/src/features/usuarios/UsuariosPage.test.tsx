// @vitest-environment jsdom
/* Vista Usuarios con el repositorio mock (8.6): alta, suspensión, matriz, auditoría y política. */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import { montarApp } from '@/test/app';
import { seguridadDemo } from '@/test/sesion';
import type { MockSeguridadRepository } from '@/data/mock/mockSeguridadRepository';

let repo: MockSeguridadRepository;

beforeEach(async () => {
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  repo = await seguridadDemo('admin');
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const fila = (username: string) => document.querySelector(`tr[data-username="${username}"]`) as HTMLElement;

describe('Vista Usuarios (8.1)', () => {
  it('tiene las cuatro pestañas para el Administrador', async () => {
    montarApp('/usuarios');

    const tabs = await screen.findAllByRole('tab');
    expect(tabs.map((t) => t.textContent)).toEqual(['Usuarios', 'Roles y permisos', 'Auditoría', 'Seguridad']);
    expect(screen.getByRole('heading', { level: 1, name: 'Usuarios' })).toBeTruthy();
  });

  it('el Ingeniero Agrónomo no puede entrar', async () => {
    await seguridadDemo('agronomo');
    montarApp('/usuarios');

    expect(await screen.findByText('No tenés permiso para ver esta sección')).toBeTruthy();
  });
});

describe('Pestaña Usuarios (8.2)', () => {
  it('lista personas y cuentas de servicio por separado', async () => {
    await repo.crearUsuario({ username: 'simulador', nombre: 'Simulador', rol: 'SERVICIO', clave: 'temporal1' });
    montarApp('/usuarios');

    const personas = await screen.findByRole('table', { name: 'Personas' });
    const servicio = screen.getByRole('table', { name: 'Cuentas de servicio' });
    expect(within(personas).getByText('agronomo')).toBeTruthy();
    expect(within(personas).queryByText('simulador')).toBeNull();
    expect(within(servicio).getByText('simulador')).toBeTruthy();
  });

  it('alta válida: el usuario aparece activo con clave temporal y queda auditado', async () => {
    montarApp('/usuarios');
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo usuario' }));

    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'JPerez' } });
    fireEvent.change(screen.getByLabelText('Nombre a mostrar'), { target: { value: 'Juan Pérez' } });
    fireEvent.change(screen.getByLabelText('Rol'), { target: { value: 'OPERARIO' } });
    fireEvent.change(screen.getByLabelText('Contraseña temporal'), { target: { value: 'temporal1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Dar de alta' }));

    await waitFor(() => expect(fila('jperez')).toBeTruthy());
    expect(within(fila('jperez')).getByText('Activo')).toBeTruthy();
    expect(within(fila('jperez')).getByText('Clave temporal')).toBeTruthy();
    const audit = await repo.getAuditoria({ objetivo: 'jperez' });
    expect(audit.items[0].tipo).toBe('USUARIO_ALTA');
  });

  it('elegir el rol Servicio advierte que es para integraciones', async () => {
    montarApp('/usuarios');
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo usuario' }));

    fireEvent.change(screen.getByLabelText('Rol'), { target: { value: 'SERVICIO' } });

    expect(screen.getByRole('note').textContent).toMatch(/para integraciones/);
  });

  it('un username repetido muestra el 409 del repositorio', async () => {
    montarApp('/usuarios');
    fireEvent.click(await screen.findByRole('button', { name: 'Nuevo usuario' }));

    fireEvent.change(screen.getByLabelText('Nombre de usuario'), { target: { value: 'operario' } });
    fireEvent.change(screen.getByLabelText('Nombre a mostrar'), { target: { value: 'Otro' } });
    fireEvent.change(screen.getByLabelText('Contraseña temporal'), { target: { value: 'temporal1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Dar de alta' }));

    expect((await screen.findByRole('alert')).textContent).toMatch(/Ya existe un usuario «operario»/);
  });

  it('suspender y reactivar', async () => {
    montarApp('/usuarios');
    await waitFor(() => expect(fila('operario')).toBeTruthy());

    fireEvent.click(within(fila('operario')).getByRole('button', { name: 'Suspender' }));
    await waitFor(() => expect(within(fila('operario')).getByText('Suspendido')).toBeTruthy());

    fireEvent.click(within(fila('operario')).getByRole('button', { name: 'Reactivar' }));
    await waitFor(() => expect(within(fila('operario')).getByText('Activo')).toBeTruthy());
  });

  it('no ofrece suspenderse ni darse de baja a uno mismo', async () => {
    montarApp('/usuarios');
    await waitFor(() => expect(fila('admin')).toBeTruthy());

    expect((within(fila('admin')).getByRole('button', { name: 'Suspender' }) as HTMLButtonElement).disabled).toBe(true);
    expect((within(fila('admin')).getByRole('button', { name: 'Dar de baja' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('la baja pide confirmación y el usuario desaparece salvo que se muestren las bajas', async () => {
    montarApp('/usuarios');
    await waitFor(() => expect(fila('productor')).toBeTruthy());

    fireEvent.click(within(fila('productor')).getByRole('button', { name: 'Dar de baja' }));
    expect(screen.getByRole('alertdialog', { name: 'Confirmar baja' })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    await waitFor(() => expect(fila('productor')).toBeNull());
    fireEvent.click(screen.getByLabelText('Mostrar bajas'));
    await waitFor(() => expect(within(fila('productor')).getByText('Baja')).toBeTruthy());
  });
});

describe('Pestaña Roles y permisos (8.3)', () => {
  it('los intocables del Administrador están marcados y bloqueados', async () => {
    montarApp('/usuarios?tab=roles');

    const casilla = (await screen.findByLabelText('usuarios.gestionar para Administrador')) as HTMLInputElement;
    expect(casilla.checked).toBe(true);
    expect(casilla.disabled).toBe(true);
  });

  it('marcar una edición marca su lectura; guardar avisa del cierre de sesiones y persiste', async () => {
    montarApp('/usuarios?tab=roles');

    const editar = (await screen.findByLabelText('reglas.editar para Operario')) as HTMLInputElement;
    const ver = screen.getByLabelText('reglas.ver para Operario') as HTMLInputElement;
    expect(ver.checked).toBe(false);

    fireEvent.click(editar);

    expect(editar.checked).toBe(true);
    expect(ver.checked).toBe(true);
    expect(screen.getByRole('note').textContent).toMatch(/cierra las sesiones abiertas de los usuarios con el rol Operario/);

    fireEvent.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText(/Permisos guardados: Operario/)).toBeTruthy();
    const operario = (await repo.getRoles()).find((r) => r.rol === 'OPERARIO')!;
    expect(operario.permisos).toEqual(expect.arrayContaining(['reglas.ver', 'reglas.editar']));
  });

  it('quitar la lectura quita la edición que depende de ella', async () => {
    montarApp('/usuarios?tab=roles');

    const ver = (await screen.findByLabelText('reglas.ver para Ingeniero Agrónomo')) as HTMLInputElement;
    const editar = screen.getByLabelText('reglas.editar para Ingeniero Agrónomo') as HTMLInputElement;
    expect(editar.checked).toBe(true);

    fireEvent.click(ver);

    expect(ver.checked).toBe(false);
    expect(editar.checked).toBe(false);
  });
});

describe('Pestaña Auditoría (8.4)', () => {
  it('muestra el estado de la cadena y filtra por usuario objetivo', async () => {
    await repo.crearUsuario({ username: 'jperez', nombre: 'Juan Pérez', rol: 'OPERARIO', clave: 'temporal1' });
    montarApp('/usuarios?tab=auditoria');

    expect((await screen.findByText(/Cadena íntegra/)).textContent).toMatch(/registros verificados/);
    const tabla = await screen.findByRole('table', { name: 'Registro de auditoría' });
    await waitFor(() => expect(within(tabla).getAllByRole('row').length).toBeGreaterThan(3));

    fireEvent.change(screen.getByLabelText('Usuario o rol objetivo'), { target: { value: 'jperez' } });
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }));

    await waitFor(() => {
      const filas = within(screen.getByRole('table', { name: 'Registro de auditoría' })).getAllByRole('row').slice(1);
      expect(filas).toHaveLength(1);
      expect(filas[0].textContent).toMatch(/jperez/);
      expect(filas[0].textContent).toMatch(/Alta de usuario/);
    });
  });

  it('sin auditoria.ver la pestaña no aparece', async () => {
    const otraMatriz = (await repo.getRoles()).find((r) => r.rol === 'INGENIERO_AGRONOMO')!;
    await repo.guardarPermisosRol('INGENIERO_AGRONOMO', [...otraMatriz.permisos, 'usuarios.gestionar']);
    await repo.login('agronomo', 'demo');
    montarApp('/usuarios');

    const tabs = await screen.findAllByRole('tab');
    expect(tabs.map((t) => t.textContent)).toEqual(['Usuarios', 'Roles y permisos', 'Seguridad']);
  });
});

describe('Pestaña Seguridad (8.5)', () => {
  it('valida el rango 5–480 y guarda el tiempo de inactividad', async () => {
    montarApp('/usuarios?tab=seguridad');

    const campo = (await screen.findByLabelText('Tiempo máximo de inactividad (min)')) as HTMLInputElement;
    expect(campo.value).toBe('60');

    fireEvent.change(campo, { target: { value: '2' } });
    expect(screen.getByRole('alert').textContent).toMatch(/entre 5 y 480/);
    expect((screen.getByRole('button', { name: 'Guardar' }) as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(campo, { target: { value: '15' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('Tiempo máximo de inactividad: 15 min.')).toBeTruthy();
    expect((await repo.getPolitica()).inactividadMin).toBe(15);
  });
});
