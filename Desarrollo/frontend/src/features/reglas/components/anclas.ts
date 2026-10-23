/* Ids de las tarjetas y de los campos, y el salto de un parámetro compartido al lugar donde se edita. */

export const idTarjeta = (id: string) => `regla-${id}`;

export const idParametro = (clave: string) => `param-${clave}`;

/** Lleva a un parámetro (el compartido se edita en el bloque de su grupo). */
export function irAlParametro(clave: string) {
  const el = document.getElementById(idParametro(clave));
  el?.scrollIntoView?.({ block: 'center' });
  el?.focus();
}

/** "22/10 14:05". */
export function fechaCorta(ts: number): string {
  const d = new Date(ts);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
