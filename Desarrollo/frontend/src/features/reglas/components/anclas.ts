/* Ids de las filas y de los campos (los usan `?regla=` y los tests), y la fecha corta del último cambio. */
import type { ParametroRegla } from '@/types/domain';

/** id de la fila de una pieza: `?regla=` la despliega y lleva hasta ella. */
export const idFila = (id: string) => `regla-${id}`;

/** id del campo de una copia: único por `(regla, clave)`, así dos piezas con la misma clave no chocan. */
export const idCampo = (p: Pick<ParametroRegla, 'regla' | 'clave'>) => `param-${p.regla}-${p.clave}`;

/** "22/10 14:05". */
export function fechaCorta(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
