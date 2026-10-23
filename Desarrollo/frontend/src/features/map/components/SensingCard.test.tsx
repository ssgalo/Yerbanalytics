// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, within } from '@testing-library/react';
import { montarApp } from '@/test/app';
import { seguridadDemo } from '@/test/sesion';

beforeEach(async () => {
  // jsdom no trae ResizeObserver; lo usa la grilla desplazable de sectores
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  await seguridadDemo('agronomo');
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

/** Las filas de métricas de "Valores sensados" (abren el modal). */
async function filasDeMetricas() {
  await screen.findByRole('heading', { name: 'Valores sensados' });
  return screen.getAllByRole('button').filter((b) => b.getAttribute('aria-haspopup') === 'dialog');
}

describe('SensingCard · detalle en modal', () => {
  it('abre la métrica en un modal, recorre las vecinas y se cierra con Escape', async () => {
    montarApp('/mapa?zona=MZ-2');
    const filas = await filasDeMetricas();

    fireEvent.click(filas[0]);
    const panel = screen.getByRole('dialog');
    const primera = within(panel).getByRole('heading', { level: 2 }).textContent;
    expect(within(panel).getByText('Histórico del nodo testigo')).toBeTruthy();

    // La primera no tiene anterior; "siguiente" pasa a otra métrica sin cerrar el modal
    const [anterior, siguiente] = within(panel).getAllByRole('button').slice(-2);
    expect((anterior as HTMLButtonElement).disabled).toBe(true);
    fireEvent.click(siguiente);
    expect(within(screen.getByRole('dialog')).getByRole('heading', { level: 2 }).textContent).not.toBe(primera);

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
  });
});

describe('SensingCard · DPV', () => {
  it('muestra el déficit de presión de vapor de la zona, derivado de su temperatura y humedad', async () => {
    montarApp('/mapa?zona=MZ-2');
    await screen.findByRole('heading', { name: 'Valores sensados' });

    const grupo = screen.getByRole('region', { name: 'Derivada, informativa' });
    expect(within(grupo).getByText('Déficit de presión de vapor')).toBeTruthy();
    expect(within(grupo).getByText('kPa')).toBeTruthy();
    // El DPV no se sensa: no es una fila que abra el histórico de una métrica
    expect(within(grupo).queryByRole('button')).toBeNull();
  });
});
