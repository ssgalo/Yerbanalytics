// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import { cleanup, render, screen, within } from '@testing-library/react';
import { ReglaNodeView, type ReglaNodeData } from './ReglaNode';
import type { Comparacion, TrazaRegla } from '@/types/domain';

afterEach(cleanup);

const cmp = (p: Partial<Comparacion>): Comparacion => ({
  etiqueta: 'Humedad de sustrato',
  clave: 'riego.umbral-humedad',
  recibido: 38,
  operador: 'LT',
  umbral: 45,
  unidad: '%',
  configurable: true,
  resultado: 'CUMPLE',
  ...p,
});

const traza = (p: Partial<TrazaRegla>): TrazaRegla => ({
  ruleId: 'RiegoPorDeficitRule',
  rama: 'RIEGO',
  prioridad: 10,
  estado: 'EVALUADA',
  comparaciones: [cmp({})],
  acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: '' }],
  bloqueadaPor: null,
  error: null,
  ...p,
});

const montar = (data: Partial<ReglaNodeData>) =>
  render(
    <ReglaNodeView
      data={{
        label: '💦 Riego',
        ruleId: 'RiegoPorDeficitRule',
        estado: 'accion',
        traza: traza({}),
        bloqueadaPorLabel: null,
        seleccionado: false,
        ...data,
      }}
    />,
  );

describe('ReglaNodeView', () => {
  it('muestra "recibido op umbral" con su resultado', () => {
    montar({});

    const linea = screen.getByTestId('comparacion-0');
    expect(linea.textContent).toContain('Humedad de sustrato');
    expect(linea.textContent).toContain('38 %');
    expect(linea.textContent).toContain('<');
    expect(linea.textContent).toContain('45 %');
    expect(linea.textContent).toContain('✓');
    expect(linea.getAttribute('data-resultado')).toBe('CUMPLE');
  });

  it('una comparación no cumplida se marca con ✗', () => {
    montar({
      estado: 'paso',
      traza: traza({ comparaciones: [cmp({ recibido: 55, resultado: 'NO_CUMPLE' })] }),
    });

    expect(screen.getByTestId('comparacion-0').textContent).toContain('✗');
    expect(screen.getByTestId('comparacion-0').getAttribute('data-resultado')).toBe('NO_CUMPLE');
  });

  it('muestra hasta dos comparaciones y avisa que hay más', () => {
    const tres = [
      cmp({}),
      cmp({
        etiqueta: 'Riegos en las últimas 24 h',
        recibido: 0,
        umbral: 1,
        unidad: 'riegos',
        operador: 'GE',
        resultado: 'NO_CUMPLE',
      }),
      cmp({ etiqueta: 'Tercera' }),
    ];
    montar({ traza: traza({ comparaciones: tres }) });

    expect(screen.getByTestId('comparacion-0')).toBeTruthy();
    expect(screen.getByTestId('comparacion-1')).toBeTruthy();
    expect(screen.queryByTestId('comparacion-2')).toBeNull();
    expect(screen.getByText('+1 comparación más')).toBeTruthy();
  });

  it('una comparación no configurable lleva candado', () => {
    montar({
      traza: traza({
        comparaciones: [
          cmp({
            etiqueta: 'Estado del sector',
            clave: null,
            configurable: false,
            recibido: 'ok',
            umbral: 'critical',
            unidad: '',
            operador: 'EQ',
            resultado: 'NO_CUMPLE',
          }),
        ],
      }),
    });

    const linea = screen.getByTestId('comparacion-0');
    expect(within(linea).getByLabelText('No configurable')).toBeTruthy();
  });

  it('una configurable no lleva candado', () => {
    montar({});
    expect(
      within(screen.getByTestId('comparacion-0')).queryByLabelText('No configurable'),
    ).toBeNull();
  });

  it('SIN_DATO se muestra como "sin dato"', () => {
    montar({
      estado: 'paso',
      traza: traza({ comparaciones: [cmp({ recibido: null, resultado: 'SIN_DATO' })] }),
    });

    const linea = screen.getByTestId('comparacion-0');
    expect(linea.textContent).toContain('sin dato');
    expect(linea.getAttribute('data-resultado')).toBe('SIN_DATO');
  });

  it('el estado se ve como chip y como atributo para el estilo', () => {
    const { container } = montar({ estado: 'pospuso' });

    expect(screen.getByText('Pospuso')).toBeTruthy();
    expect(container.querySelector('[data-estado="pospuso"]')).toBeTruthy();
  });

  it('una regla omitida dice quién cortó la rama y no muestra comparaciones', () => {
    montar({
      estado: 'omitida',
      traza: traza({
        estado: 'OMITIDA_RAMA_BLOQUEADA',
        comparaciones: [],
        acciones: [],
        bloqueadaPor: 'PosponerPorLluviaRule',
      }),
      bloqueadaPorLabel: '🌧️ Condición climática (lluvia)',
    });

    expect(screen.getByText('Omitida')).toBeTruthy();
    expect(screen.getByText(/la cortó 🌧️ Condición climática \(lluvia\)/)).toBeTruthy();
    expect(screen.queryByTestId('comparacion-0')).toBeNull();
  });

  it('una regla no alcanzada dice dónde se detuvo el motor', () => {
    montar({
      estado: 'noAlcanzada',
      traza: traza({
        estado: 'NO_ALCANZADA',
        comparaciones: [],
        acciones: [],
        bloqueadaPor: 'ManualLockRule',
      }),
      bloqueadaPorLabel: '🔒 Bloqueo manual',
    });

    expect(screen.getByText('No alcanzada')).toBeTruthy();
    expect(screen.getByText(/se detuvo en 🔒 Bloqueo manual/)).toBeTruthy();
  });

  it('una regla con error muestra el mensaje', () => {
    montar({
      estado: 'error',
      traza: traza({
        estado: 'ERROR',
        comparaciones: [],
        acciones: [],
        error: 'NullPointerException: boom',
      }),
    });

    expect(screen.getByText('Error')).toBeTruthy();
    expect(screen.getByText(/NullPointerException: boom/)).toBeTruthy();
  });

  it('una regla evaluada sin comparaciones lo dice', () => {
    montar({
      estado: 'paso',
      traza: traza({ comparaciones: [], acciones: [{ tipo: 'NOOP_INFO', motivo: '' }] }),
    });
    expect(screen.getByText('Sin comparaciones')).toBeTruthy();
  });

  it('el nodo seleccionado se marca', () => {
    const { container } = montar({ seleccionado: true });
    expect(container.querySelector('[data-seleccionado="true"]')).toBeTruthy();
  });
});
