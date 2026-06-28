/* ============================================================
   Repositorio HTTP: consume el backend real. Se activa con
   VITE_DATA_SOURCE=http. El backend aún no existe; este cliente
   ya define el contrato esperado (GET {baseUrl}/nursery).
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type { ActionRecord, NurseryData } from '@/types/domain';

export class HttpRepository implements DataRepository {
  constructor(private readonly baseUrl: string) {}

  async getNursery(): Promise<NurseryData> {
    const res = await fetch(`${this.baseUrl}/nursery?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el vivero desde ${this.baseUrl}`);
    }
    return (await res.json()) as NurseryData;
  }

  async getHistory(): Promise<ActionRecord[]> {
    const res = await fetch(`${this.baseUrl}/historial?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el historial desde ${this.baseUrl}`);
    }
    return (await res.json()) as ActionRecord[];
  }
}
