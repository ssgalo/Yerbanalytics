// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Chip } from './Chip';
import { StateGlyph } from './StateGlyph';
import { Input, InputUnit } from './Field';
import { FilterChip, Segmented, Switch } from './Seleccion';
import { SaveBar } from './EstadosPantalla';
import { CompositionBar } from './Medidas';
import { escalaDeRango, estadoDeValor, fmtNum } from './rango';

afterEach(cleanup);

describe('estado con forma', () => {
  it('el glifo anuncia el estado sólo si se lo piden', () => {
    const { container, rerender } = render(<StateGlyph status="warning" />);
    expect(screen.queryByText('En observación')).toBeNull();
    expect(container.querySelector('[data-estado="warning"]')).not.toBeNull();
    rerender(<StateGlyph status="warning" label />);
    expect(screen.getByText('En observación')).toBeTruthy();
  });

  it('el chip de estado dice el nombre por defecto', () => {
    render(<Chip tone="critical" />);
    expect(screen.getByText('Crítico')).toBeTruthy();
  });

  it('la composición describe los conteos en palabras', () => {
    render(<CompositionBar counts={{ ok: 10, warning: 2, critical: 0, offline: 1 }} />);
    expect(screen.getByRole('img').getAttribute('aria-label')).toBe(
      '13 sectores: 10 saludable, 2 en observación, 1 fuera de servicio',
    );
  });
});

describe('selección accesible', () => {
  it('Segmented marca con aria-pressed la opción elegida', () => {
    const onChange = vi.fn();
    render(
      <Segmented
        label="Rango"
        value="hoy"
        onChange={onChange}
        options={[
          { value: 'hoy', label: 'Hoy' },
          { value: '7d', label: '7 días' },
        ]}
      />,
    );
    expect(screen.getByRole('button', { name: 'Hoy' }).getAttribute('aria-pressed')).toBe('true');
    expect(screen.getByRole('button', { name: '7 días' }).getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(screen.getByRole('button', { name: '7 días' }));
    expect(onChange).toHaveBeenCalledWith('7d');
  });

  it('FilterChip expone aria-pressed y su cantidad', () => {
    render(
      <FilterChip pressed onClick={() => {}} count={162}>
        Clorosis
      </FilterChip>,
    );
    const b = screen.getByRole('button');
    expect(b.getAttribute('aria-pressed')).toBe('true');
    expect(b.textContent).toBe('Clorosis162');
  });

  it('Switch es un role=switch con aria-checked', () => {
    const onChange = vi.fn();
    render(<Switch checked onChange={onChange} label="Demo Expo" />);
    const s = screen.getByRole('switch', { name: 'Demo Expo' });
    expect(s.getAttribute('aria-checked')).toBe('true');
    fireEvent.click(s);
    expect(onChange).toHaveBeenCalledWith(false);
  });

  it('Switch a medio camino expone aria-checked="mixed" y al tocarlo pide prender', () => {
    const onChange = vi.fn();
    render(<Switch checked="mixed" onChange={onChange} label="Todas las reglas de Riego" />);
    const s = screen.getByRole('switch', { name: 'Todas las reglas de Riego' });
    expect(s.getAttribute('aria-checked')).toBe('mixed');
    fireEvent.click(s);
    expect(onChange).toHaveBeenCalledWith(true);
  });
});

describe('campos', () => {
  it('un campo con error expone aria-invalid', () => {
    render(
      <>
        <Input aria-label="Motivo" invalid />
        <InputUnit aria-label="Volumen" unit="L" invalid />
        <Input aria-label="Nota" />
      </>,
    );
    expect(screen.getByLabelText('Motivo').getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('Volumen').getAttribute('aria-invalid')).toBe('true');
    expect(screen.getByLabelText('Nota').hasAttribute('aria-invalid')).toBe(false);
  });
});

describe('barra de guardado', () => {
  it('no aparece sin cambios y nombra la cantidad con ellos', () => {
    const { rerender } = render(<SaveBar count={0} onDiscard={() => {}} onSave={() => {}} />);
    expect(screen.queryByRole('region')).toBeNull();
    rerender(<SaveBar count={2} onDiscard={() => {}} onSave={() => {}} />);
    expect(screen.getByRole('region', { name: 'Cambios sin guardar' }).textContent).toContain('2 cambios sin guardar.');
  });
});

describe('rango de una métrica', () => {
  const b = { ideal: [42, 68] as [number, number], warn: [32, 80] as [number, number], crit: [22, 90] as [number, number] };

  it('la escala deja un margen del 8 % a cada lado de la banda crítica', () => {
    const { lo, hi, pos } = escalaDeRango(b);
    expect(lo).toBeCloseTo(16.56);
    expect(hi).toBeCloseTo(95.44);
    expect(pos(22)).toBeGreaterThan(0);
    expect(pos(-100)).toBe(0);
    expect(pos(500)).toBe(100);
  });

  it('el estado de un valor sale de sus bandas', () => {
    expect(estadoDeValor(55, b)).toBe('ok');
    expect(estadoDeValor(35, b)).toBe('warning');
    expect(estadoDeValor(28, b)).toBe('critical');
  });

  it('los números van con coma decimal', () => {
    expect(fmtNum(2.5, 1)).toBe('2,5');
  });
});
