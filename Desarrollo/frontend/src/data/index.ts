/* ============================================================
   API pública de la capa de datos + factory por entorno.
   Todo el resto de la app importa desde '@/data'.
   ============================================================ */
import type { DataRepository } from './repository';
import { HttpRepository } from './http/httpRepository';
import { MockRepository } from './mock/mockRepository';

export type { DataRepository } from './repository';
export { selectSectorDetail } from './selectors';

let instance: DataRepository | null = null;

/** Devuelve el repositorio según VITE_DATA_SOURCE (singleton). */
export function getRepository(): DataRepository {
  if (instance) return instance;

  const source = import.meta.env.VITE_DATA_SOURCE ?? 'mock';

  if (source === 'http') {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';
    instance = new HttpRepository(baseUrl);
  } else {
    const seed = Number(import.meta.env.VITE_MOCK_SEED ?? 20260613);
    instance = new MockRepository(Number.isFinite(seed) ? seed : 20260613);
  }

  return instance;
}
