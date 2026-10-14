/* Cuántos valores cambió el borrador respecto de lo guardado (lo que cuenta la barra de guardado). */
import type { ConfigOperativa, Configuracion, MetricThreshold } from '@/types/domain';

const BANDAS: (keyof MetricThreshold)[] = [
  'idealMin',
  'idealMax',
  'warnMin',
  'warnMax',
  'critMin',
  'critMax',
  'alertMin',
  'alertMax',
];

/* Lo que el operador edita; quién y cuándo, y lo que calcula el backend, no cuentan. */
const OPERATIVA: (keyof ConfigOperativa)[] = [
  'insumoDosisMax24hMl',
  'seguimientoLatenciaMin',
  'seguimientoDeltaMin',
  'intervaloSensadoMinutos',
  'pasadaDiariaActiva',
  'pasadaDiariaHora',
  'mediasombraAbiertaDesde',
  'mediasombraAbiertaHasta',
];

/** Cada celda de umbral y cada campo operativo cuentan como un cambio. */
export function countChanges(guardada: Configuracion, borrador: Configuracion): number {
  let n = 0;
  for (const u of borrador.umbrales) {
    const antes = guardada.umbrales.find((g) => g.key === u.key);
    n += antes ? BANDAS.filter((k) => antes[k] !== u[k]).length : 1;
  }
  n += OPERATIVA.filter((k) => !Object.is(guardada.operativa[k], borrador.operativa[k])).length;

  return n;
}
