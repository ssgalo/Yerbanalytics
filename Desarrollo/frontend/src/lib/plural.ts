/* ============================================================
   Pluralización de cantidades: "1 cambio" / "2 cambios", "1 riego" / "2 riegos".
   Funciones puras; la tabla de singulares cubre las unidades del catálogo del motor.
   ============================================================ */

/** Singular de las unidades/palabras que el catálogo y la UI usan en plural. */
const SINGULARES: Record<string, string> = {
  riegos: 'riego',
  dosis: 'dosis',
  parámetros: 'parámetro',
  modificados: 'modificado',
  cambios: 'cambio',
  reglas: 'regla',
};

/** "1 cambio", "0 cambios", "3 cambios". */
export function contar(n: number, singular: string, plural: string): string {
  return `${n} ${n === 1 ? singular : plural}`;
}

/** La unidad que corresponde al valor: con 1 va en singular si se la conoce ("riegos" → "riego"). */
export function unidadSegunValor(unidad: string, valor: number | string): string {
  const n = typeof valor === 'number' ? valor : Number(valor);
  if (n !== 1) return unidad;
  return SINGULARES[unidad] ?? unidad;
}
