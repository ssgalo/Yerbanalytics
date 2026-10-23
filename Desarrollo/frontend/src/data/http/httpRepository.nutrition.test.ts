import { afterEach, describe, expect, it, vi } from 'vitest';
import { HttpRepository } from './httpRepository';
import { NutritionPlanError } from '@/data/nutritionPlanError';
import type { NutritionPlan } from '@/types/domain';

const BASE = 'http://localhost:8000/api';

const plan: NutritionPlan = {
  intervalDays: 14,
  customInterval: true,
  lastApplicationTs: 1000,
  nextDueTs: 2000,
  batchOpen: false,
  advanced: false,
};

function stubFetch(status: number, body?: unknown) {
  const fn = vi.fn(async () => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (body === undefined) throw new Error('sin cuerpo');
      return body;
    },
  }));
  vi.stubGlobal('fetch', fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

describe('HttpRepository · plan de nutrición', () => {
  it('setNutritionPlanInterval hace PUT /zonas/{zona}/plan-nutricion con los días y devuelve el plan', async () => {
    const fetchFn = stubFetch(200, plan);

    const result = await new HttpRepository(BASE).setNutritionPlanInterval('MZ-3', 14);

    const [url, init] = fetchFn.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`${BASE}/zonas/MZ-3/plan-nutricion`);
    expect(init.method).toBe('PUT');
    expect(JSON.parse(init.body as string)).toEqual({ intervalDays: 14 });
    expect(result).toEqual(plan);
  });

  it('un 400 se traduce a NutritionPlanError con el mensaje del backend', async () => {
    stubFetch(400, { error: 'El intervalo del plan de nutrición debe estar entre 3 y 30 días.' });

    await expect(new HttpRepository(BASE).setNutritionPlanInterval('MZ-3', 2)).rejects.toThrow(
      new NutritionPlanError('El intervalo del plan de nutrición debe estar entre 3 y 30 días.'),
    );
  });

  it('un 404 (macro-zona inexistente) también es un rechazo esperable', async () => {
    stubFetch(404, { error: 'La macro-zona MZ-9 no existe.' });

    await expect(new HttpRepository(BASE).setNutritionPlanInterval('MZ-9', 7)).rejects.toBeInstanceOf(
      NutritionPlanError,
    );
  });

  it('un 400 sin cuerpo usa un mensaje genérico', async () => {
    stubFetch(400);

    await expect(new HttpRepository(BASE).setNutritionPlanInterval('MZ-3', 2)).rejects.toThrow(/No se pudo guardar/);
  });

  it('cualquier otro código es una falla, no un rechazo', async () => {
    stubFetch(500);

    const error = await new HttpRepository(BASE).setNutritionPlanInterval('MZ-3', 7).catch((e: unknown) => e);

    expect(error).not.toBeInstanceOf(NutritionPlanError);
    expect((error as Error).message).toMatch(/500/);
  });
});
