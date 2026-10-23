import type { DemoRule } from './types';

/** Leyenda junto al interruptor cuando el vivero apagó la regla (el override de demo la fuerza o puede forzarla). */
export function operationalLegend(rule: DemoRule): string | null {
  return rule.operationalActive ? null : 'Apagada en el vivero';
}

/** El interruptor parte del estado efectivo: es lo que de verdad corre en el motor. */
export function nextEnabled(rule: DemoRule): boolean {
  return !rule.effectiveEnabled;
}
