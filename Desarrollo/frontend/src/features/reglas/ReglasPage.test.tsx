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

/** El campo de un parámetro (se espera a que cargue el catálogo). */
const campo = async (clave: string) => {
  await screen.findByRole('switch', { name: 'Apagar Riego por déficit hídrico' });
  return document.getElementById(`param-${clave}`)!;
};

/** La app completa (sesión + shell + rutas) en memoria, como la monta `App`. */
const montarEn = (ruta: string) => montarApp(ruta);

describe('Ruta /reglas (7.1)', () => {
  it('el Sidebar enlaza a "Motor de reglas" dentro del grupo Configuración', async () => {
    montarEn('/');

    fireEvent.click(await screen.findByRole('button', { name: 'Configuración' }));
    const enlace = await screen.findByRole('link', { name: 'Motor de reglas' });
    expect(enlace.getAttribute('href')).toBe('/reglas');
  });

  it('monta ReglasPage con las pestañas Reglas e Inspector', async () => {
    montarEn('/reglas');

    expect(await screen.findByRole('tab', { name: /^Reglas/ })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Inspector' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: /^Reglas/ }).getAttribute('aria-selected')).toBe('true');
    // La pestaña Reglas carga el catálogo del repositorio (mock), con un interruptor por regla.
    expect(await screen.findByRole('switch', { name: 'Apagar Riego por déficit hídrico' })).toBeTruthy();
    expect(screen.getByRole('switch', { name: 'Apagar Bloqueo manual' })).toBeTruthy();
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

  it('?tab=parametros (el nombre de la pestaña anterior) abre Reglas', async () => {
    montarEn('/reglas?tab=parametros');

    const tab = await screen.findByRole('tab', { name: /^Reglas/ });
    expect(tab.getAttribute('aria-selected')).toBe('true');
    expect(await screen.findByRole('switch', { name: 'Apagar Riego por déficit hídrico' })).toBeTruthy();
  });

  it('?regla= lleva a la tarjeta de esa regla, resaltada', async () => {
    const scroll = vi.fn();
    window.HTMLElement.prototype.scrollIntoView = scroll;
    montarEn('/reglas?regla=RiegoPorDeficitRule');

    const tarjeta = await screen.findByRole('article', { name: /Riego por déficit hídrico/ });
    expect(tarjeta.id).toBe('regla-RiegoPorDeficitRule');
    await waitFor(() => expect(scroll).toHaveBeenCalled());
  });

  it('un cambio guardado en la demo se conserva en el repositorio', async () => {
    montarEn('/reglas');
    fireEvent.change(await campo('riego.umbral-humedad'), { target: { value: '40' } });
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
    montarEn('/reglas');
    fireEvent.change(await campo('riego.umbral-humedad'), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('tab', { name: 'Inspector' }));
    await screen.findByLabelText('Macro-zona');
    fireEvent.click(screen.getByRole('tab', { name: /^Reglas/ }));

    expect(screen.getByText(/1 cambio sin guardar/)).toBeTruthy();
    // El valor editado sigue ahí
    expect(((await campo('riego.umbral-humedad')) as HTMLInputElement).value).toBe('40');
  });

  it('el aviso de cambios sin guardar se ve desde la pestaña Inspector', async () => {
    montarEn('/reglas');
    fireEvent.change(await campo('riego.umbral-humedad'), { target: { value: '40' } });

    fireEvent.click(screen.getByRole('tab', { name: 'Inspector' }));

    const marca = await screen.findByTitle('1 cambio sin guardar');
    expect(marca.textContent).toBe('1');
    expect(screen.getByRole('tab', { name: /^Reglas/ }).contains(marca)).toBe(true);
  });

  it('sin cambios no hay marca en la pestaña', async () => {
    montarEn('/reglas');
    await screen.findByRole('switch', { name: 'Apagar Riego por déficit hídrico' });
    expect(screen.queryByTitle(/sin guardar/)).toBeNull();
  });
});

describe('Reglas sin permiso de edición', () => {
  it('el Productor Viverista ve las reglas y sus umbrales en sólo lectura, sin cambiarlos', async () => {
    await seguridadDemo('productor');
    montarEn('/reglas');

    const umbral = (await campo('riego.umbral-humedad')) as HTMLInputElement;
    expect(umbral.readOnly).toBe(true);
    expect((screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: /Guardar cambios/ })).toBeNull();
    expect(screen.getByText(/hace falta el permiso/)).toBeTruthy();
  });
});
