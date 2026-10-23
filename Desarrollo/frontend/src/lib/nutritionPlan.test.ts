import { describe, expect, it } from 'vitest';
import fixture from '@/data/mock/catalogoReglas.fixture.json';
import type { CatalogoReglas, NutritionPlan } from '@/types/domain';
import {
  NUTRITION_INTERVAL_DEFAULT_DAYS,
  NUTRITION_INTERVAL_MAX_DAYS,
  NUTRITION_INTERVAL_MIN_DAYS,
  nutritionIntervalError,
  nutritionPlanStatus,
} from './nutritionPlan';

const plan = (over: Partial<NutritionPlan> = {}): NutritionPlan => ({
  intervalDays: 7,
  customInterval: false,
  lastApplicationTs: 1_000,
  nextDueTs: 5_000,
  batchOpen: false,
  advanced: false,
  ...over,
});

describe('rango del intervalo', () => {
  it('es el de nutricion.intervalo-plan del catálogo', () => {
    const p = (fixture as CatalogoReglas).parametros.find((x) => x.clave === 'nutricion.intervalo-plan')!;
    expect(NUTRITION_INTERVAL_MIN_DAYS).toBe(p.min);
    expect(NUTRITION_INTERVAL_MAX_DAYS).toBe(p.max);
    expect(String(NUTRITION_INTERVAL_DEFAULT_DAYS)).toBe(p.fabrica);
  });
});

describe('nutritionIntervalError', () => {
  it.each(['3', '7', '14', '30', ' 10 '])('%j es válido', (text) => {
    expect(nutritionIntervalError(text)).toBeNull();
  });

  it('2 días se rechaza, por debajo del mínimo', () => {
    expect(nutritionIntervalError('2')).toMatch(/entre 3 y 30/);
  });

  it('31 días se rechaza, por encima del máximo', () => {
    expect(nutritionIntervalError('31')).toMatch(/entre 3 y 30/);
  });

  it.each(['', '  ', 'siete', '7,5', '7.5', '-7', '1e1'])('%j no es un número entero de días', (text) => {
    expect(nutritionIntervalError(text)).not.toBeNull();
  });
});

describe('nutritionPlanStatus', () => {
  it('con la tanda en curso, el plan está ejecutándose', () => {
    expect(nutritionPlanStatus(plan({ batchOpen: true, nextDueTs: null }), 2_000)).toEqual({ kind: 'running' });
  });

  it('adelantado por CE baja', () => {
    expect(nutritionPlanStatus(plan({ advanced: true, nextDueTs: 1_500 }), 2_000)).toEqual({ kind: 'advanced' });
  });

  it('una zona que nunca recibió el plan vence ya', () => {
    expect(nutritionPlanStatus(plan({ lastApplicationTs: null, nextDueTs: null }), 2_000)).toEqual({ kind: 'due-now' });
  });

  it('un vencimiento ya pasado también es "ya"', () => {
    expect(nutritionPlanStatus(plan({ nextDueTs: 1_500 }), 2_000)).toEqual({ kind: 'due-now' });
  });

  it('un vencimiento futuro se muestra con su fecha', () => {
    expect(nutritionPlanStatus(plan({ nextDueTs: 5_000 }), 2_000)).toEqual({ kind: 'scheduled', dueTs: 5_000 });
  });
});
