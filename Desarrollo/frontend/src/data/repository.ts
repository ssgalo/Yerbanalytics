/* ============================================================
   Contrato de acceso a datos. La UI solo conoce esta interface;
   nunca sabe si detrás hay un mock o un backend real.

   Cubre el dominio del dashboard y nada más. Las pasadas del riel (Demo Expo) SÍ son
   superficie del dashboard. Las demás piezas de captura —órdenes, dispositivos de cámara, alta
   de diagnósticos— NO se declaran acá: son superficie pública de la plataforma, para el
   planificador de pasadas y el servicio de inferencia, y ninguna vista las consume. La CONSULTA
   de las detecciones de un diagnóstico y el criterio de diagnóstico sí son del dashboard.
   ============================================================ */
import type {
  ActionRecord,
  CambioParametro,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  Detection,
  DiagnosisCriteria,
  DisposicionTopologia,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  ManualLock,
  NewManualLock,
  NewTankLoad,
  Supply,
  TankLevel,
  ParametrosSecuencia,
  Pasada,
  Secuencia,
  TipoSecuencia,
  TopologiaVivero,
  TrazaEvaluacion,
  NuevoPlanRustificacion,
  PlanRustificacion,
} from '@/types/domain';

/** Filtros del historial que resuelve el origen de datos (los mismos que `GET /api/historial`). */
export interface FiltroHistorial {
  tipo?: string;
  /** Id exacto del sector (p. ej. `MZ-2-014`). */
  sector?: string;
  /** Epoch ms, inclusive. */
  desde?: number;
  /** Epoch ms, inclusive. */
  hasta?: number;
  /**
   * Tope de eventos (los más recientes). El backend lo acota a su máximo y, si no se pide, aplica el suyo
   * por defecto: nunca devuelve el historial entero.
   */
  limit?: number;
}

export interface DataRepository {
  /** Devuelve el snapshot completo del vivero. */
  getNursery(): Promise<NurseryData>;
  /**
   * Devuelve el historial global de acciones (más recientes primero). Con `filtro`, el recorte se
   * hace en el origen: el historial real puede tener cientos de miles de eventos.
   */
  getHistory(filtro?: FiltroHistorial): Promise<ActionRecord[]>;
  /** Devuelve la configuración agronómica vigente (HU-15). */
  getConfig(): Promise<Configuracion>;
  /** Persiste la configuración validada y devuelve la versión guardada (HU-15). */
  saveConfig(config: Configuracion): Promise<Configuracion>;
  /** Criterio agronómico del diagnóstico: bandas de severidad y orden de criticidad. */
  getDiagnosisCriteria(): Promise<DiagnosisCriteria>;
  /**
   * Reemplaza el criterio completo y devuelve el guardado. Si es inválido rechaza con un `Error`
   * cuyo mensaje es apto para mostrar (el `{ error }` del 400).
   */
  saveDiagnosisCriteria(criteria: DiagnosisCriteria): Promise<DiagnosisCriteria>;
  /**
   * Cajas guardadas con un diagnóstico (`DX-`). Rechaza si el id no existe (incluye los `DG-`
   * derivados del sector, que no tienen detecciones).
   */
  getDiagnosisDetections(diagnosisId: string): Promise<Detection[]>;
  /** Devuelve el estado técnico de la flota de hardware (HU-18 / HU-21). */
  getHardware(): Promise<HardwareData>;
  /** Da de alta un dispositivo (valida unicidad) y devuelve la flota actualizada (HU-18). */
  registerDevice(device: NuevoDispositivo): Promise<HardwareData>;
  /** Recambia un dispositivo reutilizando su registro y devuelve la flota actualizada (HU-21 CA-05). */
  replaceDevice(id: string, device: NuevoDispositivo): Promise<HardwareData>;
  /**
   * Registra la reparación de un dispositivo averiado (S-05, HU-21 CA-04): levanta la falla y lo devuelve a lo
   * autónomo. El detalle es obligatorio; devuelve la flota actualizada o rechaza con el motivo.
   */
  registerRepair(id: string, detalle: string): Promise<HardwareData>;
  /** Devuelve el resumen de la topología cargada en el vivero (HU-18 CA-01). */
  getTopologia(): Promise<TopologiaVivero>;
  /** Genera (o regenera) la grilla lógica del vivero y devuelve el resumen resultante (HU-18 CA-01). */
  generarTopologia(input: NuevaTopologia): Promise<TopologiaVivero>;
  /** Actualiza la disposición visual por fila sin regenerar la grilla (HU-18 CA-01). */
  guardarDisposicion(input: DisposicionTopologia): Promise<TopologiaVivero>;
  /**
   * Plan de rustificación vigente (programado o en curso) de una macro-zona; `null` si no tiene
   * (204). Sin plan, la mediasombra sigue el horario por defecto de la configuración.
   */
  getPlanRustificacion(zonaId: string): Promise<PlanRustificacion | null>;
  /** Inicia un plan. Rechaza con `PlanRustificacionError` si es inválido o la zona ya tiene uno. */
  iniciarPlanRustificacion(zonaId: string, plan: NuevoPlanRustificacion): Promise<PlanRustificacion>;
  /** Cancela el plan vigente. Rechaza con `PlanRustificacionError` si no hay ninguno. */
  cancelarPlanRustificacion(zonaId: string): Promise<PlanRustificacion>;
  /** Catálogo de umbrales del motor de reglas, agrupado por regla (cada parámetro una vez). */
  getCatalogoReglas(): Promise<CatalogoReglas>;
  /**
   * Aplica cambios al catálogo, todo o nada, y devuelve el catálogo actualizado. `valor: null`
   * restablece el valor de fábrica. Si algo es inválido rechaza con `ParametrosInvalidosError`.
   */
  saveParametros(cambios: CambioParametro[]): Promise<CatalogoReglas>;
  /** Esquema base del DAG del motor (topología estática, con los parámetros de cada regla). */
  getRuleSchema(): Promise<DagSchema>;
  /**
   * Última evaluación del motor para un sector (hoy hay un solo origen: la lectura de un pulso).
   * `null` si todavía no se evaluó desde el arranque.
   */
  getTrazaEvaluacion(sectorId: string, origen?: OrigenEvaluacion): Promise<TrazaEvaluacion | null>;
  /** Rechaza con `PasadaRechazadaError` (mensaje del 409) si no se puede iniciar. */
  iniciarPasada(): Promise<Pasada>;
  /** La pasada en curso o la última; `null` si no hubo ninguna desde que arrancó el backend (204). */
  getPasadaActual(): Promise<Pasada | null>;
  /** Rechaza con `PasadaRechazadaError` si no hay una pasada en curso. */
  cancelarPasada(): Promise<Pasada>;
  /** Rechaza con `SecuenciaRechazadaError` (mensaje del 400/409) si no se puede iniciar. */
  iniciarSecuencia(tipo: TipoSecuencia, parametros?: ParametrosSecuencia): Promise<Secuencia>;
  /** La secuencia en curso o la última; `null` si no hubo ninguna desde que arrancó el backend (204). */
  getSecuenciaActual(): Promise<Secuencia | null>;
  /** Rechaza con `SecuenciaRechazadaError` si no hay una secuencia en curso. */
  cancelarSecuencia(): Promise<Secuencia>;
  /** Bloqueos manuales vigentes, del más nuevo al más viejo (HU-19). */
  getManualLocks(): Promise<ManualLock[]>;
  /**
   * Bloquea un sector o, sin `sectorId`, toda la macro-zona. Rechaza con `ManualLockError` (mensaje del
   * 400/409) si es inválido o ya está bloqueado.
   */
  activateManualLock(lock: NewManualLock): Promise<ManualLock>;
  /** Reanuda (desactiva) un bloqueo. Rechaza con `ManualLockError` si no existe o ya se reanudó (404). */
  releaseManualLock(id: number): Promise<void>;
  /** Tanques de insumo registrados, por macro-zona e insumo. */
  getTanks(): Promise<TankLevel[]>;
  /**
   * Suma litros al tanque de `supply` en `zoneId` y devuelve cómo quedó. Rechaza con `TankLoadError` (mensaje del
   * 400/404) si la carga es inválida, no entra en el tanque o falta su capacidad.
   */
  loadTank(zoneId: string, supply: Supply, load: NewTankLoad): Promise<TankLevel>;
}
