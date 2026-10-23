// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { HardeningCard } from './HardeningCard';
import { getRepository, PlanRustificacionError } from '@/data';
import { ConPermisos } from '@/test/sesion';
import type { Rol } from '@/types/seguridad';
import type { PlanRustificacion, Zona } from '@/types/domain';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const zona = (id: string) => ({ id, name: `Macro-zona ${id.slice(3)}` }) as Zona;

const montar = (id: string, rol: Rol = 'INGENIERO_AGRONOMO') =>
  render(
    <ConPermisos rol={rol}>
      <HardeningCard zona={zona(id)} />
    </ConPermisos>,
  );

describe('HardeningCard', () => {
  it('sin plan muestra el horario por defecto y "Iniciar plan"', async () => {
    montar('MZ-1');

    expect(await screen.findByText('Sin plan de rustificación')).toBeTruthy();
    await waitFor(() => expect(screen.getByText(/abierta de 08:00 a 17:00/)).toBeTruthy());
    expect((screen.getByRole('button', { name: 'Iniciar plan' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('"Iniciar plan" abre el formulario y "Cancelar" lo cierra sin crear nada', async () => {
    const iniciar = vi.spyOn(getRepository(), 'iniciarPlanRustificacion');
    montar('MZ-1');
    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar plan' }));

    const form = screen.getByRole('form', { name: /Nuevo plan de rustificación/ });
    expect(within(form).getByLabelText('Fecha de inicio')).toBeTruthy();
    expect(within(form).getAllByLabelText(/Duración en días/)).toHaveLength(4);
    expect(within(form).queryByText(/%/)).toBeNull(); // sin porcentaje de apertura

    fireEvent.click(within(form).getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('form')).toBeNull();
    expect(screen.getByText('Sin plan de rustificación')).toBeTruthy();
    expect(iniciar).not.toHaveBeenCalled();
  });

  it('inicia el plan con la fecha y las etapas elegidas', async () => {
    // MZ-6: el mock guarda el plan y el resto de los tests usa MZ-1 sin plan.
    const iniciar = vi.spyOn(getRepository(), 'iniciarPlanRustificacion');
    montar('MZ-6');
    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar plan' }));
    fireEvent.change(screen.getByLabelText('Etapa 1 · Duración en días'), { target: { value: '5' } });
    fireEvent.change(screen.getByLabelText('Etapa 1 · Abierta hasta'), { target: { value: '12:30' } });

    fireEvent.click(within(screen.getByRole('form')).getByRole('button', { name: 'Iniciar plan' }));

    await waitFor(() => expect(iniciar).toHaveBeenCalledTimes(1));
    const [zonaId, plan] = iniciar.mock.calls[0];
    expect(zonaId).toBe('MZ-6');
    expect(plan.etapas[0]).toEqual({ dias: 5, abiertaDesde: '10:00', abiertaHasta: '12:30' });
    // Empieza hoy (la fecha por defecto): ya está en curso.
    expect(await screen.findByText('En curso')).toBeTruthy();
  });

  it('un horario inválido bloquea el inicio', async () => {
    montar('MZ-1');
    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar plan' }));

    fireEvent.change(screen.getByLabelText('Etapa 2 · Abierta hasta'), { target: { value: '08:00' } });

    const form = screen.getByRole('form');
    expect(within(form).getByText(/Etapa 2: la hora de cierre debe ser posterior/)).toBeTruthy();
    expect((within(form).getByRole('button', { name: 'Iniciar plan' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('muestra el rechazo del backend tal cual', async () => {
    vi.spyOn(getRepository(), 'iniciarPlanRustificacion').mockRejectedValue(
      new PlanRustificacionError('La macro-zona ya tiene un plan de rustificación vigente.'),
    );
    montar('MZ-1');
    fireEvent.click(await screen.findByRole('button', { name: 'Iniciar plan' }));

    fireEvent.click(within(screen.getByRole('form')).getByRole('button', { name: 'Iniciar plan' }));

    expect((await screen.findByRole('alert')).textContent).toContain('ya tiene un plan de rustificación vigente');
  });

  it('con un plan en curso muestra la etapa de hoy y se puede eliminar, con confirmación', async () => {
    // Sin pasar por el mock: el plan demo de MZ-3 lo usan otros tests.
    const cancelar = vi
      .spyOn(getRepository(), 'cancelarPlanRustificacion')
      .mockResolvedValue({} as PlanRustificacion);
    montar('MZ-3'); // la demo arranca con un plan en curso en MZ-3

    expect(await screen.findByText('En curso')).toBeTruthy();
    expect(screen.getByText(/Hoy, abierta de/)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Eliminar plan' }));
    expect(screen.getByText(/vuelve desde ahora al horario por defecto/)).toBeTruthy();
    expect(cancelar).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Sí, eliminar plan' }));

    await waitFor(() => expect(cancelar).toHaveBeenCalledWith('MZ-3'));
    expect(await screen.findByText('Sin plan de rustificación')).toBeTruthy();
  });

  it('sin configuracion.editar no puede iniciar ni eliminar', async () => {
    montar('MZ-1', 'OPERARIO');
    expect(((await screen.findByRole('button', { name: 'Iniciar plan' })) as HTMLButtonElement).disabled).toBe(true);
    cleanup();

    montar('MZ-3', 'OPERARIO');
    expect(await screen.findByText('En curso')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Eliminar plan' })).toBeNull();
  });
});
