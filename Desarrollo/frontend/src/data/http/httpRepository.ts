/* ============================================================
   Repositorio HTTP: consume el backend real. Se activa con
   VITE_DATA_SOURCE=http. El backend aún no existe; este cliente
   ya define el contrato esperado (GET {baseUrl}/nursery).
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type {
  ActionRecord,
  Configuracion,
  DisposicionTopologia,
  EnvioTelemetria,
  HardwareData,
  ModoSimulacion,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  SensorSimulado,
  SimulacionEstado,
  TopologiaVivero,
} from '@/types/domain';

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

  async getHardware(): Promise<HardwareData> {
    const res = await fetch(`${this.baseUrl}/hardware?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el hardware desde ${this.baseUrl}`);
    }
    return (await res.json()) as HardwareData;
  }

  async registerDevice(device: NuevoDispositivo): Promise<HardwareData> {
    return this.mutateDevice('POST', `${this.baseUrl}/hardware`, device);
  }

  async replaceDevice(id: string, device: NuevoDispositivo): Promise<HardwareData> {
    return this.mutateDevice('PUT', `${this.baseUrl}/hardware/${encodeURIComponent(id)}`, device);
  }

  /** POST/PUT compartidos: el backend devuelve la flota actualizada o { error } (HU-18 CA-03). */
  private async mutateDevice(method: 'POST' | 'PUT', url: string, device: NuevoDispositivo): Promise<HardwareData> {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(device),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al registrar el dispositivo`);
    }
    return (await res.json()) as HardwareData;
  }

  async getTopologia(): Promise<TopologiaVivero> {
    const res = await fetch(`${this.baseUrl}/topologia?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la topología desde ${this.baseUrl}`);
    }
    return (await res.json()) as TopologiaVivero;
  }

  async generarTopologia(input: NuevaTopologia): Promise<TopologiaVivero> {
    const res = await fetch(`${this.baseUrl}/topologia`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      // El backend devuelve { error } con el motivo (rango inválido 400 / conflicto 409).
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al generar la topología`);
    }
    return (await res.json()) as TopologiaVivero;
  }

  async guardarDisposicion(input: DisposicionTopologia): Promise<TopologiaVivero> {
    const res = await fetch(`${this.baseUrl}/topologia/disposicion`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      // El backend devuelve { error } con el motivo (rango inválido 400).
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al guardar la disposición`);
    }
    return (await res.json()) as TopologiaVivero;
  }

  async getSimulacionEstado(): Promise<SimulacionEstado> {
    const res = await fetch(`${this.baseUrl}/simulacion?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el estado de simulación desde ${this.baseUrl}`);
    }
    return (await res.json()) as SimulacionEstado;
  }

  async setModoSimulacion(modo: ModoSimulacion): Promise<SimulacionEstado> {
    const res = await fetch(`${this.baseUrl}/simulacion`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ modo }),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al cambiar el modo de simulación`);
    }
    return (await res.json()) as SimulacionEstado;
  }

  async getSensoresSimulados(): Promise<SensorSimulado[]> {
    const res = await fetch(`${this.baseUrl}/simulacion/sensores?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al listar los sensores simulados desde ${this.baseUrl}`);
    }
    return (await res.json()) as SensorSimulado[];
  }

  async crearSensorSimulado(input: SensorSimulado): Promise<SensorSimulado[]> {
    const res = await fetch(`${this.baseUrl}/simulacion/sensores`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      // 400 si el serial/MAC ya existe o falta la zona.
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al crear el sensor simulado`);
    }
    return this.getSensoresSimulados();
  }

  async eliminarSensorSimulado(serial: string): Promise<SensorSimulado[]> {
    const res = await fetch(
      `${this.baseUrl}/simulacion/sensores?serial=${encodeURIComponent(serial)}`,
      { method: 'DELETE' },
    );
    if (!res.ok) {
      throw new Error(`Error ${res.status} al eliminar el sensor simulado`);
    }
    return this.getSensoresSimulados();
  }

  async enviarTelemetria(input: EnvioTelemetria): Promise<void> {
    const res = await fetch(`${this.baseUrl}/simulacion/telemetria`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      // El backend devuelve { error }: 409 si la simulación está inactiva, 400 dato inválido,
      // 502 si falla la publicación al broker.
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al enviar la telemetría`);
    }
  }
}
