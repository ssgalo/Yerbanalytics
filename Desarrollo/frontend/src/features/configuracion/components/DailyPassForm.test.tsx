// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { DailyPassForm } from './DailyPassForm';
import { buildConfig } from '@/data/mock/config';
import type { ConfigOperativa } from '@/types/domain';

afterEach(cleanup);

const mount = (patch: Partial<ConfigOperativa> = {}, onChange = vi.fn()) => {
  render(<DailyPassForm value={{ ...buildConfig().operativa, ...patch }} onChange={onChange} />);
  return onChange;
};

const sw = () => screen.getByRole('switch', { name: 'Pasada diaria activa' });
const hour = () => screen.getByLabelText('Hora de la pasada') as HTMLInputElement;

describe('DailyPassForm · pasada diaria del riel', () => {
  it('de fábrica, apagada a las 11:00', () => {
    mount();
    expect(sw().getAttribute('aria-checked')).toBe('false');
    expect(hour().value).toBe('11:00');
  });

  it('la ayuda dice que fotografía el recorrido una vez al día y que se pide desde Demo Expo', () => {
    mount();
    expect(screen.getByText(/fotografía los sectores del recorrido una vez al día.*Demo Expo/)).toBeTruthy();
  });

  it('el interruptor emite pasadaDiariaActiva', () => {
    const onChange = mount();
    fireEvent.click(sw());
    expect(onChange).toHaveBeenCalledWith({ pasadaDiariaActiva: true });
  });

  it('la hora se edita aunque la pasada esté desactivada', () => {
    const onChange = mount({ pasadaDiariaActiva: false });
    expect(hour().disabled).toBe(false);
    fireEvent.change(hour(), { target: { value: '10:30' } });
    expect(onChange).toHaveBeenCalledWith({ pasadaDiariaHora: '10:30' });
  });

  it.each(['', '24:00', '9:5'])('la hora «%s» es inválida', (v) => {
    mount({ pasadaDiariaHora: v });
    expect(hour().getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByRole('alert').textContent).toMatch(/00:00 y 23:59/);
  });
});
