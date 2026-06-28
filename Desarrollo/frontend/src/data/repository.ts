/* ============================================================
   Contrato de acceso a datos. La UI solo conoce esta interface;
   nunca sabe si detrás hay un mock o un backend real.
   ============================================================ */
import type { ActionRecord, Configuracion, NurseryData } from '@/types/domain';

export interface DataRepository {
  /** Devuelve el snapshot completo del vivero. */
  getNursery(): Promise<NurseryData>;
  /** Devuelve el historial global de acciones (más recientes primero). */
  getHistory(): Promise<ActionRecord[]>;
  /** Devuelve la configuración agronómica vigente (HU-15). */
  getConfig(): Promise<Configuracion>;
  /** Persiste la configuración validada y devuelve la versión guardada (HU-15). */
  saveConfig(config: Configuracion): Promise<Configuracion>;
}
