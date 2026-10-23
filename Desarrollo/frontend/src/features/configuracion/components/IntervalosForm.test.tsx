// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntervalosForm } from './IntervalosForm';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa } from '@/types/domain';

afterEach(cleanup);

const mount = (patch: Partial<ConfigOperativa> = {}, onChange = vi.fn()) => {
  render(<IntervalosForm value={{ ...buildConfig().operativa, ...patch }} onChange={onChange} />);
  return onChange;
};

const field = () => screen.getByLabelText('Intervalo de sensado y evaluación') as HTMLInputElement;
const minutos = () => fireEvent.click(screen.getByRole('button', { name: 'Minutos' }));

describe('IntervalosForm · pulso de sensado y evaluación', () => {
  it('es el único campo: sin intervalo de evaluación ni pedido de lectura en segundos', () => {
    mount();
    expect(screen.getAllByRole('spinbutton')).toHaveLength(1);
    expect(screen.queryByLabelText(/Intervalo de Evaluación/i)).toBeNull();
    expect(screen.queryByLabelText(/segundos/i)).toBeNull();
  });

  it('muestra el valor guardado (240 min = 4 h)', () => {
    mount();
    expect(field().value).toBe('4');
    minutos();
    expect(field().value).toBe('240');
  });

  it('la ayuda explica el pulso anclado a las 02:00', () => {
    mount();
    expect(screen.getByText(/anclada a las 02:00.*macro-zonas/)).toBeTruthy();
  });

  it('emite el valor en minutos', () => {
    const onChange = mount();
    minutos();
    fireEvent.change(field(), { target: { value: '180' } });
    expect(onChange).toHaveBeenCalledWith({ intervaloSensadoMinutos: 180 });
  });

  it.each([30, 400])('marca %s min como inválido', (v) => {
    mount({ intervaloSensadoMinutos: v });
    expect(field().getAttribute('aria-invalid')).toBe('true');
  });

  it.each([60, 360])('acepta %s min', (v) => {
    mount({ intervaloSensadoMinutos: v });
    expect(field().getAttribute('aria-invalid')).not.toBe('true');
  });

  it('muestra el umbral de lectura vieja que informó el backend', () => {
    mount({ effectiveStaleThresholdSeconds: 14580 });
    expect(screen.getByText(/lectura vieja.*14580 s/)).toBeTruthy();
  });

  it('sin umbral del backend no lo inventa', () => {
    mount({ effectiveStaleThresholdSeconds: undefined });
    expect(screen.queryByText(/umbral de lectura vieja/i)).toBeNull();
  });
});
