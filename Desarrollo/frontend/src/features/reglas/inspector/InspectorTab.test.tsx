// @vitest-environment jsdom
import '@/test/reactFlowJsdom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { InspectorTab } from './InspectorTab';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { MockRepository } from '@/data/mock/mockRepository';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

function montar(props: Partial<React.ComponentProps<typeof InspectorTab>> = {}) {
  const onEditarRegla = vi.fn();
  const onSectorChange = vi.fn();
  render(
    <MemoryRouter>
      <NurseryProvider>
        <InspectorTab sectorInicial={null} onEditarRegla={onEditarRegla} onSectorChange={onSectorChange} {...props} />
      </NurseryProvider>
    </MemoryRouter>,
  );
  return { onEditarRegla, onSectorChange };
}

const nodo = (id: string) => document.querySelector(`.react-flow__node[data-id="${id}"]`) as HTMLElement;

describe('InspectorTab · selección (8.4)', () => {
  it('ofrece zona, sector y origen, y un botón Actualizar', async () => {
    montar();

    expect(await screen.findByLabelText('Macro-zona')).toBeTruthy();
    expect(screen.getByLabelText('Sector')).toBeTruthy();
    expect(screen.getByLabelText('Origen')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Actualizar' })).toBeTruthy();
    expect(screen.getByLabelText(/Actualizar cada 5 s/)).toBeTruthy();
  });

  it('respeta ?sector=: el sector y su zona ya vienen elegidos', async () => {
    montar({ sectorInicial: 'MZ-2-006' });

    const sector = (await screen.findByLabelText('Sector')) as HTMLSelectElement;
    expect(sector.value).toBe('MZ-2-006');
    expect((screen.getByLabelText('Macro-zona') as HTMLSelectElement).value).toBe('MZ-2');
  });

  it('un sector inexistente cae al primero en vez de romper', async () => {
    montar({ sectorInicial: 'NOPE-9' });

    const sector = (await screen.findByLabelText('Sector')) as HTMLSelectElement;
    expect(sector.value).toBe('MZ-1-001');
  });

  it('cambiar de zona elige el primer sector de esa zona y avisa', async () => {
    const { onSectorChange } = montar();

    fireEvent.change(await screen.findByLabelText('Macro-zona'), { target: { value: 'MZ-3' } });

    expect((screen.getByLabelText('Sector') as HTMLSelectElement).value).toBe('MZ-3-001');
    expect(onSectorChange).toHaveBeenLastCalledWith('MZ-3-001');
  });

  it('muestra el DAG con la traza del sector elegido', async () => {
    montar({ sectorInicial: 'MZ-1-001' });

    await waitFor(() => expect(nodo('RiegoPorDeficitRule')).toBeTruthy());
    expect(nodo('RiegoPorDeficitRule').querySelector('[data-estado]')).toBeTruthy();
    expect(within(nodo('RiegoPorDeficitRule')).getByTestId('comparacion-0').textContent).toMatch(/%/);
  });

  it('resume de un vistazo qué pasó', async () => {
    montar({ sectorInicial: 'MZ-1-001' });

    expect(await screen.findByRole('heading', { name: /Qué pasó en la última evaluación/ })).toBeTruthy();
    expect(screen.getByText(/Telemetría · .* · parámetros #/)).toBeTruthy();
  });

  it('el barrido muestra su propia traza', async () => {
    montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(nodo('RiegoPorDeficitRule')).toBeTruthy());

    fireEvent.change(screen.getByLabelText('Origen'), { target: { value: 'BARRIDO' } });

    await waitFor(() =>
      expect(nodo('StaleSensorRule').querySelector('[data-estado="bloqueo"]')).toBeTruthy(),
    );
  });

  it('sin traza dice "sin evaluaciones desde el último arranque" y no dibuja el DAG', async () => {
    vi.spyOn(MockRepository.prototype, 'getTrazaEvaluacion').mockResolvedValue(null);
    montar({ sectorInicial: 'MZ-1-001' });

    expect(await screen.findByText(/sin evaluaciones desde el último arranque/i)).toBeTruthy();
    expect(nodo('RiegoPorDeficitRule')).toBeNull();
  });

  it('un error al pedir la traza se muestra', async () => {
    vi.spyOn(MockRepository.prototype, 'getTrazaEvaluacion').mockRejectedValue(new Error('Error 500 al obtener la evaluación'));
    montar({ sectorInicial: 'MZ-1-001' });

    expect(await screen.findByText(/Error 500/)).toBeTruthy();
  });

  it('Actualizar vuelve a pedir la traza', async () => {
    const espia = vi.spyOn(MockRepository.prototype, 'getTrazaEvaluacion');
    montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(espia).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole('button', { name: 'Actualizar' }));

    await waitFor(() => expect(espia).toHaveBeenCalledTimes(2));
  });
});

describe('InspectorTab · detalle de un nodo (8.5)', () => {
  it('el clic en un nodo abre el panel con todas las comparaciones, la acción y el motivo', async () => {
    montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(nodo('RiegoPorDeficitRule')).toBeTruthy());

    fireEvent.click(nodo('RiegoPorDeficitRule'));

    const panel = await screen.findByRole('complementary', { name: /Detalle de .*Riego/ });
    expect(within(panel).getAllByTestId('fila-comparacion').length).toBeGreaterThanOrEqual(1);
    expect(within(panel).getByText(/Acción y motivo/)).toBeTruthy();
    expect(within(panel).getByText('riego.umbral-humedad')).toBeTruthy();
  });

  it('"Editar parámetro" lleva a Parámetros con esa regla abierta', async () => {
    const { onEditarRegla } = montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(nodo('RiegoPorDeficitRule')).toBeTruthy());
    fireEvent.click(nodo('RiegoPorDeficitRule'));

    fireEvent.click(await screen.findByRole('button', { name: /Editar parámetro/ }));

    expect(onEditarRegla).toHaveBeenCalledWith('RiegoPorDeficitRule');
  });

  it('una regla sin parámetros no ofrece "Editar parámetro"', async () => {
    montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(nodo('ManualLockRule')).toBeTruthy());

    fireEvent.click(nodo('ManualLockRule'));

    await screen.findByRole('complementary');
    expect(screen.queryByRole('button', { name: /Editar parámetro/ })).toBeNull();
  });

  it('al cambiar de sector se cierra el panel', async () => {
    montar({ sectorInicial: 'MZ-1-001' });
    await waitFor(() => expect(nodo('RiegoPorDeficitRule')).toBeTruthy());
    fireEvent.click(nodo('RiegoPorDeficitRule'));
    await screen.findByRole('complementary');

    fireEvent.change(screen.getByLabelText('Sector'), { target: { value: 'MZ-1-002' } });

    await waitFor(() => expect(screen.queryByRole('complementary')).toBeNull());
  });
});
