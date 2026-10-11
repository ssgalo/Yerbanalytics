/* ============================================================
   Errores tipados de seguridad. Los lanzan el cliente HTTP y el repositorio mock por igual,
   para que las vistas reaccionen sin saber de dónde vienen.
   ============================================================ */
import type { MotivoCierre } from '@/types/seguridad';

/** Mensaje uniforme para una acción que el backend rechazó por permisos. */
export const SIN_PERMISO_ACCION = 'No tenés permiso para esta acción.';

/** 401 de una petición con sesión: la sesión no existe, venció o fue revocada. */
export class SesionCerradaError extends Error {
  constructor(readonly motivo: MotivoCierre) {
    super(
      motivo === 'SESION_EXPIRADA'
        ? 'Tu sesión se cerró por inactividad.'
        : motivo === 'SESION_REVOCADA'
          ? 'Tu cuenta cambió; volvé a iniciar sesión.'
          : 'No hay una sesión iniciada.',
    );
    this.name = 'SesionCerradaError';
  }
}

/**
 * 403 por permiso. El mensaje es siempre el mismo ("No tenés permiso para esta acción."), así
 * que una vista que ya muestra `error.message` queda con el aviso uniforme sin tocarla.
 */
export class PermisoDenegadoError extends Error {
  constructor(readonly permiso: string | null) {
    super(SIN_PERMISO_ACCION);
    this.name = 'PermisoDenegadoError';
  }
}

/** 403 con `CAMBIO_CLAVE_REQUERIDO`: la clave es temporal y hay que cambiarla antes de seguir. */
export class CambioClaveRequeridoError extends Error {
  constructor() {
    super('Tenés que cambiar la contraseña antes de seguir.');
    this.name = 'CambioClaveRequeridoError';
  }
}

/**
 * Login fallido. El mensaje es genérico a propósito (HU-01 CA-02): no dice si falló el
 * usuario, la clave o si la cuenta está suspendida.
 */
export class CredencialesIncorrectasError extends Error {
  constructor() {
    super('Credenciales incorrectas');
    this.name = 'CredencialesIncorrectasError';
  }
}

/**
 * El login respondió 200 pero la cookie no volvió en la petición siguiente. El caso típico es
 * el esquema mixto: dashboard por http y API por https (o hosts distintos), que el navegador
 * considera sitios distintos y no le manda la cookie `SameSite=Strict`.
 */
export class SesionNoEstablecidaError extends Error {
  constructor() {
    super(
      'No se pudo establecer la sesión: el navegador no envió la cookie. Revisá que el dashboard y la API usen el mismo esquema (http/https) y el mismo host.',
    );
    this.name = 'SesionNoEstablecidaError';
  }
}

/** 400 (validación) o 409 (conflicto) de la gestión de seguridad, con el mensaje del backend. */
export class OperacionRechazadaError extends Error {
  constructor(
    readonly status: 400 | 409,
    mensaje: string,
  ) {
    super(mensaje);
    this.name = 'OperacionRechazadaError';
  }
}
