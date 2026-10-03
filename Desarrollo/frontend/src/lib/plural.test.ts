import { describe, expect, it } from 'vitest';
import { contar, unidadSegunValor } from './plural';
import { formatearValor, parametroError } from './parametrosValidation';

describe('contar', () => {
  it('singulariza con 1 y pluraliza con el resto, incluido 0', () => {
    expect(contar(1, 'cambio', 'cambios')).toBe('1 cambio');
    expect(contar(0, 'cambio', 'cambios')).toBe('0 cambios');
    expect(contar(3, 'cambio', 'cambios')).toBe('3 cambios');
  });
});

describe('unidadSegunValor', () => {
  it('riegos/riego, dosis/dosis, parámetros/parámetro, modificados/modificado', () => {
    expect(unidadSegunValor('riegos', 1)).toBe('riego');
    expect(unidadSegunValor('riegos', 2)).toBe('riegos');
    expect(unidadSegunValor('dosis', 1)).toBe('dosis');
    expect(unidadSegunValor('dosis', 4)).toBe('dosis');
    expect(unidadSegunValor('parámetros', 1)).toBe('parámetro');
    expect(unidadSegunValor('modificados', 1)).toBe('modificado');
  });

  it('deja intactas las unidades que no son plurales de palabra ("%", "s", "índice")', () => {
    expect(unidadSegunValor('%', 1)).toBe('%');
    expect(unidadSegunValor('s', 1)).toBe('s');
    expect(unidadSegunValor('índice', 1)).toBe('índice');
  });

  it('acepta el valor como texto canónico ("1", "1.0") y no singulariza 1.5', () => {
    expect(unidadSegunValor('riegos', '1')).toBe('riego');
    expect(unidadSegunValor('riegos', '1.0')).toBe('riego');
    expect(unidadSegunValor('riegos', '1.5')).toBe('riegos');
  });
});

describe('formatearValor con unidad en plural', () => {
  it('muestra "1 riego", no "1 riegos"', () => {
    const def = { tipo: 'ENTERO', unidad: 'riegos' } as const;
    expect(formatearValor(def, '1')).toBe('1 riego');
    expect(formatearValor(def, '4')).toBe('4 riegos');
  });
});

describe('parametroError con unidad en plural', () => {
  it('"como mínimo 1 riego" en singular y "entre 2 y 10 riegos" en plural', () => {
    const def = { tipo: 'ENTERO', unidad: 'riegos', decimales: 0 } as const;
    expect(parametroError({ ...def, min: 1, max: null }, '0')).toBe('Debe ser como mínimo 1 riego.');
    expect(parametroError({ ...def, min: 2, max: 10 }, '0')).toBe('Debe estar entre 2 y 10 riegos.');
    expect(parametroError({ ...def, min: null, max: 1 }, '5')).toBe('Debe ser como máximo 1 riego.');
  });
});
