import { describe, expect, it } from 'vitest';
import { MockRepository } from './mockRepository';
import { buildMockNutritionPlan } from './nutritionPlanMock';
import { NutritionPlanError } from '@/data/nutritionPlanError';

const DAY = 86_400_000;

describe('buildMockNutritionPlan', () => {
  it('sin intervalo propio rige el general (7 días) y la próxima aplicación es la última más el intervalo', () => {
    const plan = buildMockNutritionPlan(0, null, 10 * DAY);

    expect(plan).toMatchObject({ intervalDays: 7, customInterval: false, batchOpen: false, advanced: false });
    expect(plan.lastApplicationTs).toBe(9 * DAY);
    expect(plan.nextDueTs).toBe(16 * DAY);
  });

  it('con intervalo propio lo marca y corre la próxima aplicación', () => {
    const plan = buildMockNutritionPlan(0, 14, 10 * DAY);

    expect(plan).toMatchObject({ intervalDays: 14, customInterval: true });
    expect(plan.nextDueTs).toBe(9 * DAY + 14 * DAY);
  });

  it('las zonas se escalonan: no todas aplicaron el mismo día', () => {
    const lasts = [0, 1, 2, 3, 4, 5].map((i) => buildMockNutritionPlan(i, null, 10 * DAY).lastApplicationTs);

    expect(new Set(lasts).size).toBeGreaterThan(1);
  });
});

describe('MockRepository · plan de nutrición', () => {
  it('el snapshot trae el plan de cada macro-zona', async () => {
    const { zonas } = await new MockRepository(1).getNursery();

    expect(zonas.length).toBeGreaterThan(0);
    for (const z of zonas) expect(z.nutritionPlan).toMatchObject({ intervalDays: 7, customInterval: false });
  });

  it('fijar el intervalo lo devuelve y queda en el snapshot', async () => {
    const repo = new MockRepository(1);

    const plan = await repo.setNutritionPlanInterval('MZ-2', 14);

    expect(plan).toMatchObject({ intervalDays: 14, customInterval: true });
    const zone = (await repo.getNursery()).zonas.find((z) => z.id === 'MZ-2')!;
    expect(zone.nutritionPlan).toEqual(plan);
    const other = (await repo.getNursery()).zonas.find((z) => z.id === 'MZ-1')!;
    expect(other.nutritionPlan?.intervalDays).toBe(7);
  });

  it.each([2, 31, 7.5])('un intervalo de %s se rechaza como el backend', async (days) => {
    await expect(new MockRepository(1).setNutritionPlanInterval('MZ-2', days)).rejects.toBeInstanceOf(
      NutritionPlanError,
    );
  });

  it('una macro-zona inexistente se rechaza', async () => {
    await expect(new MockRepository(1).setNutritionPlanInterval('MZ-99', 7)).rejects.toThrow(/no existe/);
  });
});
