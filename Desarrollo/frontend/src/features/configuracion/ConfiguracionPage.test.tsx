// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ConfiguracionPage } from './ConfiguracionPage';
import { PageMetaProvider } from '@/hooks/PageMeta';
import { getRepository } from '@/data';

beforeEach(() => vi.stubEnv('VITE_DATA_SOURCE', 'mock'));
afterEach(() => {
  cleanup();
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

  it('valida el plan de rustificación contra la apertura máxima del catálogo del motor', async () => {
    const repo = getRepository();
    await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: '50' }]);
    try {
      montar();

      // El plan de fábrica llega a 100 %: contra el tope de 50 % del catálogo es inválido.
      await waitFor(() => expect(screen.getByText(/apertura máxima \(50 %\)/)).toBeTruthy());
      expect((screen.getByRole('button', { name: /Guardar cambios/ }) as HTMLButtonElement).disabled).toBe(true);
    } finally {
      await repo.saveParametros([{ clave: 'mediasombra.apertura-maxima', valor: null }]);
    }
  });
});
