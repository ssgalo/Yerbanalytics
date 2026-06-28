/* ============================================================
   Repositorio mock: genera el vivero determinístico una sola vez
   y lo cachea. Implementa el mismo contrato que el backend real.
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type { ActionRecord, Configuracion, NurseryData } from '@/types/domain';
import { validateConfig } from '@/lib/configValidation';
import { buildConfig } from './config';
import { buildNursery } from './generators';
import { buildHistory } from './history';

export class MockRepository implements DataRepository {
  private cache: NurseryData | null = null;
  private historyCache: ActionRecord[] | null = null;
  private configCache: Configuracion | null = null;

  constructor(private readonly seed: number) {}

  async getNursery(): Promise<NurseryData> {
    if (!this.cache) {
      this.cache = buildNursery(this.seed);
    }
    return this.cache;
  }

  async getHistory(): Promise<ActionRecord[]> {
    if (!this.historyCache) {
      this.historyCache = buildHistory(this.seed);
    }
    return this.historyCache;
  }

  async getConfig(): Promise<Configuracion> {
    if (!this.configCache) {
      this.configCache = buildConfig();
    }
    return structuredClone(this.configCache);
  }

  async saveConfig(config: Configuracion): Promise<Configuracion> {
    // Mismo comportamiento que el backend: rechaza configuraciones inválidas (HU-15 CA-03).
    const errors = validateConfig(config);
    if (errors.length > 0) {
      throw new Error(errors[0]);
    }
    const saved: Configuracion = {
      ...structuredClone(config),
      operativa: {
        ...config.operativa,
        updatedBy: 'Ingeniero Agrónomo',
        updatedTs: Date.now(),
      },
    };
    this.configCache = saved;
    return structuredClone(saved);
  }
}
