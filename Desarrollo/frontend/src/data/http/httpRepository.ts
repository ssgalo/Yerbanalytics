/* ============================================================
   Repositorio HTTP: consume el backend Spring Boot real
   (GET {baseUrl}/nursery y demás endpoints). Se activa con
   VITE_DATA_SOURCE=http.

   Todas las llamadas pasan por `apiFetch`: llevan la cookie de sesión y un 401/403 se
   convierte en un error tipado y en un aviso a la capa de sesión, igual en todos lados.
   ============================================================ */
import type { DataRepository, FiltroHistorial } from '@/data/repository';
import { apiFetch } from './apiFetch';
import { ParametrosInvalidosError } from '@/data/parametrosError';
import { ManualLockError } from '@/data/manualLockError';
import { TankLoadError } from '@/data/tankError';
import { PasadaRechazadaError } from '@/data/pasadaError';
import { PlanRustificacionError } from '@/data/planRustificacionError';
import { HORARIO_MEDIASOMBRA_DEFAULT } from '@/lib/planRustificacion';
import { SecuenciaRechazadaError } from '@/data/secuenciaError';
import type {
  ActionRecord,
  CambioParametro,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  Detection,
  DiagnosisCriteria,
  ErrorParametro,
  DisposicionTopologia,
  DiagnosisCard,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  ParametrosSecuencia,
  ManualLock,
  NewManualLock,
  NewTankLoad,
  Supply,
  TankLevel,
  Pasada,
  Secuencia,
  TipoSecuencia,
  TopologiaVivero,
  TrazaEvaluacion,
  NuevoPlanRustificacion,
  PlanRustificacion,
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
    const res = await apiFetch(`${this.baseUrl}/nursery?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el vivero desde ${this.baseUrl}`);
    }
    const data = (await res.json()) as NurseryData;

    const diagnoses = data.diagnoses.map((d) => this.absolutizarImagen(normalizeCard(d)));
    const diagById: NurseryData['diagById'] = {};
    diagnoses.forEach((d) => (diagById[d.id] = d));

    return {
      ...data,
      diagnoses,
      diagById,
      recentDiag: data.recentDiag.map((d) => this.absolutizarImagen(normalizeCard(d))),
    };
  }

  async getHistory(filtro: FiltroHistorial = {}): Promise<ActionRecord[]> {
    const params = new URLSearchParams({ t: String(Date.now()) });
    if (filtro.tipo) params.set('tipo', filtro.tipo);
    if (filtro.sector) params.set('sector', filtro.sector);
    if (filtro.desde != null) params.set('desde', String(filtro.desde));
    if (filtro.hasta != null) params.set('hasta', String(filtro.hasta));
    if (filtro.limit != null) params.set('limit', String(filtro.limit));
    const res = await apiFetch(`${this.baseUrl}/historial?${params}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el historial desde ${this.baseUrl}`);
    }
    return (await res.json()) as ActionRecord[];
  }

  async getConfig(): Promise<Configuracion> {
    const res = await apiFetch(`${this.baseUrl}/configuracion?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la configuración desde ${this.baseUrl}`);
    }
    return normalizeConfig((await res.json()) as Configuracion);
  }

  async saveConfig(config: Configuracion): Promise<Configuracion> {
    const res = await apiFetch(`${this.baseUrl}/configuracion`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(config),
    });
    if (!res.ok) {
      // El backend devuelve { error } con el motivo de la validación (HU-15 CA-03).
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al guardar la configuración`);
    }
    return normalizeConfig((await res.json()) as Configuracion);
  }

  async getDiagnosisCriteria(): Promise<DiagnosisCriteria> {
    const res = await apiFetch(`${this.baseUrl}/configuracion/diagnostico?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el criterio de diagnóstico desde ${this.baseUrl}`);
    }
    return (await res.json()) as DiagnosisCriteria;
  }

  async saveDiagnosisCriteria(criteria: DiagnosisCriteria): Promise<DiagnosisCriteria> {
    const res = await apiFetch(`${this.baseUrl}/configuracion/diagnostico`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(criteria),
    });
    if (!res.ok) {
      // Un 400 trae { error } con un mensaje apto para mostrar (design D12).
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al guardar el criterio de diagnóstico`);
    }
    return (await res.json()) as DiagnosisCriteria;
  }

  async getDiagnosisDetections(diagnosisId: string): Promise<Detection[]> {
    const res = await apiFetch(`${this.baseUrl}/diagnosticos/${encodeURIComponent(diagnosisId)}/detecciones`);
    if (!res.ok) {
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al obtener las detecciones de ${diagnosisId}`);
    }
    const body = (await res.json()) as { detections?: Detection[] | null };
    return body.detections ?? [];
  }

  async getPlanRustificacion(zonaId: string): Promise<PlanRustificacion | null> {
    const res = await apiFetch(`${this.baseUrl}/zonas/${encodeURIComponent(zonaId)}/rustificacion?t=${Date.now()}`);
    // 204: la macro-zona no tiene un plan vigente.
    if (res.status === 204) return null;
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener el plan de rustificación de ${zonaId}`);
    }
    return (await res.json()) as PlanRustificacion;
  }

  async iniciarPlanRustificacion(zonaId: string, plan: NuevoPlanRustificacion): Promise<PlanRustificacion> {
    return this.postPlan(`${this.baseUrl}/zonas/${encodeURIComponent(zonaId)}/rustificacion`, 'iniciar', plan);
  }

  async cancelarPlanRustificacion(zonaId: string): Promise<PlanRustificacion> {
    return this.postPlan(`${this.baseUrl}/zonas/${encodeURIComponent(zonaId)}/rustificacion/cancelar`, 'cancelar');
  }

  /** POST de los planes: 400 y 409 traen { error } y son rechazos esperables, no una falla. */
  private async postPlan(url: string, accion: string, body?: unknown): Promise<PlanRustificacion> {
    const res = await apiFetch(url, {
      method: 'POST',
      ...(body !== undefined && { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    });
    if (res.status === 400 || res.status === 409) {
      const err = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new PlanRustificacionError(err?.error ?? `No se pudo ${accion} el plan de rustificación.`);
    }
    if (!res.ok) {
      throw new Error(`Error ${res.status} al ${accion} el plan de rustificación`);
    }
    return (await res.json()) as PlanRustificacion;
  }

  async getManualLocks(): Promise<ManualLock[]> {
    const res = await apiFetch(`${this.baseUrl}/bloqueos?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener los bloqueos manuales`);
    }
    return (await res.json()) as ManualLock[];
  }

  async activateManualLock(lock: NewManualLock): Promise<ManualLock> {
    const res = await apiFetch(`${this.baseUrl}/bloqueos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(lock),
    });
    await this.rechazoDeBloqueo(res, 'bloquear');
    return (await res.json()) as ManualLock;
  }

  async releaseManualLock(id: number): Promise<void> {
    const res = await apiFetch(`${this.baseUrl}/bloqueos/${id}`, { method: 'DELETE' });
    await this.rechazoDeBloqueo(res, 'reanudar');
  }

  /** 400, 404 y 409 del bloqueo traen { error } y son rechazos esperables, no una falla. */
  private async rechazoDeBloqueo(res: Response, accion: string): Promise<void> {
    if (res.status === 400 || res.status === 404 || res.status === 409) {
      const err = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new ManualLockError(err?.error ?? `No se pudo ${accion} la actuación autónoma.`);
    }
    if (!res.ok) {
      throw new Error(`Error ${res.status} al ${accion} la actuación autónoma`);
    }
  }

  async getTanks(): Promise<TankLevel[]> {
    const res = await apiFetch(`${this.baseUrl}/tanques?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener los tanques de insumo`);
    }
    return (await res.json()) as TankLevel[];
  }

  async loadTank(zoneId: string, supply: Supply, load: NewTankLoad): Promise<TankLevel> {
    const res = await apiFetch(
      `${this.baseUrl}/tanques/${encodeURIComponent(zoneId)}/${supply}/recarga`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(load),
      },
    );
    // 400 y 404 traen { error } y son rechazos esperables (no entra, falta la capacidad), no una falla.
    if (res.status === 400 || res.status === 404) {
      const err = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new TankLoadError(err?.error ?? 'No se pudo registrar la carga del tanque.');
    }
    if (!res.ok) {
      throw new Error(`Error ${res.status} al registrar la carga del tanque`);
    }
    return (await res.json()) as TankLevel;
  }

  async getHardware(): Promise<HardwareData> {
    const res = await apiFetch(`${this.baseUrl}/hardware?t=${Date.now()}`);
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

  async registerRepair(id: string, detalle: string): Promise<HardwareData> {
    const res = await apiFetch(`${this.baseUrl}/hardware/${encodeURIComponent(id)}/reparacion`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ detalle }),
    });
    if (!res.ok) {
      // 400 (sin detalle o dispositivo inexistente) y 409 (sin falla registrada) traen { error } con el motivo.
      const body = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new Error(body?.error ?? `Error ${res.status} al registrar la reparación`);
    }
    return (await res.json()) as HardwareData;
  }

  /** POST/PUT compartidos: el backend devuelve la flota actualizada o { error } (HU-18 CA-03). */
  private async mutateDevice(
    method: 'POST' | 'PUT',
    url: string,
    device: NuevoDispositivo,
  ): Promise<HardwareData> {
    const res = await apiFetch(url, {
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
    const res = await apiFetch(`${this.baseUrl}/topologia?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la topología desde ${this.baseUrl}`);
    }
    return (await res.json()) as TopologiaVivero;
  }

  async generarTopologia(input: NuevaTopologia): Promise<TopologiaVivero> {
    const res = await apiFetch(`${this.baseUrl}/topologia`, {
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
    const res = await apiFetch(`${this.baseUrl}/topologia/disposicion`, {
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
    const res = await apiFetch(`${this.baseUrl}/rules/parametros?t=${Date.now()}`);
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener los parámetros del motor desde ${this.baseUrl}`);
    }
    return (await res.json()) as CatalogoReglas;
  }

  async saveParametros(cambios: CambioParametro[]): Promise<CatalogoReglas> {
    const res = await apiFetch(`${this.baseUrl}/rules/parametros`, {
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
    const res = await apiFetch(`${this.baseUrl}/rules/schema`);
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
    const res = await apiFetch(
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
    const res = await apiFetch(url, { method: 'POST' });
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
    const res = await apiFetch(`${this.baseUrl}/pasadas/actual?t=${Date.now()}`);
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

  /** POST de las secuencias: 400 y 409 traen { error } y son rechazos esperables, no una falla. */
  private async postSecuencia(url: string, accion: string, body?: unknown): Promise<Secuencia> {
    const init: RequestInit = { method: 'POST' };
    if (body !== undefined) {
      init.headers = { 'Content-Type': 'application/json' };
      init.body = JSON.stringify(body);
    }
    const res = await fetch(url, init);
    if (res.status === 400 || res.status === 409) {
      const cuerpo = (await res.json().catch(() => null)) as { error?: string } | null;
      throw new SecuenciaRechazadaError(cuerpo?.error ?? `No se pudo ${accion} la secuencia.`);
    }
    if (!res.ok) {
      throw new Error(`Error ${res.status} al ${accion} la secuencia`);
    }
    return (await res.json()) as Secuencia;
  }

  async iniciarSecuencia(tipo: TipoSecuencia, parametros: ParametrosSecuencia = {}): Promise<Secuencia> {
    return this.postSecuencia(`${this.baseUrl}/secuencias`, 'iniciar', { tipo, parametros });
  }

  async getSecuenciaActual(): Promise<Secuencia | null> {
    const res = await fetch(`${this.baseUrl}/secuencias/actual?t=${Date.now()}`);
    // 204: todavía no hubo ninguna secuencia desde el arranque del backend.
    if (res.status === 204) return null;
    if (!res.ok) {
      throw new Error(`Error ${res.status} al obtener la secuencia`);
    }
    return (await res.json()) as Secuencia;
  }

  async cancelarSecuencia(): Promise<Secuencia> {
    return this.postSecuencia(`${this.baseUrl}/secuencias/actual/cancelar`, 'cancelar');
  }
}

/**
 * Tarjeta de diagnóstico con los campos del diagnóstico por detecciones siempre presentes: un
 * backend anterior al cambio no los manda, y la vista los trata como "sin nivel" y sin cajas.
 */
function normalizeCard(d: DiagnosisCard): DiagnosisCard {
  return { ...d, confidence: d.confidence ?? null, detectionCount: d.detectionCount ?? 0 };
}

/**
 * Completa los campos que el backend todavía no manda (filas previas a una migración, o campos
 * que aún no implementa, como el horario por defecto de la mediasombra).
 */
function normalizeConfig(data: Configuracion): Configuracion {
  if (data.operativa) {
    const op = data.operativa;
    op.intervaloSensadoMinutos = op.intervaloSensadoMinutos ?? 240;
    op.pasadaDiariaActiva = op.pasadaDiariaActiva ?? false;
    op.pasadaDiariaHora = op.pasadaDiariaHora ?? '11:00';
    op.mediasombraAbiertaDesde = op.mediasombraAbiertaDesde ?? HORARIO_MEDIASOMBRA_DEFAULT.desde;
    op.mediasombraAbiertaHasta = op.mediasombraAbiertaHasta ?? HORARIO_MEDIASOMBRA_DEFAULT.hasta;
  }
  return data;
}
