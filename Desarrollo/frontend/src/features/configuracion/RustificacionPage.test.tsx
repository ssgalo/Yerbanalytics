// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { RustificacionPage } from './RustificacionPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { getRepository } from '@/data';
import { ConPermisos } from '@/test/sesion';
import { todayIso } from '@/lib/horario';
import type { Rol } from '@/types/seguridad';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const montar = (rol: Rol = 'INGENIERO_AGRONOMO') =>
  render(
    <MemoryRouter>
      <ConPermisos rol={rol}>
        <NurseryProvider>
          <PageMetaProvider>
            <RustificacionPage />
          </PageMetaProvider>
        </NurseryProvider>
      </ConPermisos>
    </MemoryRouter>,
  );

const guardar = () => screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement;

describe('Rustificación (RustificacionPage)', () => {
  it('edita el horario por defecto de la mediasombra y bloquea un cierre anterior a la apertura', async () => {
    montar();
    const desde = (await screen.findByLabelText('Abierta desde')) as HTMLInputElement;
    const hasta = screen.getByLabelText('Abierta hasta') as HTMLInputElement;
    expect([desde.value, hasta.value]).toEqual(['08:00', '17:00']);

    fireEvent.change(hasta, { target: { value: '07:00' } });

    expect(screen.getByRole('alert').textContent).toMatch(/cierre debe ser posterior/);
    expect(guardar().disabled).toBe(true);
  });

  it('un horario válido se guarda en la misma configuración', async () => {
    const save = vi.spyOn(getRepository(), 'saveConfig');
    montar();
    const hasta = (await screen.findByLabelText('Abierta hasta')) as HTMLInputElement;

    fireEvent.change(hasta, { target: { value: '16:00' } });
    fireEvent.click(guardar());

    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(save.mock.calls[0][0].operativa).toMatchObject({
      mediasombraAbiertaDesde: '08:00',
      mediasombraAbiertaHasta: '16:00',
    });
  });

  it('lista las macro-zonas con su plan o "sin plan" y un enlace a su detalle', async () => {
    const repo = getRepository();
    const { zonas } = await repo.getNursery();
    const conPlan = zonas[1];
    await repo.iniciarPlanRustificacion(conPlan.id, {
      fechaInicio: todayIso(),
      etapas: [
        { dias: 7, abiertaDesde: '10:00', abiertaHasta: '14:00' },
        { dias: 7, abiertaDesde: '09:00', abiertaHasta: '16:00' },
      ],
    });
    montar();

    const lista = await screen.findByRole('list', { name: 'Plan de cada macro-zona' });
    const filas = await within(lista).findAllByRole('listitem');
    expect(filas).toHaveLength(zonas.length);

    const fila = (id: string) =>
      filas.find((f) => within(f).queryByRole('link', { name: new RegExp(id) }))!;
    await waitFor(() => expect(fila(conPlan.id).textContent).toMatch(/Etapa 1 de 2/));
    const sinPlan = fila(zonas[0].id);
    await waitFor(() =>
      expect(sinPlan.textContent).toMatch(/Sin plan: rige el horario por defecto/),
    );
    expect(within(fila(conPlan.id)).getByRole('link').getAttribute('href')).toBe(
      `/mapa?zona=${conPlan.id}`,
    );
  });

  it('sin configuracion.editar: campos deshabilitados y sin guardar', async () => {
    montar('PRODUCTOR_VIVERISTA');

    const desde = await screen.findByLabelText('Abierta desde');
    expect(desde.matches(':disabled')).toBe(true);
    expect(screen.queryByRole('button', { name: /Guardar cambios/ })).toBeNull();
  });
});
