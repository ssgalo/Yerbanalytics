/* ============================================================
   API pública de la capa de datos + factory por entorno.
   Todo el resto de la app importa desde '@/data'.
   ============================================================ */
import type { DataRepository } from './repository';
import { HttpRepository } from './http/httpRepository';
import { MockRepository } from './mock/mockRepository';

export type { DataRepository } from './repository';
export { selectSectorDetail } from './selectors';

let httpInstance: HttpRepository | null = null;
let mockInstance: MockRepository | null = null;

/**
 * Repositorio http contra el backend real. Singleton.
 *
 * Se expone aparte de {@link getRepository} porque el dashboard, aunque corra en modo
 * mock, necesita leer el estado de simulación (modo/sensores) del backend —fuente de
 * verdad compartida entre el dashboard (:5173) y el simulador (:5180)— y servir el vivero
 * real cuando el modo es simulación.
 */
export function getHttpRepository(): HttpRepository {
  if (!httpInstance) {
    const baseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';
    httpInstance = new HttpRepository(baseUrl);
  }
  return httpInstance;
}

/** Repositorio mock determinístico (demo hardcodeada del vivero). Singleton. */
export function getMockRepository(): MockRepository {
  if (!mockInstance) {
    const seed = Number(import.meta.env.VITE_MOCK_SEED ?? 20260613);
    mockInstance = new MockRepository(Number.isFinite(seed) ? seed : 20260613);
  }
  return mockInstance;
}

/** Devuelve el repositorio según VITE_DATA_SOURCE (singleton). */
export function getRepository(): DataRepository {
  const source = import.meta.env.VITE_DATA_SOURCE ?? 'mock';
  return source === 'http' ? getHttpRepository() : getMockRepository();
}
