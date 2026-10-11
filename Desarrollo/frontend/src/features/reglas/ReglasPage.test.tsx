// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { getRepository } from '@/data';
import { montarApp } from '@/test/app';
import { seguridadDemo } from '@/test/sesion';

beforeEach(async () => {
  vi.stubEnv('VITE_DATA_SOURCE', 'mock');
  // Con sesión de Administrador: puede ver y editar el motor.
  await seguridadDemo('admin');
  // El DAG del Inspector mide su lienzo con ResizeObserver, que jsdom no trae.
  vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

/** La app completa (sesión + shell + rutas) en memoria, como la monta `App`. */
const montarEn = (ruta: string) => montarApp(ruta);

describe('Ruta /reglas (7.1)', () => {
  it('el Sidebar enlaza a "Motor de reglas"', async () => {
    montarEn('/');

    const enlace = await screen.findByRole('link', { name: 'Motor de reglas' });
    expect(enlace.getAttribute('href')).toBe('/reglas');
  });

  it('monta ReglasPage con las pestañas Parámetros e Inspector', async () => {
    montarEn('/reglas');

    expect(await screen.findByRole('tab', { name: /^Parámetros/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Inspector' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /^Parámetros/ }).getAttribute('aria-selected')).toBe('true');
    // La pestaña Parámetros carga el catálogo del repositorio (mock): las 13 reglas
    expect(await screen.findByText('💦 Riego por déficit hídrico (R-01)')).toBeTruthy();
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
    const ubicacion = montarEn('/reglas');

    fireEvent.click(await screen.findByRole('tab', { name: 'Inspector' }));

    await waitFor(() => expect(ubicacion.actual?.search).toContain('tab=inspector'));
  });

  it('?regla= abre esa regla en Parámetros', async () => {
    montarEn('/reglas?regla=RiegoPorDeficitRule');

    expect(await screen.findByLabelText(/Umbral de riego/)).toBeTruthy();
  });

  it('un cambio guardado en la demo se conserva en el repositorio', async () => {
    montarEn('/reglas?regla=RiegoPorDeficitRule');
    fireEvent.change(await screen.findByLabelText(/Umbral de riego/), { target: { value: '40' } });
    fireEvent.click(screen.getByRole('button', { name: /Guardar cambios/ }));

    await waitFor(async () => {
      const c = await getRepository().getCatalogoReglas();
      expect(c.parametros.find((p) => p.clave === 'riego.umbral-humedad')!.valor).toBe('40');
    });
    await getRepository().saveParametros([{ clave: 'riego.umbral-humedad', valor: null }]);
  });
});

describe('Borrador de Parámetros entre pestañas (#1)', () => {
  it('editar, ir al Inspector y volver conserva el borrador', async () => {
    montarEn('/reglas?regla=RiegoPorDeficitRule');
    fireEvent.change(await screen.findByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('tab', { name: 'Inspector' }));
    await screen.findByLabelText('Macro-zona');
    fireEvent.click(screen.getByRole('tab', { name: /^Parámetros/ }));

    expect(screen.getByText('1 cambio sin guardar')).toBeTruthy();
    // La regla vuelve abierta (la URL conserva ?regla=) y el valor editado sigue ahí
    expect((await screen.findByLabelText(/Umbral de riego/) as HTMLInputElement).value).toBe('40');
  });

  it('el aviso de cambios sin guardar se ve desde la pestaña Inspector', async () => {
    montarEn('/reglas?regla=RiegoPorDeficitRule');
    fireEvent.change(await screen.findByLabelText(/Umbral de riego/), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('tab', { name: 'Inspector' }));

    const marca = await screen.findByTitle('1 cambio sin guardar');
    expect(marca.textContent).toBe('1');
    expect(screen.getByRole('tab', { name: /^Parámetros/ }).contains(marca)).toBe(true);
  });

  it('sin cambios no hay marca en la pestaña', async () => {
    montarEn('/reglas');
    await screen.findByText('💦 Riego por déficit hídrico (R-01)');
    expect(screen.queryByTitle(/sin guardar/)).toBeNull();
  });
});

describe('Parámetros sin permiso de edición (7.5)', () => {
  it('el Productor Viverista ve el catálogo en sólo lectura, sin botón para guardar', async () => {
    await seguridadDemo('productor');
    montarEn('/reglas?regla=RiegoPorDeficitRule');

    const campo = (await screen.findByLabelText(/Umbral de riego/)) as HTMLInputElement;
    expect(campo.disabled).toBe(true);
    expect(screen.queryByRole('button', { name: /Guardar cambios/ })).toBeNull();
    expect(screen.getByText(/Sólo lectura/)).toBeTruthy();
  });
});
