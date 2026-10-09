/* ============================================================
   La secuencia no se pudo iniciar o cancelar (el backend respondió 400 o 409). El mensaje es
   el del backend ("Ya hay una secuencia en curso.", "Hay una pasada del riel en curso."…) y
   la vista lo muestra tal cual.
   ============================================================ */
export class SecuenciaRechazadaError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'SecuenciaRechazadaError';
  }
}
