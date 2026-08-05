/* ============================================================
   Contrato de acceso a datos. La UI solo conoce esta interface;
   nunca sabe si detrás hay un mock o un backend real.
   ============================================================ */
import type {
  ActionRecord,
  CodigoVinculacion,
  Configuracion,
  DiagnosticoRegistrado,
  DispositivoCamara,
  DisposicionTopologia,
  EnvioTelemetria,
  HardwareData,
  ModoSimulacion,
  NuevaOrdenCaptura,
  NuevoDiagnostico,
  NurseryData,
  NuevaTopologia,
  NuevoDispositivo,
  OrdenCaptura,
  SensorSimulado,
  SimulacionEstado,
  TopologiaVivero,
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
  /** Devuelve el modo de operación vigente (dashboard de simulación). */
  getSimulacionEstado(): Promise<SimulacionEstado>;
  /** Cambia el modo de operación (estático/simulación) y devuelve el estado resultante. */
  setModoSimulacion(modo: ModoSimulacion): Promise<SimulacionEstado>;
  /** Lista los sensores simulados (emisores en memoria, desacoplados del registro de hardware). */
  getSensoresSimulados(): Promise<SensorSimulado[]>;
  /** Da de alta un sensor simulado (serial/MAC + macro-zona) y devuelve la lista actualizada. */
  crearSensorSimulado(input: SensorSimulado): Promise<SensorSimulado[]>;
  /** Elimina un sensor simulado por serial/MAC y devuelve la lista actualizada. */
  eliminarSensorSimulado(serial: string): Promise<SensorSimulado[]>;
  /** Envía una lectura manual de un sensor simulado (se publica por MQTT en el backend real). */
  enviarTelemetria(input: EnvioTelemetria): Promise<void>;

  /* ----------------------------------------------------------------
     Captura de imágenes (HU-04 CA-01).

     Todos estos son endpoints PÚBLICOS de la plataforma, no del simulador: emitir una orden
     es lo que hará el planificador de pasadas del riel, y dar de alta un diagnóstico es lo
     que hará el servicio de inferencia. El panel de cámara del simulador no tiene ni un
     endpoint propio.
     ---------------------------------------------------------------- */

  /** Lista los dispositivos de captura enrolados con su estado técnico. */
  getDispositivosCamara(): Promise<DispositivoCamara[]>;
  /** Emite un código de vinculación de un solo uso para enrolar un dispositivo. */
  generarCodigoVinculacion(): Promise<CodigoVinculacion>;
  /** Emite una orden de captura (mismo endpoint que usará el planificador). */
  emitirOrdenCaptura(input: NuevaOrdenCaptura): Promise<OrdenCaptura>;
  /** Consulta el avance de una orden hasta que se resuelve. */
  getOrdenCaptura(ordenId: string): Promise<OrdenCaptura>;
  /** Da de alta un diagnóstico (mismo endpoint que usará el servicio de inferencia). */
  crearDiagnostico(input: NuevoDiagnostico): Promise<DiagnosticoRegistrado>;
}
