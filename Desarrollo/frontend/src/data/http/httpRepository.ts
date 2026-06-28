/* ============================================================
   Repositorio HTTP: consume el backend real. Se activa con
   VITE_DATA_SOURCE=http. El backend aún no existe; este cliente
   ya define el contrato esperado (GET {baseUrl}/nursery).
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type { ActionRecord, Configuracion, NurseryData } from '@/types/domain';

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

  async getConfig(): Promise<Configuracion> {
    const res = await fetch(`${this.baseUrl}/configuracion?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la configuración desde ${this.baseUrl}`);
    }
    return (await res.json()) as Configuracion;
  }

  async saveConfig(config: Configuracion): Promise<Configuracion> {
    const res = await fetch(`${this.baseUrl}/configuracion`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });
    if (!res.ok) {
      // El backend devuelve { error } con el motivo de la validación (HU-15 CA-03).
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al guardar la configuración`);
    }
    return (await res.json()) as Configuracion;
  }
}
