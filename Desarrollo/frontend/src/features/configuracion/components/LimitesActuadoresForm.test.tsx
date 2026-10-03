// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { LimitesActuadoresForm } from './LimitesActuadoresForm';
import { buildConfig } from '@/data/mock/config';

afterEach(cleanup);

const montar = () =>
  render(
    <MemoryRouter>
      <LimitesActuadoresForm value={buildConfig().operativa} onChange={() => undefined} />
    </MemoryRouter>,
  );

describe('LimitesActuadoresForm', () => {
  it('ya no edita el tiempo de apertura de riego ni la apertura de mediasombra', () => {
    montar();

    expect(screen.queryByLabelText(/Tiempo máx\. de apertura de riego/)).toBeNull();
    expect(screen.queryByLabelText(/Apertura máx\. de mediasombra/)).toBeNull();
  });

  it('manda a "Motor de reglas" para editar esos umbrales, sin duplicar la edición', () => {
    montar();

    const enlace = screen.getByRole('link', { name: /Motor de reglas/ });
    expect(enlace.getAttribute('href')).toBe('/reglas');
  });

  it('ya no muestra el volumen máx. diario de riego (los límites diarios se eliminaron)', () => {
    montar();

    expect(screen.queryByLabelText(/Volumen máx\. diario de riego/)).toBeNull();
    expect(screen.queryByText(/ml\/día/)).toBeNull();
  });

  it('rotula la dosis máx. de insumo como no interviniente en las decisiones del motor', () => {
    montar();

    expect(screen.getByLabelText(/Dosis máx\. de insumo/)).toBeTruthy();
    expect(screen.getAllByText(/no intervienen en decisiones del motor/i).length).toBeGreaterThanOrEqual(1);
  });
});
