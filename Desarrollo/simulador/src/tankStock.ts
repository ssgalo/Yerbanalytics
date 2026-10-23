/* ============================================================
   Stock de los tanques de insumo para las demos (reglas_v2 §8.3). Una aplicación se rechaza contra un tanque sin
   registrar o vacío, así que antes de pedir una el simulador se asegura de que los tanques de la zona tengan stock:
   registra el que falta y repone el que está bajo.

   Módulo sin React ni fetch (las llamadas se inyectan en `TankStockDeps`), como `happyPath.ts`: se prueba sin backend.
   Usa la API pública de tanques (`/api/tanques`), la misma que usa el dashboard; el simulador no guarda estado de tanques.
   ============================================================ */
import type { Supply, TankLevel } from './types';

export const SUPPLIES: readonly Supply[] = ['FERTILIZER', 'FUNGICIDE'];

/** Capacidad con la que se registra un tanque que no existe, en litros. */
export const DEFAULT_CAPACITY_LITERS = 20;

/** Por debajo de este porcentaje de su capacidad, un tanque se repone antes de la demo. */
export const MIN_LEVEL_PCT = 50;

export interface TankLoadPlan {
  supply: Supply;
  liters: number;
  /** Sólo al registrar un tanque nuevo: la capacidad es obligatoria la primera vez. */
  capacityLiters?: number;
}

/**
 * Qué cargas hacen falta para que los dos tanques de la zona tengan stock: el que no existe se registra lleno, el que
 * está por debajo del mínimo se repone hasta llenarlo, y el que está bien no se toca.
 */
export function planTankLoads(tanks: TankLevel[], zoneId: string): TankLoadPlan[] {
  const plans: TankLoadPlan[] = [];
  for (const supply of SUPPLIES) {
    const tank = tanks.find((t) => t.zoneId === zoneId && t.supply === supply);
    if (!tank) {
      plans.push({ supply, liters: DEFAULT_CAPACITY_LITERS, capacityLiters: DEFAULT_CAPACITY_LITERS });
    } else if (tank.levelPct < MIN_LEVEL_PCT) {
      plans.push({ supply, liters: (tank.capacityMl - tank.levelMl) / 1000 });
    }
  }
  return plans;
}

export interface TankStockDeps {
  getTanks(): Promise<TankLevel[]>;
  loadTank(zoneId: string, supply: Supply, load: { liters: number; capacityLiters?: number }): Promise<unknown>;
}

const LABEL: Record<Supply, string> = { FERTILIZER: 'fertilizante', FUNGICIDE: 'fitosanitario' };

/** Deja los tanques de la zona con stock y devuelve las líneas para el registro de la corrida. */
export async function ensureTankStock(zoneId: string, deps: TankStockDeps): Promise<string[]> {
  const plans = planTankLoads(await deps.getTanks(), zoneId);
  if (plans.length === 0) return [`✓ Los tanques de ${zoneId} ya tienen stock`];
  const lines: string[] = [];
  for (const plan of plans) {
    await deps.loadTank(zoneId, plan.supply, {
      liters: plan.liters,
      ...(plan.capacityLiters != null && { capacityLiters: plan.capacityLiters }),
    });
    lines.push(
      plan.capacityLiters != null
        ? `✓ Tanque de ${LABEL[plan.supply]} de ${zoneId} registrado con ${plan.liters} L`
        : `✓ Tanque de ${LABEL[plan.supply]} de ${zoneId} repuesto (+${plan.liters} L)`,
    );
  }
  return lines;
}
