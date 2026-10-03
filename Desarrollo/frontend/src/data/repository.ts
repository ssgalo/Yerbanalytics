/* ============================================================
   Contrato de acceso a datos. La UI solo conoce esta interface;
   nunca sabe si detrás hay un mock o un backend real.

   Cubre el dominio del dashboard y nada más. El backend expone además endpoints de captura
   —órdenes, dispositivos de cámara, alta de diagnósticos— que NO se declaran acá: son
   superficie pública de la plataforma, para el planificador de pasadas del riel y el servicio
   de inferencia, y ninguna vista del dashboard los consume.
   ============================================================ */
import type {
  ActionRecord,
  CambioParametro,
  CatalogoReglas,
  Configuracion,
  DagSchema,
  DisposicionTopologia,
  HardwareData,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrigenEvaluacion,
  TopologiaVivero,
  TrazaEvaluacion,
} from '@/types/domain';

export interface DataRepository {
  /** Devuelve el snapshot completo del vivero. */
  getNursery(): Promise<NurseryData>;
  /** Devuelve el historial global de acciones (más recientes primero). */
  getHistory(): Promise<ActionRecord[]>;
  /** Devuelve la configuración agronómica vigente (HU-15). */
  getConfig(): Promise<Configuracion>;
  /** Persiste la configuración validada y devuelve la versión guardada (HU-15). */
  saveConfig(config: Configuracion): Promise<Configuracion>;
  /** Devuelve el estado técnico de la flota de hardware (HU-18 / HU-21). */
  getHardware(): Promise<HardwareData>;
  /** Da de alta un dispositivo (valida unicidad) y devuelve la flota actualizada (HU-18). */
  registerDevice(device: NuevoDispositivo): Promise<HardwareData>;
  /** Recambia un dispositivo reutilizando su registro y devuelve la flota actualizada (HU-21 CA-05). */
  replaceDevice(id: string, device: NuevoDispositivo): Promise<HardwareData>;
  /** Devuelve el resumen de la topología cargada en el vivero (HU-18 CA-01). */
  getTopologia(): Promise<TopologiaVivero>;
  /** Genera (o regenera) la grilla lógica del vivero y devuelve el resumen resultante (HU-18 CA-01). */
  generarTopologia(input: NuevaTopologia): Promise<TopologiaVivero>;
  /** Actualiza la disposición visual por fila sin regenerar la grilla (HU-18 CA-01). */
  guardarDisposicion(input: DisposicionTopologia): Promise<TopologiaVivero>;
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
   * Última evaluación del motor para un sector. Sin `origen`, la más reciente de las dos.
   * `null` si todavía no se evaluó desde el arranque.
   */
  getTrazaEvaluacion(sectorId: string, origen?: OrigenEvaluacion): Promise<TrazaEvaluacion | null>;
}
