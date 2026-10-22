// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TankCard } from './TankCard';
import { getRepository, TankLoadError } from '@/data';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const mount = (zoneId: string, rol: Rol = 'OPERARIO', permisos?: Permiso[]) =>
  render(
    <ConPermisos rol={rol} permisos={permisos}>
      <TankCard zoneId={zoneId} />
    </ConPermisos>,
  );

describe('TankCard', () => {
  it('sin insumos.ver no muestra nada ni consulta', () => {
    const query = vi.spyOn(getRepository(), 'getTanks');
    const { container } = mount('MZ-1', 'SERVICIO', ['vivero.ver']);

    expect(container.textContent).toBe('');
    expect(query).not.toHaveBeenCalled();
  });

  it('un tanque sin registrar se dice "Sin registrar" y avisa que no se aplica', async () => {
    mount('MZ-1');

    expect((await screen.findAllByText('Sin registrar')).length).toBe(2);
    expect(screen.getAllByText(/no se aplica/i).length).toBe(2);
  });

  it('la primera carga pide la capacidad y deja el tanque con su nivel', async () => {
    const load = vi.spyOn(getRepository(), 'loadTank');
    mount('MZ-2');

    fireEvent.click(await screen.findByRole('button', { name: 'Registrar tanque de fertilizante' }));
    const submit = screen.getByRole('button', { name: 'Registrar carga' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('Litros que se cargan'), { target: { value: '20' } });
    expect(submit.disabled).toBe(true); // falta la capacidad: el tanque todavía no existe
    fireEvent.change(screen.getByLabelText(/Capacidad del tanque/), { target: { value: '50' } });
    fireEvent.click(submit);

    await waitFor(() => expect(load).toHaveBeenCalledWith('MZ-2', 'FERTILIZER', { liters: 20, capacityLiters: 50 }));
    expect(await screen.findByText(/20 L de 50 L · 40 %/)).toBeTruthy();
  });

  it('acepta la coma decimal y no manda la capacidad si el tanque ya existe', async () => {
    await getRepository().loadTank('MZ-3', 'FUNGICIDE', { liters: 10, capacityLiters: 40 });
    const load = vi.spyOn(getRepository(), 'loadTank');
    mount('MZ-3');

    fireEvent.click(await screen.findByRole('button', { name: 'Cargar fitosanitario' }));
    fireEvent.change(screen.getByLabelText('Litros que se cargan'), { target: { value: '2,5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar carga' }));

    await waitFor(() => expect(load).toHaveBeenCalledWith('MZ-3', 'FUNGICIDE', { liters: 2.5 }));
    expect(await screen.findByText(/12,5 L de 40 L/)).toBeTruthy();
  });

  it('un tanque bajo el aviso se marca "Reponer"', async () => {
    await getRepository().loadTank('MZ-4', 'FERTILIZER', { liters: 5, capacityLiters: 50 });
    mount('MZ-4');

    expect(await screen.findByText('Reponer')).toBeTruthy();
  });

  it('un rechazo del backend se muestra tal cual y deja el formulario abierto', async () => {
    vi.spyOn(getRepository(), 'loadTank').mockRejectedValue(
      new TankLoadError('La carga no entra: el tanque quedaría en 60 L.'),
    );
    mount('MZ-5');

    fireEvent.click(await screen.findByRole('button', { name: 'Registrar tanque de fertilizante' }));
    fireEvent.change(screen.getByLabelText('Litros que se cargan'), { target: { value: '60' } });
    fireEvent.change(screen.getByLabelText(/Capacidad del tanque/), { target: { value: '50' } });
    fireEvent.click(screen.getByRole('button', { name: 'Registrar carga' }));

    expect((await screen.findByRole('alert')).textContent).toContain('La carga no entra');
    expect(screen.getByLabelText('Litros que se cargan')).toBeTruthy();
  });

  it('sin insumos.gestionar se ve el stock pero no hay botones de carga', async () => {
    await getRepository().loadTank('MZ-6', 'FERTILIZER', { liters: 20, capacityLiters: 50 });
    mount('MZ-6', 'PRODUCTOR_VIVERISTA');

    expect(await screen.findByText(/20 L de 50 L/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Cargar|Registrar tanque/ })).toBeNull();
  });

  it('muestra sólo los tanques de su macro-zona', async () => {
    await getRepository().loadTank('MZ-1', 'FERTILIZER', { liters: 30, capacityLiters: 50 });
    mount('MZ-6');

    await screen.findAllByText('Sin registrar');
    expect(screen.queryByText(/30 L de 50 L/)).toBeNull();
  });
});
