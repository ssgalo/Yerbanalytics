// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { NurseryProvider } from '@/hooks/NurseryContext';
import { routes } from '@/router';
import { getRepository } from '@/data';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

/** La app completa (shell + rutas) en memoria, como la monta `App`. */
function montarEn(ruta: string) {
  const router = createMemoryRouter(routes, {
    initialEntries: [ruta],
    future: { v7_relativeSplatPath: true },
  });
  render(
    <NurseryProvider>
      <RouterProvider router={router} future={{ v7_startTransition: true }} />
    </NurseryProvider>,
  );
  return router;
}

describe('Ruta /reglas (7.1)', () => {
  it('el Sidebar enlaza a "Motor de reglas"', async () => {
    montarEn('/');

    const enlace = await screen.findByRole('link', { name: 'Motor de reglas' });
    expect(enlace.getAttribute('href')).toBe('/reglas');
  });

  it('monta ReglasPage con las pestañas Parámetros e Inspector', async () => {
    montarEn('/reglas');

    expect(await screen.findByRole('tab', { name: 'Parámetros' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Inspector' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Parámetros' }).getAttribute('aria-selected')).toBe('true');
    // La pestaña Parámetros carga el catálogo del repositorio (mock): las 9 reglas
    expect(await screen.findByText('💦 Riego')).toBeTruthy();
    expect(screen.getByText('🔒 Bloqueo manual')).toBeTruthy();
  });

  it('el título de la topbar es "Motor de reglas"', async () => {
    montarEn('/reglas');
    expect(await screen.findByRole('heading', { level: 1, name: 'Motor de reglas' })).toBeTruthy();
  });

  it('?tab=inspector abre el Inspector y ?sector llega al selector', async () => {
    montarEn('/reglas?tab=inspector&sector=MZ-1-001');

    const tab = await screen.findByRole('tab', { name: 'Inspector' });
    expect(tab.getAttribute('aria-selected')).toBe('true');
  });

  it('cambiar de pestaña actualiza la URL', async () => {
    const router = montarEn('/reglas');

    fireEvent.click(await screen.findByRole('tab', { name: 'Inspector' }));

    await waitFor(() => expect(router.state.location.search).toContain('tab=inspector'));
  });

  it('?regla= abre esa regla en Parámetros', async () => {
    montarEn('/reglas?regla=IrrigationRule');

    expect(await screen.findByLabelText(/Umbral de riego/)).toBeTruthy();
  });

  it('un cambio guardado en la demo se conserva en el repositorio', async () => {
    montarEn('/reglas?regla=IrrigationRule');
    fireEvent.change(await screen.findByLabelText(/Umbral de riego/), { target: { value: '40' } });
    fireEvent.click(screen.getByRole('button', { name: /Guardar cambios/ }));

    await waitFor(async () => {
      const c = await getRepository().getCatalogoReglas();
      expect(c.parametros.find((p) => p.clave === 'riego.umbral-humedad')!.valor).toBe('40');
    });
    await getRepository().saveParametros([{ clave: 'riego.umbral-humedad', valor: null }]);
  });
});
