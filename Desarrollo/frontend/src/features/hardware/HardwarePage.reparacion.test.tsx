// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { HardwarePage } from './HardwarePage';
import * as data from '@/data';
import { MockRepository } from '@/data/mock/mockRepository';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';

/** Repositorio nuevo por test: el singleton de la app compartiría la flota (y las reparaciones) entre tests. */
let repo: MockRepository;

beforeEach(() => {
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  repo = new MockRepository(0);
  vi.spyOn(data, 'getRepository').mockReturnValue(repo);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const montar = (rol: Rol, permisos?: Permiso[]) =>
  render(
    <ConPermisos rol={rol} permisos={permisos}>
      <PageMetaProvider>
        <HardwarePage />
      </PageMetaProvider>
    </ConPermisos>,
  );

/** La fila del dispositivo averiado del seed del mock (la electroválvula de MZ-5-042, sin caudal). */
const filaAveriada = async () => (await screen.findByText('DEV-110')).closest('tr') as HTMLElement;

describe('Hardware · falla y registro de reparación (S-05)', () => {
  it('muestra la falla traducida y desde cuándo, no el código del contrato', async () => {
    montar('OPERARIO');

    const fila = await filaAveriada();
    expect(within(fila).getByText('Falla hidráulica')).toBeTruthy();
    expect(within(fila).getByText(/sin caudal/)).toBeTruthy();
    expect(within(fila).getByText(/hace 2 h/)).toBeTruthy();
    expect(within(fila).queryByText('SIN_CAUDAL')).toBeNull();
  });

  it('quien puede reparar ve el botón sólo en los dispositivos averiados', async () => {
    montar('OPERARIO');

    const fila = await filaAveriada();
    expect(within(fila).getByRole('button', { name: 'Registrar reparación' })).toBeTruthy();
    expect(screen.getAllByRole('button', { name: 'Registrar reparación' })).toHaveLength(1);
  });

  it('el Operario repara pero no recambia (eso es hardware.gestionar)', async () => {
    montar('OPERARIO');

    await filaAveriada();
    expect(screen.queryByRole('button', { name: 'Recambiar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Registrar dispositivo' })).toBeNull();
  });

  it('el Administrador puede las dos cosas sobre un dispositivo averiado', async () => {
    montar('ADMINISTRADOR');

    const fila = await filaAveriada();
    expect(within(fila).getByRole('button', { name: 'Recambiar' })).toBeTruthy();
    expect(within(fila).getByRole('button', { name: 'Registrar reparación' })).toBeTruthy();
  });

  it('sin hardware.reparar no se ofrece el botón', async () => {
    montar('PRODUCTOR_VIVERISTA');

    const fila = await filaAveriada();
    expect(within(fila).queryByRole('button', { name: 'Registrar reparación' })).toBeNull();
  });

  it('el detalle es obligatorio: sin texto no se puede confirmar', async () => {
    const reparar = vi.spyOn(repo, 'registerRepair');
    montar('OPERARIO');

    fireEvent.click(within(await filaAveriada()).getByRole('button', { name: 'Registrar reparación' }));
    const confirmar = screen.getByRole('button', { name: 'Confirmar reparación' }) as HTMLButtonElement;
    expect(confirmar.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('Qué se hizo'), { target: { value: '   ' } });
    expect(confirmar.disabled).toBe(true);
    expect(reparar).not.toHaveBeenCalled();
  });

  it('registra la reparación con el detalle, avisa y levanta la falla de la tabla', async () => {
    const reparar = vi.spyOn(repo, 'registerRepair');
    montar('OPERARIO');

    fireEvent.click(within(await filaAveriada()).getByRole('button', { name: 'Registrar reparación' }));
    expect(screen.getByText(/Registrar reparación · Electroválvula · MZ-5-042/)).toBeTruthy();
    fireEvent.change(screen.getByLabelText('Qué se hizo'), { target: { value: '  Se destapó el emisor  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reparación' }));

    await waitFor(() => expect(reparar).toHaveBeenCalledWith('DEV-110', 'Se destapó el emisor'));
    expect(await screen.findByText(/Reparación registrada en Electroválvula · MZ-5-042/)).toBeTruthy();
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Registrar reparación' })).toBeNull());
    expect(within(await filaAveriada()).queryByText('Falla hidráulica')).toBeNull();
  });

  it('si el backend rechaza, el formulario queda abierto con el motivo', async () => {
    vi.spyOn(repo, 'registerRepair').mockRejectedValue(
      new Error('El dispositivo «DEV-110» no tiene una falla registrada.'),
    );
    montar('OPERARIO');

    fireEvent.click(within(await filaAveriada()).getByRole('button', { name: 'Registrar reparación' }));
    fireEvent.change(screen.getByLabelText('Qué se hizo'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar reparación' }));

    expect(await screen.findByText('El dispositivo «DEV-110» no tiene una falla registrada.')).toBeTruthy();
    expect(screen.getByLabelText('Qué se hizo')).toBeTruthy();
  });

  it('Cancelar cierra el formulario sin registrar nada', async () => {
    const reparar = vi.spyOn(repo, 'registerRepair');
    montar('OPERARIO');

    fireEvent.click(within(await filaAveriada()).getByRole('button', { name: 'Registrar reparación' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByLabelText('Qué se hizo')).toBeNull();
    expect(reparar).not.toHaveBeenCalled();
  });
});
