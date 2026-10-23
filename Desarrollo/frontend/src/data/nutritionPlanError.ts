/* ============================================================
   El intervalo del plan de nutrición no se pudo guardar (el backend respondió 400 o 404). El mensaje es el del
   backend ("El intervalo del plan de nutrición debe estar entre 3 y 30 días.") y la vista lo muestra tal cual.
   ============================================================ */
export class NutritionPlanError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'NutritionPlanError';
  }
}
