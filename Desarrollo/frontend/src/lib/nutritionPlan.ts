/* ============================================================
   Plan de nutrición de una macro-zona (reglas_v2 §8.4): el intervalo entre aplicaciones y lo que se muestra de él.

   El rango es el de `nutricion.intervalo-plan` del catálogo de parámetros del backend (3–30 días, fábrica 7): el
   backend lo valida igual al guardar y rechaza lo que quede afuera; la vista lo usa para marcar el campo y bloquear
   el guardado. Un test lo ata al fixture del catálogo, que a su vez se compara con los enums del backend.
   ============================================================ */
import type { NutritionPlan } from '@/types/domain';

export const NUTRITION_INTERVAL_MIN_DAYS = 3;
export const NUTRITION_INTERVAL_MAX_DAYS = 30;
export const NUTRITION_INTERVAL_DEFAULT_DAYS = 7;

/** Por qué el texto no es un intervalo válido, o `null` si lo es. Acepta sólo días enteros. */
export function nutritionIntervalError(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed === '') return 'Ingresá los días entre aplicaciones.';
  if (!/^\d+$/.test(trimmed)) return 'Ingresá un número entero de días.';
  const days = Number(trimmed);
  if (days < NUTRITION_INTERVAL_MIN_DAYS || days > NUTRITION_INTERVAL_MAX_DAYS) {
    return `El intervalo debe estar entre ${NUTRITION_INTERVAL_MIN_DAYS} y ${NUTRITION_INTERVAL_MAX_DAYS} días.`;
  }
  return null;
}

/** El estado del plan en una frase para el detalle de la macro-zona. */
export type NutritionPlanStatus =
  | { kind: 'running' }
  | { kind: 'advanced' }
  | { kind: 'due-now' }
  | { kind: 'scheduled'; dueTs: number };

export function nutritionPlanStatus(plan: NutritionPlan, now: number): NutritionPlanStatus {
  if (plan.batchOpen) return { kind: 'running' };
  if (plan.advanced) return { kind: 'advanced' };
  if (plan.nextDueTs == null || plan.nextDueTs <= now) return { kind: 'due-now' };
  return { kind: 'scheduled', dueTs: plan.nextDueTs };
}
