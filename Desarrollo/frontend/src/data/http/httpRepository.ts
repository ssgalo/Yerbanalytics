/* ============================================================
   Repositorio HTTP: consume el backend real. Se activa con
   VITE_DATA_SOURCE=http. El backend aún no existe; este cliente
   ya define el contrato esperado (GET {baseUrl}/nursery).
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type { NurseryData } from '@/types/domain';

export class HttpRepository implements DataRepository {
  constructor(private readonly baseUrl: string) {}

  async getNursery(): Promise<NurseryData> {
    const res = await fetch(`${this.baseUrl}/nursery`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el vivero desde ${this.baseUrl}`);
    }
    return (await res.json()) as NurseryData;
  }
}
