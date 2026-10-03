// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfiguracionPage } from './ConfiguracionPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { getRepository } from '@/data';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

const montar = () =>
  render(
    <MemoryRouter>
      <PageMetaProvider>
        <ConfiguracionPage />
      </PageMetaProvider>
    </MemoryRouter>,
  );

describe('ConfiguracionPage', () => {
  it('carga sin los dos límites mudados y con el enlace al motor de reglas', async () => {
    montar();

    await screen.findByText('Umbrales de métricas');
    expect(screen.queryByLabelText(/Tiempo máx\. de apertura de riego/)).toBeNull();
    expect(screen.getByRole('link', { name: /Motor de reglas/ })).toBeTruthy();
  });

  /** Ensucia el borrador con un cambio válido ajeno a la rustificación. */
  const ensuciar = async () => {
    const campo = (await screen.findByLabelText('Latencia de seguimiento')) as HTMLInputElement;
    fireEvent.change(campo, { target: { value: String(Number(campo.value) + 1) } });
  };
  const guardar = () => screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement;

  it('con un borrador sucio y válido, Guardar se habilita (control del test siguiente)', async () => {
    montar();
    await waitFor(() => expect(screen.queryByText(/Cargando la apertura máxima/)).toBeNull());

    await ensuciar();

    expect(guardar().disabled).toBe(false);
  });

  it('valida el plan de rustificación contra la apertura máxima del catálogo del motor', async () => {
    const repo = getRepository();
    await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: '50' }]);
    try {
      montar();
      await waitFor(() => expect(screen.getByText(/apertura máxima \(50 %\)/)).toBeTruthy());

      // El borrador está sucio y todo lo demás es válido: sólo la rustificación (100 % > 50 %)
      // puede deshabilitar Guardar. Si se quita esa validación, este test falla.
      await ensuciar();

      expect(guardar().disabled).toBe(true);
    } finally {
      await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: null }]);
    }
  });

  it('mientras el catálogo carga muestra el estado y no deja guardar contra un máximo supuesto', async () => {
    vi.spyOn(getRepository(), 'getCatalogoReglas').mockReturnValue(new Promise(() => undefined));
    montar();

    expect(await screen.findByText(/Cargando la apertura máxima/)).toBeTruthy();
    await ensuciar();

    expect(guardar().disabled).toBe(true);
  });

  it('si el catálogo falla avisa, bloquea Guardar y "Reintentar" lo vuelve a pedir', async () => {
    const espia = vi
      .spyOn(getRepository(), 'getCatalogoReglas')
      .mockRejectedValueOnce(new Error('Error 503 al leer los parámetros del motor'));
    montar();

    expect(await screen.findByText(/No se pudo obtener la apertura máxima/)).toBeTruthy();
    await ensuciar();
    expect(guardar().disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(screen.queryByText(/No se pudo obtener la apertura máxima/)).toBeNull());
    expect(espia).toHaveBeenCalledTimes(2);
    await waitFor(() => expect(guardar().disabled).toBe(false));
  });
});
