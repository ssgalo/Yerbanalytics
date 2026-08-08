/* ============================================================
   API pública de la capa de datos + factory por entorno.
   Todo el resto de la app importa desde '@/data'.
   ============================================================ */
import type { DataRepository } from './repository';
import { HttpRepository } from './http/httpRepository';
import { MockRepository } from './mock/mockRepository';

export type { DataRepository } from './repository';
export { selectSectorDetail, selectSensadoTiles, selectSerieMetrica } from './selectors';

let instancia: DataRepository | null = null;

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
    if ((import.meta.env.VITE_DATA_SOURCE ?? 'mock') === 'http') {
      instancia = new HttpRepository(import.meta.env.VITE_API_BASE_URL ?? '/api');
    } else {
      const seed = Number(import.meta.env.VITE_MOCK_SEED ?? 20260613);
      instancia = new MockRepository(Number.isFinite(seed) ? seed : 20260613);
    }
  }
  return instancia;
}
