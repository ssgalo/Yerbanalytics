// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ManualLockCard } from './ManualLockCard';
import { getRepository } from '@/data';
import { ConPermisos } from '@/test/sesion';
import type { Permiso, Rol } from '@/types/seguridad';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const montar = (
  props: { zonaId: string; sectorId?: string; label: string },
  rol: Rol = 'OPERARIO',
  permisos?: Permiso[],
) =>
  render(
    <ConPermisos rol={rol} permisos={permisos}>
      <ManualLockCard {...props} />
    </ConPermisos>,
  );

describe('ManualLockCard', () => {
  it('sin bloqueo ofrece "Bloquear" y no consulta nada si el rol no ve bloqueos', async () => {
    const consulta = vi.spyOn(getRepository(), 'getManualLocks');
    const { container } = montar({ zonaId: 'MZ-1', label: 'la macro-zona 1' }, 'SERVICIO', [
      'vivero.ver',
    ]);

    expect(container.textContent).toBe('');
    expect(consulta).not.toHaveBeenCalled();
  });

  it('el motivo es obligatorio: sin texto no se puede confirmar', async () => {
    const activar = vi.spyOn(getRepository(), 'activateManualLock');
    montar({ zonaId: 'MZ-1', label: 'la macro-zona 1' });

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear actuación' }));
    const confirmar = screen.getByRole('button', { name: 'Bloquear' }) as HTMLButtonElement;
    expect(confirmar.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: '   ' } });
    expect(confirmar.disabled).toBe(true);
    expect(activar).not.toHaveBeenCalled();
  });

  it('bloquea con el motivo, muestra el estado "Bloqueado" con quién y por qué, y se puede reanudar', async () => {
    const activar = vi.spyOn(getRepository(), 'activateManualLock');
    const reanudar = vi.spyOn(getRepository(), 'releaseManualLock');
    montar({ zonaId: 'MZ-4', sectorId: 'MZ-4-002', label: 'el sector MZ-4-002' });

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear actuación' }));
    fireEvent.change(screen.getByLabelText('Motivo'), {
      target: { value: 'mantenimiento de la línea' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }));

    await waitFor(() =>
      expect(activar).toHaveBeenCalledWith({
        zonaId: 'MZ-4',
        sectorId: 'MZ-4-002',
        motivo: 'mantenimiento de la línea',
      }),
    );
    expect(await screen.findByText('Bloqueado')).toBeTruthy();
    expect(screen.getByText(/mantenimiento de la línea/)).toBeTruthy();
    expect(screen.getByText(/Usuario de la demo/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Reanudar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(reanudar).toHaveBeenCalledTimes(1));
    expect(await screen.findByRole('button', { name: 'Bloquear actuación' })).toBeTruthy();
  });

  it('bloquear la macro-zona no manda sector', async () => {
    const activar = vi.spyOn(getRepository(), 'activateManualLock');
    montar({ zonaId: 'MZ-5', label: 'la macro-zona 5' });

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear actuación' }));
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }));

    await waitFor(() => expect(activar).toHaveBeenCalledWith({ zonaId: 'MZ-5', motivo: 'x' }));
    // Se reanuda para no dejar estado en el singleton del mock.
    fireEvent.click(await screen.findByRole('button', { name: 'Reanudar' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    await waitFor(() => expect(screen.queryByText('Bloqueado')).toBeNull());
  });

  it('un rechazo del backend se muestra tal cual y deja el formulario abierto', async () => {
    const { ManualLockError } = await import('@/data');
    vi.spyOn(getRepository(), 'activateManualLock').mockRejectedValue(
      new ManualLockError('La macro-zona MZ-6 ya tiene un bloqueo vigente.'),
    );
    montar({ zonaId: 'MZ-6', label: 'la macro-zona 6' });

    fireEvent.click(await screen.findByRole('button', { name: 'Bloquear actuación' }));
    fireEvent.change(screen.getByLabelText('Motivo'), { target: { value: 'x' } });
    fireEvent.click(screen.getByRole('button', { name: 'Bloquear' }));

    expect((await screen.findByRole('alert')).textContent).toContain('ya tiene un bloqueo vigente');
    expect(screen.getByLabelText('Motivo')).toBeTruthy();
  });

  it('sin bloqueos.gestionar se ve el estado pero no hay botones', async () => {
    await getRepository().activateManualLock({ zonaId: 'MZ-3', motivo: 'por obras' });
    montar({ zonaId: 'MZ-3', label: 'la macro-zona 3' }, 'PRODUCTOR_VIVERISTA');

    expect(await screen.findByText('Bloqueado')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reanudar' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Bloquear actuación' })).toBeNull();
    const [lock] = await getRepository().getManualLocks();
    await getRepository().releaseManualLock(lock.id);
  });

  it('en un sector, el bloqueo de su zona se informa y no se puede reanudar desde ahí', async () => {
    const zona = await getRepository().activateManualLock({
      zonaId: 'MZ-2',
      motivo: 'riesgo de helada',
    });
    montar({ zonaId: 'MZ-2', sectorId: 'MZ-2-001', label: 'el sector MZ-2-001' });

    expect(await screen.findByText(/macro-zona MZ-2 está bloqueada/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Reanudar' })).toBeNull();
    await getRepository().releaseManualLock(zona.id);
  });
});
