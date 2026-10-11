import type { MetricSpec, Status } from '@/types/domain';

type Bandas = Pick<MetricSpec, 'ideal' | 'warn' | 'crit'>;

/**
 * Escala de una barra de rango: va de la banda crítica con un margen del 8 % a cada lado, para
 * que un valor justo en el borde no quede pegado al extremo. Devuelve la posición en % (0–100).
 */
export function escalaDeRango(b: Bandas) {
  const ancho = b.crit[1] - b.crit[0];
  const lo = b.crit[0] - ancho * 0.08;
  const hi = b.crit[1] + ancho * 0.08;
  const pos = (x: number) => (hi === lo ? 50 : Math.max(0, Math.min(100, ((x - lo) / (hi - lo)) * 100)));
  return { lo, hi, pos };
}

/** Estado de un valor según sus bandas: ideal → ok, dentro de advertencia → warning, si no crítico. */
export function estadoDeValor(v: number, b: Bandas): Status {
  if (v >= b.ideal[0] && v <= b.ideal[1]) return 'ok';
  if (v >= b.warn[0] && v <= b.warn[1]) return 'warning';
  return 'critical';
}

/** Número con coma decimal, como se escribe en el vivero. */
export const fmtNum = (v: number, dec = 0) =>
  v.toLocaleString('es-AR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
