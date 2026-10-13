/* ============================================================
   El plan de rustificación no se pudo iniciar o cancelar (el backend respondió 400 o 409). El
   mensaje es el del backend ("La macro-zona ya tiene un plan vigente.", "La fecha de inicio no
   puede ser anterior a hoy."…) y la vista lo muestra tal cual.
   ============================================================ */
export class PlanRustificacionError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'PlanRustificacionError';
  }
}
