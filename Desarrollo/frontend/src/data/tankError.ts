/* ============================================================
   La carga de un tanque no se pudo registrar (el backend respondió 400 o 404). El mensaje es el del
   backend ("La carga no entra: el tanque quedaría en…", "El tanque todavía no está registrado…") y la
   vista lo muestra tal cual.
   ============================================================ */
export class TankLoadError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'TankLoadError';
  }
}
