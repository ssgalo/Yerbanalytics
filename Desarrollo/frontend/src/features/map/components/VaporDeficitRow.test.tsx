// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { VaporDeficitRow } from './VaporDeficitRow';

afterEach(cleanup);

describe('VaporDeficitRow', () => {
  it('calor seco: muestra el valor en kPa y la etiqueta del tramo', () => {
    render(<VaporDeficitRow dpv={{ value: 2.2, band: 'DRY_HEAT', label: 'calor seco' }} />);

    expect(screen.getByText('Déficit de presión de vapor')).toBeTruthy();
    expect(screen.getByText('2,20')).toBeTruthy();
    expect(screen.getByText('kPa')).toBeTruthy();
    expect(screen.getByText('calor seco')).toBeTruthy();
  });

  it('sin dato: muestra «—» y «sin dato», nunca un cero', () => {
    render(<VaporDeficitRow dpv={null} />);

    expect(screen.getByText('—')).toBeTruthy();
    expect(screen.getByText('sin dato')).toBeTruthy();
    expect(screen.queryByText(/0,00/)).toBeNull();
  });

  it('es informativo: no es un botón que abra un histórico', () => {
    render(<VaporDeficitRow dpv={{ value: 1.0, band: 'COMFORTABLE', label: 'confortable' }} />);

    expect(screen.queryByRole('button')).toBeNull();
  });
});
