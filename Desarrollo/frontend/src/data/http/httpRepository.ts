/* ============================================================
   Repositorio HTTP: consume el backend Spring Boot real
   (GET {baseUrl}/nursery y demás endpoints). Se activa con
   VITE_DATA_SOURCE=http.
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import type {
  ActionRecord,
  CodigoVinculacion,
  DiagnosticoRegistrado,
  DispositivoCamara,
  NuevaOrdenCaptura,
  NuevoDiagnostico,
  OrdenCaptura,
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

  /**
   * Convierte la ruta de una imagen de captura en una URL absoluta contra el backend.
   *
   * El backend devuelve `/api/capturas/{id}/imagen`, una ruta relativa a la raíz — no puede
   * hacer otra cosa: no conoce su propia URL pública. Pero el navegador la resuelve contra el
   * origen de la PÁGINA, que es el servidor de Vite (`:5173`), no el backend (`:8000`). El
   * resultado es un 404 silencioso: la tarjeta cae a su gradiente de respaldo y parece que el
   * diagnóstico simplemente no tuviera foto.
   *
   * Este es el único lugar que sabe dónde vive el backend, así que la resolución va acá y no
   * en los componentes.
   */
  private absolutizarImagen<T extends { imagenUrl?: string | null }>(item: T): T {
    if (!item.imagenUrl || /^(https?:|data:|blob:)/.test(item.imagenUrl)) return item;
    return { ...item, imagenUrl: new URL(item.imagenUrl, this.baseUrl).toString() };
  }

  async getNursery(): Promise<NurseryData> {
    const res = await fetch(`${this.baseUrl}/nursery?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el vivero desde ${this.baseUrl}`);
    }
    const data = (await res.json()) as NurseryData;

    const diagnoses = data.diagnoses.map((d) => this.absolutizarImagen(d));
    const diagById: NurseryData['diagById'] = {};
    diagnoses.forEach((d) => (diagById[d.id] = d));

    return {
      ...data,
      diagnoses,
      diagById,
      recentDiag: data.recentDiag.map((d) => this.absolutizarImagen(d)),
    };
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
  private async mutateDevice(
    method: 'POST' | 'PUT',
    url: string,
    device: NuevoDispositivo,
  ): Promise<HardwareData> {
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
      throw new Error(
        `Error ${res.status} al obtener el estado de simulación desde ${this.baseUrl}`,
      );
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

  /* ----------------------------------------------------------------
     Captura de imágenes (HU-04 CA-01).

     Endpoints públicos de la plataforma. Ninguno es exclusivo del simulador: `ordenes` es
     el que usará el planificador de pasadas del riel y `diagnosticos` el que usará el
     servicio de inferencia. El contrato del dispositivo de captura (`/api/camara/v1/**`) NO
     se toca desde acá — ese lo implementa la app de cámara.
     ---------------------------------------------------------------- */

  async getDispositivosCamara(): Promise<DispositivoCamara[]> {
    const res = await fetch(`${this.baseUrl}/camara/dispositivos?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener los dispositivos de cámara`);
    }
    return (await res.json()) as DispositivoCamara[];
  }

  async generarCodigoVinculacion(): Promise<CodigoVinculacion> {
    const res = await fetch(`${this.baseUrl}/camara/vinculacion`, { method: 'POST' });
    if (!res.ok) {
      throw new Error(`Error ${res.status} al generar el código de vinculación`);
    }
    return (await res.json()) as CodigoVinculacion;
  }

  async emitirOrdenCaptura(input: NuevaOrdenCaptura): Promise<OrdenCaptura> {
    const res = await fetch(`${this.baseUrl}/capturas/ordenes`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al pedir la captura`);
    }
    return this.absolutizarImagen((await res.json()) as OrdenCaptura);
  }

  async getOrdenCaptura(ordenId: string): Promise<OrdenCaptura> {
    const res = await fetch(`${this.baseUrl}/capturas/ordenes/${encodeURIComponent(ordenId)}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al consultar la orden de captura`);
    }
    return this.absolutizarImagen((await res.json()) as OrdenCaptura);
  }

  async crearDiagnostico(input: NuevoDiagnostico): Promise<DiagnosticoRegistrado> {
    const res = await fetch(`${this.baseUrl}/diagnosticos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al registrar el diagnóstico`);
    }
    return this.absolutizarImagen((await res.json()) as DiagnosticoRegistrado);
  }
}
