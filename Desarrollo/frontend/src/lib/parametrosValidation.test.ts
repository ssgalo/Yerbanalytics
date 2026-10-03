import { describe, expect, it } from 'vitest';
import { parametroError } from './parametrosValidation';
import type { ParametroRegla } from '@/types/domain';

/** Sólo los campos que la validación lee: no hay constantes propias en el cliente. */
type Def = Pick<ParametroRegla, 'tipo' | 'min' | 'max' | 'decimales' | 'unidad'>;

const umbral: Def = { tipo: 'NUMERO', min: 35, max: 60, decimales: 0, unidad: '%' };
const entero: Def = { tipo: 'ENTERO', min: 1, max: 10, decimales: 0, unidad: 'riegos' };
const litros: Def = { tipo: 'NUMERO', min: 0.1, max: 0.5, decimales: 2, unidad: 'L/punto' };
const hora: Def = { tipo: 'HORA', min: null, max: null, decimales: 0, unidad: '' };
const ventana: Def = { tipo: 'VENTANA_HORARIA', min: null, max: null, decimales: 0, unidad: '' };

describe('parametroError · numéricos', () => {
  it('acepta valores dentro del rango, bordes incluidos', () => {
    expect(parametroError(umbral, '42')).toBeNull();
    expect(parametroError(umbral, '35')).toBeNull();
    expect(parametroError(umbral, '60')).toBeNull();
  });

  it('rechaza fuera de rango con el mismo mensaje que el servidor', () => {
    expect(parametroError(umbral, '34')).toBe('Debe estar entre 35 y 60 %.');
    expect(parametroError(umbral, '61')).toBe('Debe estar entre 35 y 60 %.');
  });

  it('rechaza vacío y no numérico', () => {
    expect(parametroError(umbral, '')).toMatch(/número/);
    expect(parametroError(umbral, 'abc')).toMatch(/número/);
    expect(parametroError(umbral, 'NaN')).toMatch(/número/);
  });

  it('ENTERO rechaza decimales', () => {
    expect(parametroError(entero, '2.5')).toMatch(/entero/);
    expect(parametroError(entero, '3')).toBeNull();
  });

  it('NUMERO respeta la cantidad de decimales del DTO', () => {
    expect(parametroError(litros, '0.2')).toBeNull();
    expect(parametroError(litros, '0.25')).toBeNull();
    expect(parametroError(litros, '0.255')).toMatch(/2 decimales/);
    expect(parametroError(umbral, '42.5')).toMatch(/decimales|entero/);
  });

  it('sin min/max no valida rango', () => {
    expect(parametroError({ ...umbral, min: null, max: null }, '9999')).toBeNull();
  });
});

describe('parametroError · horas y ventanas', () => {
  it('valida HH:mm', () => {
    expect(parametroError(hora, '06:00')).toBeNull();
    expect(parametroError(hora, '23:59')).toBeNull();
    expect(parametroError(hora, '24:00')).toMatch(/HH:mm/);
    expect(parametroError(hora, '6')).toMatch(/HH:mm/);
    expect(parametroError(hora, '')).toMatch(/HH:mm/);
  });

  it('valida la ventana desde-hasta y admite la que cruza la medianoche', () => {
    expect(parametroError(ventana, '06:00-18:00')).toBeNull();
    expect(parametroError(ventana, '18:00-06:00')).toBeNull();
    expect(parametroError(ventana, '06:00')).toMatch(/ventana/i);
    expect(parametroError(ventana, '25:00-18:00')).toMatch(/ventana/i);
  });

  it('rechaza una ventana vacía (desde = hasta)', () => {
    expect(parametroError(ventana, '06:00-06:00')).toMatch(/ventana/i);
  });
});
