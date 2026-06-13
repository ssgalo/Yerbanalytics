/* ============================================================
   Repositorio mock: genera el vivero determinístico una sola vez
   y lo cachea. Implementa el mismo contrato que el backend real.
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type { NurseryData } from '@/types/domain';
import { buildNursery } from './generators';

export class MockRepository implements DataRepository {
  private cache: NurseryData | null = null;

  constructor(private readonly seed: number) {}

  async getNursery(): Promise<NurseryData> {
    if (!this.cache) {
      this.cache = buildNursery(this.seed);
    }
    return this.cache;
  }
}
