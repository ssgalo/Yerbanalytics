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

const R01 = 'RiegoPorDeficitRule';
const UMBRAL = 'riego.umbral-humedad';
const campo = () => document.getElementById(`param-${R01}-${UMBRAL}`) as HTMLInputElement;
const cargada = () => screen.findByRole('switch', { name: 'Apagar Riego por déficit hídrico' }, { timeout: 5000 });

describe('Ruta /reglas', () => {
  it('el Sidebar enlaza a "Motor de reglas" dentro de Configuración', async () => {
    montarApp('/');

    fireEvent.click(await screen.findByRole('button', { name: 'Configuración' }));
    expect((await screen.findByRole('link', { name: 'Motor de reglas' })).getAttribute('href')).toBe('/reglas');
    expect(screen.queryByRole('link', { name: 'Inspector del motor' })).toBeNull();
  });

  it('muestra las reglas, sin selector de pestañas', async () => {
    montarApp('/reglas');

    expect(await cargada()).toBeTruthy();
    expect(screen.getByRole('switch', { name: 'Apagar Bloqueo manual' })).toBeTruthy();
    expect(screen.queryByRole('tab')).toBeNull();
    expect(screen.getByRole('heading', { level: 1, name: 'Motor de reglas' })).toBeTruthy();
  });

  it('?regla= despliega la fila de esa regla', async () => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    montarApp(`/reglas?regla=${R01}`);

    await cargada();
    await waitFor(() => expect(campo()).toBeTruthy());
  });

  it('un cambio guardado en la demo se conserva en el repositorio, sólo en la copia de esa regla', async () => {
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    montarApp(`/reglas?regla=${R01}`);
    await cargada();
    await waitFor(() => expect(campo()).toBeTruthy());
    fireEvent.change(campo(), { target: { value: '40' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(async () => {
      const c = await getRepository().getCatalogoReglas();
      expect(c.parametros.find((p) => p.regla === R01 && p.clave === UMBRAL)!.valor).toBe('40');
      expect(c.parametros.find((p) => p.regla === 'PosponerPorLluviaRule' && p.clave === UMBRAL)!.valor).toBe('45');
    });
    await getRepository().saveParametros([{ regla: R01, clave: UMBRAL, valor: null }]);
  });
});

describe('Ruta /reglas/inspector', () => {
  it('monta el Inspector con su título', async () => {
    montarApp('/reglas/inspector');
    expect(await screen.findByLabelText('Macro-zona', {}, { timeout: 5000 })).toBeTruthy();
    expect(screen.getByRole('heading', { level: 1, name: 'Inspector del motor' })).toBeTruthy();
  });

  it('el enlace viejo ?tab=inspector&sector= redirige conservando el sector', async () => {
    const ubicacion = montarApp('/reglas?tab=inspector&sector=MZ-1-001');

    await waitFor(() => expect(ubicacion.actual?.pathname).toBe('/reglas/inspector'));
    expect(ubicacion.actual?.search).toBe('?sector=MZ-1-001');
    expect(await screen.findByLabelText('Macro-zona', {}, { timeout: 5000 })).toBeTruthy();
  });
});

describe('Reglas sin permiso de edición', () => {
  it('el Productor Viverista ve las reglas y sus valores en sólo lectura', async () => {
    await seguridadDemo('productor');
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    montarApp(`/reglas?regla=${R01}`);

    await cargada();
    await waitFor(() => expect(campo()).toBeTruthy());
    expect(campo().readOnly).toBe(true);
    expect((screen.getByRole('switch', { name: 'Apagar Riego por déficit hídrico' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    expect(screen.queryByRole('button', { name: 'Guardar' })).toBeNull();
    expect(screen.getByText(/hace falta el permiso/)).toBeTruthy();
  });
});
