// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { ParametroField } from './ParametroField';
import type { ParametroRegla } from '@/types/domain';
import { catalogoDeFabrica } from '../__fixtures__/catalogos';

afterEach(cleanup);

const base = catalogoDeFabrica().parametros.find((p) => p.clave === 'riego.umbral-humedad')!;
const hora: ParametroRegla = {
  ...base,
  clave: 'riego.hora-inicio',
  etiqueta: 'Hora de inicio',
  tipo: 'HORA',
  unidad: '',
  valor: '06:00',
  fabrica: '06:00',
  min: null,
  max: null,
};
const ventana: ParametroRegla = {
  ...hora,
  clave: 'riego.ventana',
  etiqueta: 'Ventana de riego',
  tipo: 'VENTANA_HORARIA',
  valor: '06:00-18:00',
  fabrica: '06:00-18:00',
};

describe('ParametroField', () => {
  it('con valor 1 muestra la unidad en singular ("1 riego", no "1 riegos")', () => {
    const riegos: ParametroRegla = { ...base, unidad: 'riegos', tipo: 'ENTERO', decimales: 0 };
    const { rerender } = render(<ParametroField parametro={riegos} valor="1" invalid={false} onChange={vi.fn()} />);
    expect(screen.getByText('riego')).toBeTruthy();
    rerender(<ParametroField parametro={riegos} valor="4" invalid={false} onChange={vi.fn()} />);
    expect(screen.getByText('riegos')).toBeTruthy();
  });

  it('NUMERO usa el NumberField con la unidad y entrega el valor como texto', () => {
    const onChange = vi.fn();
    render(<ParametroField parametro={base} valor="42" invalid={false} onChange={onChange} />);

    const input = screen.getByLabelText(base.etiqueta) as HTMLInputElement;
    expect(input.type).toBe('number');
    expect(input.value).toBe('42');
    expect(screen.getByText('%')).toBeTruthy();

    fireEvent.change(input, { target: { value: '40' } });
    expect(onChange).toHaveBeenCalledWith('40');
  });

  it('el paso del campo sale de los decimales del parámetro', () => {
    const litros: ParametroRegla = { ...base, tipo: 'NUMERO', decimales: 2, valor: '0.2', min: 0.1, max: 0.5 };
    render(<ParametroField parametro={litros} valor="0.2" invalid={false} onChange={() => undefined} />);

    expect((screen.getByLabelText(base.etiqueta) as HTMLInputElement).step).toBe('0.01');
  });

  it('invalid marca el campo', () => {
    render(<ParametroField parametro={base} valor="99" invalid onChange={() => undefined} />);
    expect(screen.getByLabelText(base.etiqueta).className).toMatch(/inputError/);
  });

  it('HORA usa un input de hora', () => {
    const onChange = vi.fn();
    render(<ParametroField parametro={hora} valor="06:00" invalid={false} onChange={onChange} />);

    const input = screen.getByLabelText('Hora de inicio') as HTMLInputElement;
    expect(input.type).toBe('time');
    expect(input.value).toBe('06:00');

    fireEvent.change(input, { target: { value: '07:30' } });
    expect(onChange).toHaveBeenCalledWith('07:30');
  });

  it('VENTANA_HORARIA usa dos inputs y arma "desde-hasta"', () => {
    const onChange = vi.fn();
    render(<ParametroField parametro={ventana} valor="06:00-18:00" invalid={false} onChange={onChange} />);

    const desde = screen.getByLabelText('Ventana de riego (desde)') as HTMLInputElement;
    const hasta = screen.getByLabelText('Ventana de riego (hasta)') as HTMLInputElement;
    expect([desde.value, hasta.value]).toEqual(['06:00', '18:00']);

    fireEvent.change(hasta, { target: { value: '20:00' } });
    expect(onChange).toHaveBeenLastCalledWith('06:00-20:00');
    fireEvent.change(desde, { target: { value: '05:00' } });
    expect(onChange).toHaveBeenLastCalledWith('05:00-18:00');
  });
});
