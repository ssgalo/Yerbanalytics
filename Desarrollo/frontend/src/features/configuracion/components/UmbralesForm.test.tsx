// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { UmbralesForm } from './UmbralesForm';
import { buildConfig } from '@/data/mock/config';
import type { MetricThreshold } from '@/types/domain';

afterEach(cleanup);

/** Humedad ambiental de fábrica: crítico 52/91, alerta 57/88, ideal 62/84. */
const humAmb = (patch: Partial<MetricThreshold> = {}) => ({
  ...buildConfig().umbrales.find((u) => u.key === 'humAmb')!,
  ...patch,
});

const montar = (u: MetricThreshold, props: { disabled?: boolean } = {}) => {
  const onChange = vi.fn();
  render(<UmbralesForm value={[u]} onChange={onChange} {...props} />);
  return onChange;
};

describe('UmbralesForm · editor de bandas', () => {
  it('muestra los seis valores rotulados como en la barra', () => {
    montar(humAmb());

    for (const [rotulo, valor] of [
      ['Crítico debajo de', '52'],
      ['Alerta debajo de', '57'],
      ['Ideal desde', '62'],
      ['Ideal hasta', '84'],
      ['Alerta arriba de', '88'],
      ['Crítico arriba de', '91'],
    ]) {
      const campo = screen.getByRole('spinbutton', { name: `Humedad ambiental · ${rotulo}` }) as HTMLInputElement;
      expect(campo.value).toBe(valor);
    }
    expect(screen.getAllByRole('slider')).toHaveLength(6);
  });

  it('las flechas mueven la manija un paso y Shift diez', () => {
    const onChange = montar(humAmb());
    const manija = screen.getByRole('slider', { name: 'Humedad ambiental · Alerta debajo de' });

    fireEvent.keyDown(manija, { key: 'ArrowRight' });
    expect(onChange).toHaveBeenLastCalledWith(0, { alertMin: 58 });

    fireEvent.keyDown(manija, { key: 'ArrowLeft', shiftKey: true });
    expect(onChange).toHaveBeenLastCalledWith(0, { alertMin: 52 }); // 47 se frena en el crítico
  });

  it('una manija no pasa a su vecina', () => {
    const onChange = montar(humAmb());
    const manija = screen.getByRole('slider', { name: 'Humedad ambiental · Alerta arriba de' });

    fireEvent.keyDown(manija, { key: 'End' });
    expect(onChange).toHaveBeenLastCalledWith(0, { alertMax: 91 });
    expect(manija.getAttribute('aria-valuemin')).toBe('84');
  });

  it('un punto de alerta tipeado fuera de su franja marca el campo y la métrica', () => {
    montar(humAmb({ alertMin: 65 }));

    expect(
      screen.getByRole('spinbutton', { name: 'Humedad ambiental · Alerta debajo de' }).getAttribute('aria-invalid'),
    ).toBe('true');
    expect(screen.getByText(/punto de alerta debe quedar entre el crítico y el ideal/)).toBeTruthy();
  });

  it('en sólo lectura las manijas no se mueven', () => {
    const onChange = montar(humAmb(), { disabled: true });
    const manija = screen.getByRole('slider', { name: 'Humedad ambiental · Ideal desde' });

    fireEvent.keyDown(manija, { key: 'ArrowRight' });
    expect(onChange).not.toHaveBeenCalled();
    expect(manija.getAttribute('tabindex')).toBe('-1');
  });
});
