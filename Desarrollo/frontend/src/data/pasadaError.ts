/* ============================================================
   La pasada del riel no se pudo iniciar o cancelar (el backend respondió 409). El mensaje es
   el del backend ("Ya hay una pasada en curso.", "No hay ningún dispositivo de captura
   conectado."…) y la vista lo muestra tal cual.
   ============================================================ */
export class PasadaRechazadaError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = 'PasadaRechazadaError';
  }
}
