// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { NodoPanel } from './NodoPanel';
import type { TrazaRegla } from '@/types/domain';

afterEach(cleanup);

const regla = (p: Partial<TrazaRegla> = {}): TrazaRegla => ({
  ruleId: 'IrrigationRule',
  rama: 'RIEGO',
  prioridad: 10,
  estado: 'EVALUADA',
  comparaciones: [
    {
      etiqueta: 'Humedad de sustrato',
      clave: 'riego.umbral-humedad',
      recibido: 38,
      operador: 'LT',
      umbral: 42,
      unidad: '%',
      configurable: true,
      resultado: 'CUMPLE',
    },
    {
      etiqueta: 'Riegos en las últimas 24 h',
      clave: 'riego.max-riegos-24h-sector',
      recibido: 0,
      operador: 'GE',
      umbral: 1,
      unidad: 'riegos',
      configurable: true,
      resultado: 'NO_CUMPLE',
    },
    {
      etiqueta: 'Estado del sector',
      clave: null,
      recibido: 'critical',
      operador: 'EQ',
      umbral: 'critical',
      unidad: '',
      configurable: false,
      resultado: 'CUMPLE',
    },
  ],
  acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'Humedad de sustrato 38% bajo el umbral mínimo de 42%.' }],
  bloqueadaPor: null,
  error: null,
  ...p,
});

const montar = (r: TrazaRegla, extra: Partial<React.ComponentProps<typeof NodoPanel>> = {}) => {
  const onEditar = vi.fn();
  const onCerrar = vi.fn();
  render(
    <NodoPanel
      regla={r}
      label="💦 Riego"
      tieneParametros
      nombreDe={(id) => (id === 'WeatherOverrideRule' ? '🌧️ Condición climática (lluvia)' : id)}
      onEditar={onEditar}
      onCerrar={onCerrar}
      {...extra}
    />,
  );
  return { onEditar, onCerrar };
};

describe('NodoPanel', () => {
  it('muestra TODAS las comparaciones con recibido, operador, umbral y resultado', () => {
    montar(regla());

    const filas = screen.getAllByTestId('fila-comparacion');
    expect(filas).toHaveLength(3);
    expect(filas[0].textContent).toContain('Humedad de sustrato');
    expect(filas[0].textContent).toContain('38 %');
    expect(filas[0].textContent).toContain('42 %');
    expect(filas[0].textContent).toContain('✓');
    expect(filas[1].textContent).toContain('✗');
  });

  it('muestra la clave del parámetro de las configurables y marca las fijas', () => {
    montar(regla());

    const filas = screen.getAllByTestId('fila-comparacion');
    expect(filas[0].textContent).toContain('riego.umbral-humedad');
    expect(filas[2].textContent).toMatch(/fija/i);
  });

  it('lista las acciones con su motivo', () => {
    montar(regla());

    expect(screen.getByText('ACTIVAR_VALVULA')).toBeTruthy();
    expect(screen.getByText(/bajo el umbral mínimo de 42%/)).toBeTruthy();
  });

  it('"Editar parámetro" avisa qué regla abrir en Parámetros', () => {
    const { onEditar } = montar(regla());

    fireEvent.click(screen.getByRole('button', { name: /Editar parámetro/ }));

    expect(onEditar).toHaveBeenCalledWith('IrrigationRule');
  });

  it('una regla sin parámetros no ofrece editar', () => {
    montar(regla({ ruleId: 'ManualLockRule' }), { tieneParametros: false });
    expect(screen.queryByRole('button', { name: /Editar parámetro/ })).toBeNull();
  });

  it('cerrar avisa', () => {
    const { onCerrar } = montar(regla());
    fireEvent.click(screen.getByRole('button', { name: /Cerrar/ }));
    expect(onCerrar).toHaveBeenCalled();
  });

  it('una omitida dice quién cortó la rama', () => {
    montar(regla({ estado: 'OMITIDA_RAMA_BLOQUEADA', comparaciones: [], acciones: [], bloqueadaPor: 'WeatherOverrideRule' }));

    expect(screen.getByText(/Omitida: la rama la cortó/)).toBeTruthy();
    expect(screen.getByText(/🌧️ Condición climática \(lluvia\)/)).toBeTruthy();
    expect(screen.queryAllByTestId('fila-comparacion')).toHaveLength(0);
  });

  it('una con error muestra el mensaje', () => {
    montar(regla({ estado: 'ERROR', comparaciones: [], acciones: [], error: 'IllegalStateException: boom' }));
    expect(screen.getByText(/IllegalStateException: boom/)).toBeTruthy();
  });

  it('SIN_DATO se lee "sin dato"', () => {
    montar(
      regla({
        comparaciones: [
          { etiqueta: 'Humedad de sustrato', clave: 'riego.umbral-humedad', recibido: null, operador: 'LT', umbral: 42, unidad: '%', configurable: true, resultado: 'SIN_DATO' },
        ],
      }),
    );
    expect(screen.getByTestId('fila-comparacion').textContent).toContain('sin dato');
  });
});
