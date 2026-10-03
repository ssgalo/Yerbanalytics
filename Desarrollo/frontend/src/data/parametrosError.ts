/* ============================================================
   Error de validación del catálogo de parámetros. Lo lanzan el repositorio HTTP (ante un 400
   con `errores`) y el mock (con la misma validación que la UI), para que la vista muestre cada
   mensaje junto a su parámetro sin saber de dónde viene.
   ============================================================ */
import type { ErrorParametro } from '@/types/domain';

export class ParametrosInvalidosError extends Error {
  constructor(readonly errores: ErrorParametro[]) {
    super(errores[0]?.mensaje ?? 'Parámetros inválidos.');
    this.name = 'ParametrosInvalidosError';
  }
}
