/* ============================================================
   Repositorio HTTP: consume el backend Spring Boot real
   (GET {baseUrl}/nursery y demás endpoints). Se activa con
   VITE_DATA_SOURCE=http.
   ============================================================ */
import type { DataRepository } from '@/data/repository';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import { PasadaRechazadaError } from '@/data/pasadaError';
import type {
  ActionRecord,
  CambioParametro,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  ErrorParametro,
  DisposicionTopologia,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  Pasada,
  TopologiaVivero,
  TrazaEvaluacion,
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
    const data = (await res.json()) as Configuracion;
    // Normalizar campos nuevos que pueden venir null si la fila existía antes de la migración
    if (data.operativa) {
      data.operativa.intervaloSensadoMinutos = data.operativa.intervaloSensadoMinutos ?? 240;
      data.operativa.intervaloEvaluacionMinutos = data.operativa.intervaloEvaluacionMinutos ?? 5;
    }
    return data;
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

  async getCatalogoReglas(): Promise<CatalogoReglas> {
    const res = await fetch(`${this.baseUrl}/rules/parametros?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener los parámetros del motor desde ${this.baseUrl}`);
    }
    return (await res.json()) as CatalogoReglas;
  }

  async saveParametros(cambios: CambioParametro[]): Promise<CatalogoReglas> {
    const res = await fetch(`${this.baseUrl}/rules/parametros`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cambios }),
    });
    if (!res.ok) {
      // Un 400 de validación trae { errores: [{ clave, mensaje }] }; uno por JSON inválido
      // puede venir sin `errores` (o sin cuerpo), así que se tolera cualquiera de las formas.
      const body = (await res.json().catch(() => null)) as {
        errores?: ErrorParametro[];
        error?: string;
        message?: string;
      } | null;
      if (Array.isArray(body?.errores) && body.errores.length > 0) {
        throw new ParametrosInvalidosError(body.errores);
      }
      throw new Error(
        body?.error ?? body?.message ?? `Error ${res.status} al guardar los parámetros del motor`,
      );
    }
    return (await res.json()) as CatalogoReglas;
  }

  async getRuleSchema(): Promise<DagSchema> {
    const res = await fetch(`${this.baseUrl}/rules/schema`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el esquema del motor`);
    }
    return (await res.json()) as DagSchema;
  }

  async getTrazaEvaluacion(
    sectorId: string,
    origen?: OrigenEvaluacion,
  ): Promise<TrazaEvaluacion | null> {
    const filtro = origen ? `origen=${origen}&` : '';
    const res = await fetch(
      `${this.baseUrl}/rules/evaluaciones/${encodeURIComponent(sectorId)}?${filtro}t=${Date.now()}`,
    );
    // 204: el sector existe pero el motor todavía no lo evaluó desde el arranque.
    if (res.status === 204) return null;
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la evaluación del sector ${sectorId}`);
    }
    return (await res.json()) as TrazaEvaluacion;
  }

  /** Absolutiza la miniatura de cada paso, igual que las de los diagnósticos. */
  private resolverPasada(p: Pasada): Pasada {
    return { ...p, pasos: p.pasos.map((paso) => this.absolutizarImagen(paso)) };
  }

  /** POST sin body de las pasadas: un 409 trae { error } y es un rechazo esperable, no una falla. */
  private async postPasada(url: string, accion: string): Promise<Pasada> {
    const res = await fetch(url, { method: 'POST' });
    if (res.status === 409) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new PasadaRechazadaError(body?.error ?? `No se pudo ${accion} la pasada.`);
    }
    if (!res.ok) {
      throw new Error(`Error ${res.status} al ${accion} la pasada`);
    }
    return this.resolverPasada((await res.json()) as Pasada);
  }

  async iniciarPasada(): Promise<Pasada> {
    return this.postPasada(`${this.baseUrl}/pasadas`, 'iniciar');
  }

  async getPasadaActual(): Promise<Pasada | null> {
    const res = await fetch(`${this.baseUrl}/pasadas/actual?t=${Date.now()}`);
    // 204: todavía no hubo ninguna pasada desde el arranque del backend.
    if (res.status === 204) return null;
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la pasada del riel`);
    }
    return this.resolverPasada((await res.json()) as Pasada);
  }

  async cancelarPasada(): Promise<Pasada> {
    return this.postPasada(`${this.baseUrl}/pasadas/actual/cancelar`, 'cancelar');
  }

  async getDemoExpo(): Promise<boolean> {
    const res = await fetch(`${this.baseUrl}/configuracion/demo-expo?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la preferencia de Demo Expo`);
    }
    return ((await res.json()) as { visible: boolean }).visible === true;
  }

  async setDemoExpo(visible: boolean): Promise<boolean> {
    const res = await fetch(`${this.baseUrl}/configuracion/demo-expo`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ visible }),
    });
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al guardar la preferencia de Demo Expo`);
    }
    return ((await res.json()) as { visible: boolean }).visible === true;
  }
}
