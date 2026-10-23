import type { DemoRule } from './types';

/**
 * Estado de la regla en el vivero, sólo lectura: el encendido se cambia únicamente en Motor de reglas del dashboard.
 * La demo sólo toca cooldowns y bypass.
 */
export function stateLegend(rule: DemoRule): string {
  return rule.enabled ? 'Prendida en el vivero' : 'Apagada en el vivero — se cambia en Motor de reglas';
}
