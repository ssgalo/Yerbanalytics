/* ============================================================
   El bloqueo manual no se pudo activar o reanudar (el backend respondió 400, 404 o 409). El
   mensaje es el del backend ("El motivo del bloqueo es obligatorio.", "La macro-zona MZ-2 ya
   tiene un bloqueo vigente."…) y la vista lo muestra tal cual.
   ============================================================ */
export class ManualLockError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'ManualLockError';
  }
}
