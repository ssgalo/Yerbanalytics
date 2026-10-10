/* ============================================================
   API pública de la capa de datos + factory por entorno.
   Todo el resto de la app importa desde '@/data'.
   ============================================================ */
import type { DataRepository } from './repository';
import type { SeguridadRepository } from './seguridadRepository';
import { HttpRepository } from './http/httpRepository';
import { HttpSeguridadRepository } from './http/httpSeguridadRepository';
import { MockRepository } from './mock/mockRepository';
import { MockSeguridadRepository } from './mock/mockSeguridadRepository';

export type { DataRepository } from './repository';
export type { SeguridadRepository } from './seguridadRepository';
export { ParametrosInvalidosError } from './parametrosError';
export { PasadaRechazadaError } from './pasadaError';
export {
  CambioClaveRequeridoError,
  CredencialesIncorrectasError,
  OperacionRechazadaError,
  PermisoDenegadoError,
  SesionCerradaError,
  SesionNoEstablecidaError,
  SIN_PERMISO_ACCION,
} from './seguridadErrores';
export { escucharSesion, type OyenteSesion } from './sesionEventos';
export { selectSectorDetail, selectSensadoTiles, selectSerieMetrica } from './selectors';

let instancia: DataRepository | null = null;
let seguridad: SeguridadRepository | null = null;

const esHttp = () => (import.meta.env.VITE_DATA_SOURCE ?? 'mock') === 'http';
const baseUrl = () => import.meta.env.VITE_API_BASE_URL ?? '/api';

/**
 * Repositorio de la aplicación, según `VITE_DATA_SOURCE`. Singleton.
 *
 * Este es el ÚNICO punto donde se decide el origen de los datos, y rige para todas las
 * secciones: vivero, mapa, sector, diagnósticos, hardware, configuración, historial y
 * topología. Que sea uno solo es lo que garantiza que la demo sea una demo completa y no
 * una pantalla con la mitad de los datos hardcodeados y la otra mitad del backend.
 *
 * - `http` → backend real. Es el sistema en producción.
 * - `mock` → demo determinística, ilustrativa y sin backend (valor por defecto).
 */
export function getRepository(): DataRepository {
  if (!instancia) {
    if (esHttp()) {
      instancia = new HttpRepository(baseUrl());
    } else {
      const seed = Number(import.meta.env.VITE_MOCK_SEED ?? 20260613);
      instancia = new MockRepository(Number.isFinite(seed) ? seed : 20260613);
    }
  }
  return instancia;
}

/**
 * Repositorio de sesión y seguridad, según el MISMO `VITE_DATA_SOURCE`. Singleton.
 *
 * Que la decisión sea la misma variable es lo que impide una sesión simulada sobre datos del
 * backend (o al revés): en `http` ambos hablan con el backend; en `mock`, ambos son la demo.
 */
export function getSeguridadRepository(): SeguridadRepository {
  if (!seguridad) {
    seguridad = esHttp() ? new HttpSeguridadRepository(baseUrl()) : new MockSeguridadRepository();
  }
  return seguridad;
}
