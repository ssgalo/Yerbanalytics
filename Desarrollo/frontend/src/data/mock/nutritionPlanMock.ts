/* ============================================================
   Plan de nutrición de cada macro-zona para la demo (modo `mock`). Sin backend no hay motor que aplique el plan:
   cada zona arranca con una última aplicación de hace unos días y el único cambio posible es el intervalo.
   ============================================================ */
import { NUTRITION_INTERVAL_DEFAULT_DAYS } from '@/lib/nutritionPlan';
import type { NutritionPlan } from '@/types/domain';

const DAY_MS = 86_400_000;

/**
 * El plan de la zona número `zoneIndex` (desde 0). `customDays` es el intervalo que fijó el usuario en la sesión;
 * `null` = rige el general. Las zonas se escalonan (la última aplicación fue hace 1 a 5 días) para que la demo no
 * muestre las seis iguales.
 */
export function buildMockNutritionPlan(zoneIndex: number, customDays: number | null, now: number): NutritionPlan {
  const intervalDays = customDays ?? NUTRITION_INTERVAL_DEFAULT_DAYS;
  const lastApplicationTs = now - ((zoneIndex % 5) + 1) * DAY_MS;
  return {
    intervalDays,
    customInterval: customDays !== null,
    lastApplicationTs,
    nextDueTs: lastApplicationTs + intervalDays * DAY_MS,
    batchOpen: false,
    advanced: false,
  };
}
