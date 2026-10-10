/* ============================================================
   El único `fetch` del dashboard contra el backend. Toda llamada de `HttpRepository` y de
   `HttpSeguridadRepository` pasa por acá.

   - `credentials: 'include'`: la sesión viaja en la cookie `YERBA_SESION` (HttpOnly; la JS no
     la ve ni la necesita). Sin esto el navegador no la manda a otro puerto (:5173 → :8000).
   - Respuestas de seguridad, tratadas igual en todos lados:
       401 { motivo }                       → avisa a la sesión y lanza `SesionCerradaError`
       403 { motivo: CAMBIO_CLAVE_REQUERIDO } → avisa y lanza `CambioClaveRequeridoError`
       403 { permiso }                      → avisa (el perfil se recarga) y lanza `PermisoDenegadoError`
     Cualquier otro estado vuelve tal cual: cada llamada sigue interpretando sus 200/204/400/409.

   Los sondeos no necesitan ninguna marca para no contar como actividad: el backend sólo cuenta
   el login, las peticiones que no son GET y `POST /api/auth/actividad` (design D4). Un sondeo
   nuevo es un GET, así que nace del lado seguro.
   ============================================================ */
import {
  CambioClaveRequeridoError,
  PermisoDenegadoError,
  SesionCerradaError,
} from '@/data/seguridadErrores';
import { avisarCambioClaveRequerido, avisarPermisoDenegado, avisarSesionCerrada } from '@/data/sesionEventos';
import type { MotivoCierre } from '@/types/seguridad';

const MOTIVOS: readonly MotivoCierre[] = ['SIN_SESION', 'SESION_EXPIRADA', 'SESION_REVOCADA'];

export interface OpcionesApiFetch {
  /**
   * No avisar a la capa de sesión ante un 401/403: quien llama lo maneja. Lo usan el login
   * (cuyo 401 es "credenciales incorrectas", no una sesión cerrada) y la verificación de que la
   * cookie quedó puesta.
   */
  silencioso?: boolean;
}

interface CuerpoSeguridad {
  error?: string;
  motivo?: string;
  permiso?: string;
}

export async function apiFetch(url: string, init: RequestInit = {}, opciones: OpcionesApiFetch = {}): Promise<Response> {
  const res = await fetch(url, { ...init, credentials: 'include' });
  if (res.status !== 401 && res.status !== 403) return res;

  const cuerpo = (await res.json().catch(() => null)) as CuerpoSeguridad | null;

  if (res.status === 401) {
    // Un 401 sin motivo reconocible se trata como "sin sesión": es la lectura segura.
    const motivo = MOTIVOS.find((m) => m === cuerpo?.motivo) ?? 'SIN_SESION';
    if (!opciones.silencioso) avisarSesionCerrada(motivo);
    throw new SesionCerradaError(motivo);
  }

  if (cuerpo?.motivo === 'CAMBIO_CLAVE_REQUERIDO') {
    if (!opciones.silencioso) avisarCambioClaveRequerido();
    throw new CambioClaveRequeridoError();
  }

  const permiso = cuerpo?.permiso ?? null;
  if (!opciones.silencioso) avisarPermisoDenegado(permiso);
  throw new PermisoDenegadoError(permiso);
}
