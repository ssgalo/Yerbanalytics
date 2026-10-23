/* ============================================================
   El backend rechazó prender o apagar una regla (400). `codigo` es el del backend:
   `MOTIVO_REQUERIDO`, `CONFIRMACION_REQUERIDA` (trae la `consecuencia` de apagar la protección),
   `MOTIVO_DEMASIADO_LARGO` o `ACTIVA_REQUERIDA`. La vista decide qué hacer con cada uno; el
   mensaje es el del backend y se muestra tal cual.
   ============================================================ */
export type CodigoEstadoRegla =
  | 'ACTIVA_REQUERIDA'
  | 'MOTIVO_REQUERIDO'
  | 'CONFIRMACION_REQUERIDA'
  | 'MOTIVO_DEMASIADO_LARGO';

export class EstadoReglaError extends Error {
  constructor(
    readonly codigo: CodigoEstadoRegla | string,
    mensaje: string,
    readonly consecuencia: string | null = null,
  ) {
    super(mensaje);
    this.name = 'EstadoReglaError';
  }
}
