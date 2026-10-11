/* ============================================================
   Canal entre la capa de datos y la capa de sesión (`AuthProvider`).

   El que descubre que la sesión se cerró es quien hizo la petición —el sondeo del vivero, un
   guardado, lo que sea—, pero quien tiene que reaccionar (mostrar el login con el aviso) es el
   `AuthProvider`. En vez de que cada hook propague el 401 hacia arriba, el cliente HTTP (y el
   mock) avisan por acá y el provider escucha. Los errores igual se lanzan, para que quien
   esperaba la respuesta no la dé por buena.
   ============================================================ */
import type { MotivoCierre } from '@/types/seguridad';

export interface OyenteSesion {
  /** 401: la sesión no existe, venció o fue revocada. */
  sesionCerrada?: (motivo: MotivoCierre) => void;
  /** 403 por permiso: la UI mostraba habilitado algo que el backend negó. */
  permisoDenegado?: (permiso: string | null) => void;
  /** 403 con `CAMBIO_CLAVE_REQUERIDO`. */
  cambioClaveRequerido?: () => void;
}

const oyentes = new Set<OyenteSesion>();

/** Registra un oyente. Devuelve la función para darlo de baja. */
export function escucharSesion(oyente: OyenteSesion): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}

export function avisarSesionCerrada(motivo: MotivoCierre): void {
  oyentes.forEach((o) => o.sesionCerrada?.(motivo));
}

export function avisarPermisoDenegado(permiso: string | null): void {
  oyentes.forEach((o) => o.permisoDenegado?.(permiso));
}

export function avisarCambioClaveRequerido(): void {
  oyentes.forEach((o) => o.cambioClaveRequerido?.());
}
