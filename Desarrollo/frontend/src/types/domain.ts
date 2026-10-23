/* ============================================================
   Tipos del dominio Yerbanalytics.
   Contratos compartidos por la capa de datos y la UI.
   ============================================================ */

/** Estado de salud de un sector / métrica. */
export type Status = 'ok' | 'warning' | 'critical' | 'offline';

/** Severidad de un diagnóstico. '—' = no aplica (Sano, No concluyente, Sin diagnóstico). */
export type Severity = 'Alta' | 'Media' | 'Baja' | '—';

/**
 * Nivel de confianza de un diagnóstico: el score promedio del estado diagnosticado ubicado en las
 * bandas de confianza de su clase, que calibra el equipo en el servicio de inferencia.
 */
export type ConfidenceLevel = 'Baja' | 'Media' | 'Alta';

/** Rango temporal del gráfico principal del detalle de sector. */
export type Range = '24h' | '7d' | '30d';

/** Par soft/ink para chips de color (fondo + texto). */
export interface ColorPair {
  soft: string;
  ink: string;
}

/** Agrupación visual de una métrica en el panel de sensado. */
export type GrupoMetrica = 'ambiente' | 'nutricion';

/** Especificación de una métrica (bandas ideal/warn/crit). */
export interface MetricSpec {
  key: string;
  label: string;
  unit: string;
  ideal: [number, number];
  warn: [number, number];
  crit: [number, number];
  dec: number;
  base: number;
  grupo: GrupoMetrica;
  /**
   * Si la métrica participa del cálculo del estado de salud de los sectores. Las que
   * llegaron con la sonda de suelo (tempSuelo, phSuelo, n, p, k) son informativas hasta
   * validar sus rangos con el vivero: se muestran y colorean, pero no cambian el estado.
   */
  afectaEstado: boolean;
}

/** Lectura concreta de una métrica de la macro-zona. */
export interface Metric {
  key: string;
  label: string;
  unit: string;
  /** `null` si el nodo no reportó esa métrica (sensor en falla o lectura parcial). */
  raw: number | null;
  value: string;
  status: Status;
  color: string;
  spec: MetricSpec;
}

/** Diagnóstico de IA de un sector. */
export interface Diagnosis {
  estado: string;
  /** Score promedio × 100 del estado diagnosticado; `null` en No concluyente o sin diagnóstico. */
  conf: number | null;
  /** `null` en No concluyente, sin diagnóstico, o en un diagnóstico anterior a los niveles. */
  confidence: ConfidenceLevel | null;
  sev: Severity;
}

/** Estado de los actuadores físicos del sector. La mediasombra no está acá: es de la macro-zona (`Zona.mediasombra`). */
export interface Actuadores {
  /** Electroválvula: `Regando` (abierta), `En cola` (espera su turno: en la tanda de la macro-zona o en la cola del nodo) o `Cerrada`. */
  valve: string;
  /** Bomba de insumo: `Dosificando`, `En cola` (el nodo la tiene esperando su turno: sirve una carga a la vez) o `En espera`. Se muestra tal cual llega. */
  pump: string;
}

/** Última posición que el sistema le ordenó a la mediasombra de una macro-zona (no es una lectura). */
export type PosicionMediasombra = 'abierta' | 'cerrada';

/**
 * Un sector del vivero (~100 tubetes). No tiene métricas propias: la lectura pertenece a
 * su macro-zona (`Zona.lectura`), porque hay un solo nodo sensor testigo por macro-zona.
 * Sí son suyos el diagnóstico de IA del plantín y los actuadores.
 */
export interface Sector {
  id: string;
  zona: string;
  zonaName: string;
  n: number;
  status: Status;
  color: string;
  statusLabel: string;
  tip: string;
  diagnosis: Diagnosis;
  reason: string;
  actuadores: Actuadores;
  /**
   * Hasta cuándo (epoch ms) el sector tiene el reingreso restringido tras un fitosanitario; `null` si nunca se le
   * aplicó uno. Un valor pasado ya no restringe.
   */
  reentryUntil: number | null;
}

/** Tramo agronómico del DPV (reglas_v2 §1.4). */
export type VaporBand = 'SATURATED' | 'TRANSITION' | 'COMFORTABLE' | 'HIGH_DEMAND' | 'DRY_HEAT';

/**
 * Déficit de presión de vapor de una macro-zona: derivado de su temperatura del aire y humedad ambiental, no
 * sensado (no viaja por el contrato del nodo). Informativo: no mueve el estado de los sectores.
 */
export interface VaporDeficit {
  /** En kPa. */
  value: number;
  band: VaporBand;
  /** El tramo en palabras: 'calor seco'. */
  label: string;
}

/** Lectura sensada de una macro-zona: lo que reportó su nodo testigo. */
export interface LecturaZona {
  /** Las 10 métricas, ya evaluadas contra sus umbrales. */
  metrics: Metric[];
  /** DPV derivado de T y HR; `null` si la zona no tiene dato o el nodo está fuera de servicio. */
  dpv: VaporDeficit | null;
  /** Instante de la lectura (epoch ms); `null` si el nodo nunca reportó. */
  ts: number | null;
  /** Antigüedad legible: 'hace 4 min'. */
  ago: string;
  /** El nodo superó el umbral de silencio: los valores no son vigentes. */
  stale: boolean;
}

/** Estado del nodo sensor testigo que produce la lectura de una macro-zona. */
export interface NodoTestigo {
  mac: string | null;
  battery: number | null; // %
  signal: number | null; // dBm
  bateriaBaja: boolean;
}



/**
 * Plan de nutrición de una macro-zona (reglas_v2 §8.4): fertilizante foliar en todos sus sectores cada
 * `intervalDays`. Espejo del DTO `NutritionPlan`. Lo ejecuta el motor; el agrónomo sólo fija el intervalo.
 */
export interface NutritionPlan {
  /** Días entre dos aplicaciones: el de la zona o, si no fijó uno, el del catálogo. */
  intervalDays: number;
  /** La zona fijó su propio intervalo (no rige el general). */
  customInterval: boolean;
  /** epoch ms en que terminó la última tanda del plan; `null` si nunca hubo una. */
  lastApplicationTs: number | null;
  /** epoch ms desde el que el plan está vencido; `null` si ya lo está (nunca se aplicó) o hay una tanda en curso. */
  nextDueTs: number | null;
  /** El plan ya pidió el fertilizante y algún sector espera su ventana. */
  batchOpen: boolean;
  /** La CE baja (N-02) adelantó el plan y todavía no se ejecutó. */
  advanced: boolean;
}

/** Macro-zona (100 sectores). Dueña de la lectura sensada. */
export interface Zona {
  id: string;
  name: string;
  sub: string;
  sectors: Sector[];
  sano: number;
  alerta: number;
  off: number;
  total: number;
  lectura: LecturaZona;
  nodo: NodoTestigo;
  /** `null` si el snapshot no lo trae. */
  nutritionPlan?: NutritionPlan | null;
  /** Mediasombra de la zona (una por macro-zona): última posición ordenada; `null` si nunca se le ordenó nada. */
  mediasombra?: PosicionMediasombra | null;
}

/** KPIs y conteos globales del vivero. */
export interface Stats {
  total: number;
  sano: number;
  warning: number;
  critical: number;
  offline: number;
  alerta: number;
  sanoPct: number;
  actToday: number;
  actRiego: number;
  actInsumo: number;
  actSombra: number;
  diagCount: number;
}

/** Ítem de la lista de atención prioritaria. */
export interface PriorityItem {
  id: string;
  color: string;
  reason: string;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
  pulse: string;
}

/** Tarjeta de diagnóstico (vista Diagnósticos / recientes). */
export interface DiagnosisCard {
  id: string;
  sectorId: string;
  zonaName: string;
  estado: string;
  /** Score promedio × 100 del estado diagnosticado; `null` en No concluyente. */
  conf: number | null;
  /** `null` en No concluyente o en un diagnóstico anterior a los niveles ("sin nivel"). */
  confidence: ConfidenceLevel | null;
  /**
   * Detecciones guardadas con el diagnóstico. Con más de cero, el modal ofrece las cajas; se piden
   * aparte (`getDiagnosisDetections`) porque no viajan en el snapshot. 0 en los `DG-` derivados.
   */
  detectionCount: number;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
  /** Gradiente CSS de respaldo. NO es una imagen: se usa cuando no hay captura asociada. */
  thumb: string;
  time: string;
  /** Estado distinto de No concluyente. */
  concluyente: boolean;
  /**
   * Ruta de la fotografía real de la captura que originó el diagnóstico. Viene vacía en los
   * diagnósticos que se derivan del estado del sector, y entonces la vista usa `thumb`.
   */
  imagenUrl?: string | null;
  /**
   * Momento del diagnóstico (ms epoch): el alta, si nació de una captura; la última lectura de la
   * zona, si se deriva del sector. `null` si la zona nunca reportó. `time` es el mismo dato ya
   * redactado ("hace 12 min").
   */
  creadoEn: number | null;
}

/** Evento del feed de actividad del sistema. */
export interface ActionEvent {
  title: string;
  detail: string;
  time: string;
  result: string;
  resSoft: string;
  resInk: string;
  tint: string;
  ink: string;
  path: string;
}

/** Alerta activa (campana de la topbar). */
export interface Alert {
  level: string;
  color: string;
  time: string;
  sectorId: string;
  msg: string;
  read: boolean;
}

/** Pronóstico por franja horaria. */
export interface ForecastSlot {
  t: string;
  uv: number;
  rain: number;
}

/** Clima y riesgo actual. */
export interface Weather {
  tempC: number;
  cond: string;
  hum: number;
  uv: number;
  uvLabel: string;
  rainText: string;
  forecast: ForecastSlot[];
}

/** Histórico de una métrica del nodo testigo de una macro-zona, en valores de la métrica. */
export interface MetricHistory {
  /** Puntos en orden cronológico; el último es la lectura actual. */
  points: { ts: number; value: number }[];
  min: number;
  max: number;
}

/** Fila de actuador en el detalle. */
export interface ActuatorRow {
  name: string;
  state: string;
  active: boolean;
  path: string;
  soft: string;
  ink: string;
  dot: string;
}

/** Entrada del historial de acciones del sector. */
export interface HistoryEntry {
  tipo: string;
  t: string;
  d: string;
  res: string;
  soft: string;
  ink: string;
}

/** Seguimiento post-acción. */
export interface Evolution {
  show: boolean;
  metric: string;
  antes: string;
  ahora: string;
  unit: string;
  delta: string;
  latencia: string;
  verdict: string;
  vSoft: string;
  vInk: string;
}

/** Registro del historial global de acciones (HU-11 / HU-12). Espejo del DTO backend. */
export interface ActionRecord {
  id: string;
  sectorId: string;
  zonaName: string;
  tipo: string; // 'Riego' | 'Insumo' | 'Mediasombra' | 'Alerta'
  time: string; // tiempo relativo: 'hace 6 min'
  ts: number; // epoch ms (orden y filtro por fecha)
  fecha: string; // 'dd/MM HH:mm'
  /** Cadena de justificación: lectura/diagnóstico → decisión → acción. */
  lectura: string;
  decision: string;
  accion: string;
  res: string; // 'Efectiva' | 'En seguimiento' | 'Pospuesta' | 'Abortada'
  resSoft: string;
  resInk: string;
  sev: Severity;
  tint: string; // fondo del ícono
  ink: string; // color del ícono
  path: string; // path SVG del ícono
  /** Seguimiento post-acción; null si la acción no lo requiere. */
  evo: Evolution | null;
  /** Regla que ordenó la acción (p. ej. `DeficitCriticoRule`); null/ausente si no aplica. */
  regla?: string | null;
  /** Nivel de la alerta; sólo en eventos de tipo `Alerta`. */
  alerta?: NivelAlerta | null;
  /** Volumen de riego ordenado (L); sólo en eventos de tipo `Riego`. */
  volumenL?: number | null;
  /** Duración de apertura ordenada (s); sólo en eventos de tipo `Riego`. */
  duracionSeg?: number | null;
  /**
   * Piezas del motor que estaban apagadas cuando se tomó la decisión (el grafo de razonamiento las excluye
   * y dibuja todas las demás). Vacío = todo estaba prendido; `null`/ausente = evento anterior a este registro
   * o que no vino del motor: el grafo cae a las que están apagadas de fábrica.
   */
  reglasInactivas?: string[] | null;
}

/** Nivel de una alerta del motor (eventos `Alerta` del historial). */
export type NivelAlerta = 'INFO' | 'WARNING' | 'CRITICAL';

/** Presentación del diagnóstico en el detalle. */
export interface DiagnosisDetail {
  estado: string;
  conf: number | null;
  confidence: ConfidenceLevel | null;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
  thumb: string;
  concluyente: boolean;
  hasFoto: boolean;
  imagenUrl?: string;
  /**
   * Id del diagnóstico `DX-` más reciente del sector (el que nació de una captura), para pedir sus
   * cajas; `null` si el sector no tiene ninguno. Lo resuelve el dashboard, no viene del backend.
   */
  diagnosisId: string | null;
  /** Detecciones de ese diagnóstico; 0 si no tiene (o no hay diagnóstico `DX-`). */
  detectionCount: number;
}

/**
 * Una caja que detectó el modelo y sobrevivió al filtro y a la resolución de solapes.
 * `box` = [x1, y1, x2, y2] normalizadas a la imagen (0–1, `xyxyn`), independientes de la resolución.
 */
export interface Detection {
  /** Estado de la plataforma ("Clorosis", "Sano"…), no el nombre interno del modelo. */
  label: string;
  /** 0–1. */
  score: number;
  box: [number, number, number, number];
}

/** Bandas de severidad de un estado anómalo, en cantidad de hojas detectadas por sector. */
export interface SeverityBand {
  state: string;
  /** Debajo de este mínimo, el estado no cuenta para el diagnóstico. */
  minimum: number;
  mediumFrom: number;
  highFrom: number;
}

/**
 * Criterio agronómico del diagnóstico: con qué cantidad de hojas cada estado anómalo es de
 * severidad baja, media o alta, y cuál pesa más ante un empate. Espejo de
 * `GET/PUT /api/configuracion/diagnostico`.
 */
export interface DiagnosisCriteria {
  /** Las cuatro clases anómalas del modelo, en orden fijo. */
  severityBands: SeverityBand[];
  /** Permutación de los cuatro estados; el primero es el más crítico. */
  criticality: string[];
}

/**
 * Detalle derivado de un sector. Sin métricas ni gráfico de series: eso vive en el panel
 * de sensado de la macro-zona.
 */
export interface SectorDetail {
  diag: DiagnosisDetail;
  actsRows: ActuatorRow[];
  hist: HistoryEntry[];
  evo: Evolution;
  statusSoft: string;
  statusInk: string;
}

/** Bandas configurables de una métrica (HU-15). Espejo del DTO `UmbralMetrica`. */
export interface MetricThreshold {
  key: string;
  label: string;
  unit: string;
  dec: number;
  idealMin: number;
  idealMax: number;
  warnMin: number;
  warnMax: number;
  critMin: number;
  critMax: number;
  /**
   * Puntos de alerta dentro de cada franja amarilla (`warnMin ≤ alertMin ≤ idealMin`,
   * `idealMax ≤ alertMax ≤ warnMax`). No cambian el estado: más allá de ellos, la campana avisa.
   */
  alertMin: number;
  alertMax: number;
  /** Bandas todavía sin validar con el vivero (métricas de la sonda de suelo). */
  provisional: boolean;
}

/**
 * Parámetros operativos de la configuración (HU-15): el pulso de sensado, la pasada diaria y el horario por defecto de
 * la mediasombra. Lo que decide el motor (volúmenes, topes de dosis, seguimiento post-acción) vive en el catálogo de
 * parámetros del motor de reglas.
 */
export interface ConfigOperativa {
  /**
   * Intervalo del pulso de sensado y evaluación (60–360 min). En cada franja, anclada a las 02:00,
   * el backend lee los sensores de todas las macro-zonas y evalúa el motor.
   */
  intervaloSensadoMinutos: number;
  /** Sólo lectura: lo calcula el backend sobre el pulso; el navegador nunca deriva la fórmula. */
  effectiveStaleThresholdSeconds?: number;
  /** ¿Corre la pasada diaria del riel? */
  pasadaDiariaActiva: boolean;
  /** Hora local de la pasada diaria ("HH:mm"); se guarda aunque la pasada esté desactivada. */
  pasadaDiariaHora: string;
  /**
   * Horario en que la mediasombra está abierta por defecto ("HH:MM", hora local del vivero), en
   * toda macro-zona sin un plan de rustificación en curso. Fuera de él, cerrada. Dentro, otras
   * reglas (p. ej. protección por radiación) pueden cerrarla igual.
   */
  mediasombraAbiertaDesde: string;
  mediasombraAbiertaHasta: string;
  updatedBy: string | null;
  updatedTs: number | null;
}

/**
 * Configuración agronómica completa (HU-15). Espejo del DTO `Configuracion`. El plan de
 * rustificación ya no vive acá: es por macro-zona (`PlanRustificacion`).
 */
export interface Configuracion {
  umbrales: MetricThreshold[];
  operativa: ConfigOperativa;
}

/**
 * Etapa de un plan de rustificación. La mediasombra sólo abre o cierra del todo, así que la etapa
 * no fija un porcentaje sino una ventana diaria: abierta de `abiertaDesde` a `abiertaHasta`
 * ("HH:MM"), cerrada el resto del día, durante `dias` días seguidos.
 */
export interface EtapaRustificacion {
  orden: number;
  dias: number;
  abiertaDesde: string;
  abiertaHasta: string;
}

/**
 * Estado de un plan: `programado` (todavía no empezó) y `en_curso` rigen; pasada la última etapa sigue `en_curso` y
 * se sostiene la última hasta que alguien elimina el plan, que queda `cancelado`.
 */
export type EstadoPlanRustificacion = 'programado' | 'en_curso' | 'cancelado';

/**
 * Plan de rustificación de una macro-zona: expone a los plantines al sol cada vez más tiempo antes
 * del trasplante. Mientras rige, reemplaza el horario por defecto de la mediasombra en esa zona.
 */
export interface PlanRustificacion {
  id: string;
  zonaId: string;
  /** Primer día del plan ("YYYY-MM-DD", fecha local del vivero). */
  fechaInicio: string;
  etapas: EtapaRustificacion[];
  estado: EstadoPlanRustificacion;
  creadoPor: string | null;
  /** epoch ms */
  creadoEn: number;
  canceladoEn: number | null;
}

/** Lo que manda el dashboard al iniciar un plan. */
export interface NuevoPlanRustificacion {
  fechaInicio: string;
  etapas: Omit<EtapaRustificacion, 'orden'>[];
}

/**
 * Bloqueo manual vigente de la actuación autónoma (HU-19). Espejo del DTO `ManualLock`; las claves
 * son las del contrato de la API. `sectorId` es `null` si el bloqueo es de toda la macro-zona.
 */
export interface ManualLock {
  id: number;
  zonaId: string;
  sectorId: string | null;
  activadoPor: string;
  /** epoch ms */
  activadoTs: number;
  motivo: string;
}

/** Lo que manda el dashboard al bloquear. Quién bloquea lo sabe el backend por la sesión. */
export interface NewManualLock {
  zonaId: string;
  /** Sin sector, se bloquea toda la macro-zona. */
  sectorId?: string;
  motivo: string;
}

/** Insumos que se aplican (reglas_v2 §8.1). Es el nombre que viaja por la API. */
export type Supply = 'FERTILIZER' | 'FUNGICIDE';

/**
 * Tanque de un insumo en una macro-zona (un tanque por insumo y por MZ). Espejo del DTO `TankLevel`. Un tanque que
 * nadie registró no figura: su stock es desconocido, no está lleno.
 */
export interface TankLevel {
  zoneId: string;
  supply: Supply;
  /** Nombre para mostrar: "Fertilizante", "Fitosanitario". */
  supplyLabel: string;
  capacityMl: number;
  levelMl: number;
  levelPct: number;
  /** Por debajo del nivel de aviso (`insumo.stock-aviso`): hay que reponer. */
  low: boolean;
  /** epoch ms */
  updatedTs: number;
  /** Quién hizo la última carga; `null` si desde entonces sólo hubo aplicaciones. */
  updatedBy: string | null;
}

/** Carga de un tanque. Quién la hace lo sabe el backend por la sesión. */
export interface NewTankLoad {
  liters: number;
  /** Obligatoria la primera vez (el tanque todavía no existe); después sólo para corregirla. */
  capacityLiters?: number;
}

/** Estado operativo derivado de un dispositivo (HU-21). */
export type EstadoHardware = 'operativo' | 'intermitente' | 'fuera_de_servicio';

/** Un dispositivo de la flota con su estado técnico (HU-21). Espejo del DTO `Dispositivo`. */
export interface Dispositivo {
  id: string;
  serial: string;
  tipo: string; // 'nodo_testigo' | 'electrovalvula' | 'bomba_peristaltica' | 'mediasombra'
  tipoLabel: string;
  zonaId: string | null;
  sectorId: string | null;
  ubicacion: string;
  bateria: number | null; // %
  senal: number | null; // dBm
  ultimoUpdate: number | null; // epoch ms
  ultimoUpdateLabel: string;
  estado: EstadoHardware;
  estadoLabel: string;
  estadoSoft: string;
  estadoInk: string;
  bateriaBaja: boolean;
  /**
   * Falla marcada: el `tipo` del error que informó el nodo (`SIN_CAUDAL`, `CAUDAL_EXCESIVO`,
   * `MEDIASOMBRA_FIN_DE_CARRERA`, S-05); `null` si está sano. `lib/fallas.ts` lo traduce a texto.
   */
  falla: string | null;
  /** Epoch ms en que se marcó la falla; `null` si está sano o no se sabe. */
  fallaDesde: number | null;
}

/** Sector con mapeo de hardware incompleto (HU-18 CA-04). */
export interface SectorIncompleto {
  sectorId: string;
  zonaName: string;
  faltantes: string[]; // etiquetas de los actuadores faltantes
}

/** Estado técnico de la flota (HU-18 / HU-21). Espejo del DTO `HardwareData`. */
export interface HardwareData {
  dispositivos: Dispositivo[];
  total: number;
  operativos: number;
  bateriaBaja: number;
  fueraDeServicio: number;
  averiados: number;
  incompletos: SectorIncompleto[];
}

/** Alta o recambio de un dispositivo (HU-18 / HU-21). */
export interface NuevoDispositivo {
  serial: string;
  tipo: string;
  zonaId: string | null;
  sectorId: string | null;
}

/** Resumen de la topología cargada en el vivero (HU-18 CA-01). Espejo del DTO `TopologiaVivero`. */
export interface TopologiaVivero {
  macroZonas: number;
  sectoresPorMacroZona: number;
  totalSectores: number;
  /** `true` si hay una grilla disponible para el mapa de producción. */
  generada: boolean;
  /** Disposición visual: macro-zonas mostradas por fila en el panel general. */
  macroZonasPorFila: number;
  /** Disposición visual: sectores mostrados por fila dentro de cada macro-zona. */
  sectoresPorFila: number;
}

/** Generación de la topología del vivero (HU-18 CA-01). `regenerar` reemplaza una grilla ya cargada. */
export interface NuevaTopologia {
  macroZonas: number;
  sectoresPorMacroZona: number;
  regenerar?: boolean;
  /** Disposición visual a guardar junto con la grilla; defaults si se omite. */
  macroZonasPorFila?: number;
  sectoresPorFila?: number;
}

/** Disposición visual de la topología (HU-18 CA-01). Espejo del DTO `DisposicionTopologia`. */
export interface DisposicionTopologia {
  macroZonasPorFila: number;
  sectoresPorFila: number;
}

/** Dataset completo del vivero (lo que entrega el repositorio). */
export interface NurseryData {
  zonas: Zona[];
  sectors: Sector[];
  byId: Record<string, Sector>;
  stats: Stats;
  priority: PriorityItem[];
  diagnoses: DiagnosisCard[];
  diagById: Record<string, DiagnosisCard>;
  recentDiag: DiagnosisCard[];
  actions: ActionEvent[];
  alerts: Alert[];
  weather: Weather;
  sevMap: Record<Severity, ColorPair>;
  tints: Record<string, string>;
  specs: MetricSpec[];
  /** Disposición visual de la grilla (macro-zonas/sectores por fila) — HU-18 CA-01. */
  layout: DisposicionTopologia;
}

// -----------------------------------------------------------------------
// Tipos del Inspector de Decisiones (DAG del motor de reglas)
// -----------------------------------------------------------------------

/** Nodo del DAG de reglas. Espejo de `RuleNodeDto` del backend. */
export interface RuleNode {
  /** ID único. Para reglas: nombre de la clase (ej: "ClimaOverrideRule"). */
  id: string;
  /** Etiqueta legible para el usuario. */
  label: string;
  /** Tipo de nodo para el renderizador: "input" | "default" | "output". */
  type: string;
  /**
   * Prioridad del nodo. -1 para "start", 999 para "abort", 1000 para "success".
   * El frontend usa este valor para determinar el estado visual de cada nodo
   * al seleccionar un evento del historial.
   */
  priority: number;
  /** Rama del DAG a la que pertenece este nodo (ej. RIEGO, INSUMO). */
  branch: string;
  /** Claves del catálogo de parámetros que declara la regla; vacía en los nodos especiales. */
  parametros: string[];
  /** Código de `reglas_v2` ("R-01"); vacío si la pieza no tiene (o es un nodo especial). */
  codigo?: string;
  /**
   * Estado EFECTIVO hoy: `false` si la regla está apagada (por el vivero o, en modo demo, por el simulador) y no
   * corre. Los nodos especiales siempre vienen activos; ausente = activa.
   */
  activa?: boolean;
  /** `false` si arranca apagada de fábrica (las retiradas). Sirve para reconstruir el grafo de un evento viejo. */
  activaPorDefecto?: boolean;
}

/** Arista dirigida entre dos nodos del DAG. Espejo de `RuleEdgeDto` del backend. */
export interface RuleEdge {
  id: string;
  source: string;
  target: string;
  label: string;
}

/**
 * Esquema base del DAG del motor de reglas.
 * Espejo de `DagSchemaDto` del backend. Devuelto por GET /api/rules/schema.
 */
export interface DagSchema {
  nodes: RuleNode[];
  edges: RuleEdge[];
}


// -----------------------------------------------------------------------
// Motor de reglas: catálogo de parámetros y traza de evaluación
// Espejo de los DTO de `/api/rules/parametros` y `/api/rules/evaluaciones`.
// -----------------------------------------------------------------------

export type TipoParametro = 'NUMERO' | 'ENTERO' | 'HORA' | 'VENTANA_HORARIA';

/** Una rama del motor: agrupa reglas que se bloquean entre sí. */
export type RamaRegla = 'GLOBAL' | 'RIEGO' | 'INSUMO' | 'MEDIASOMBRA' | 'SEGUIMIENTO';

/**
 * Un umbral del catálogo. Existe UNA vez aunque lo usen varias reglas (`usadoPor`).
 * `valor` y `fabrica` son strings canónicos por tipo ("42", "0.2", "06:00", "06:00-18:00").
 */
export interface ParametroRegla {
  clave: string;
  etiqueta: string;
  descripcion: string;
  familia: string;
  tipo: TipoParametro;
  unidad: string;
  valor: string;
  fabrica: string;
  /** Nulos en horas y ventanas horarias. */
  min: number | null;
  max: number | null;
  decimales: number;
  refSpec: string;
  /** Distinto del valor de fábrica. */
  modificado: boolean;
  /** Ids de las reglas que lo declaran. Derivado en el servidor. */
  usadoPor: string[];
  updatedBy: string | null;
  /** Epoch ms de la última edición. */
  updatedTs: number | null;
}

/** Grupo de la pantalla Motor de reglas (el backend lo declara por pieza). */
export type ObjetivoRegla = 'IRRIGATION' | 'NUTRITION' | 'SANITY' | 'SHADE' | 'FOLLOW_UP' | 'PROTECTION';

/** Qué hace la pieza: actúa sobre el vivero, sólo avisa o protege (apagarla exige confirmación). */
export type EfectoRegla = 'ACTS' | 'WARNS' | 'PROTECTS';

/** Último cambio del interruptor de una pieza: quién, cuándo y por qué. */
export interface UltimoCambioRegla {
  motivo: string | null;
  updatedBy: string;
  /** Epoch ms. */
  updatedTs: number;
}

/**
 * Una pieza del motor que decide (regla por sector, de macro-zona o servicio) con las claves de los parámetros
 * que declara. `label` viene sin emoji ni código; `codigo` es "" si la pieza no tiene (puede ser una lista).
 */
export interface ReglaCatalogo {
  id: string;
  label: string;
  codigo: string;
  rama: RamaRegla;
  objetivo: ObjetivoRegla;
  efecto: EfectoRegla;
  prioridad: number;
  parametros: string[];
  /** Estado operativo: lo que decidió el vivero. */
  activa: boolean;
  /** `false` si arranca apagada de fábrica (una retirada). */
  activaPorDefecto: boolean;
  /** El override explícito del simulador la apaga (siempre `false` fuera del modo demo). */
  apagadaPorDemo: boolean;
  /** Si es una protección: qué pasa al apagarla. `null` si no lo es. */
  proteccion: string | null;
  /** `null` si nunca se cambió el interruptor. */
  estado: UltimoCambioRegla | null;
}

/** Cuerpo de `PUT /api/rules/{name}/estado`. Prender nunca pide motivo ni confirmación. */
export interface CambioEstadoRegla {
  activa: boolean;
  motivo?: string;
  /** Obligatorio al apagar una protección. */
  confirmaProteccion?: boolean;
}

/** Catálogo normalizado: los parámetros van una vez y las reglas los referencian por clave. */
export interface CatalogoReglas {
  reglas: ReglaCatalogo[];
  parametros: ParametroRegla[];
}

/** Cambio de un parámetro. `valor: null` restablece el valor de fábrica. */
export interface CambioParametro {
  clave: string;
  valor: string | null;
}

/** Error de validación del servidor; `clave` es null si no corresponde a un parámetro. */
export interface ErrorParametro {
  clave: string | null;
  mensaje: string;
}

/** Sin barrido periódico: el motor sólo se evalúa con la lectura de un pulso. */
export type OrigenEvaluacion = 'TELEMETRIA';

/**
 * `OMITIDA_SIN_DIAGNOSTICO_NUEVO`: la regla usa el diagnóstico de IA y el sector no tiene uno nuevo
 * (todavía sin usar), así que no se evaluó.
 */
export type EstadoReglaTraza =
  | 'EVALUADA'
  | 'OMITIDA_RAMA_BLOQUEADA'
  | 'OMITIDA_SIN_DIAGNOSTICO_NUEVO'
  | 'NO_ALCANZADA'
  | 'ERROR';

/** `EN` es pertenencia ("∈"): la hora local dentro de una ventana horaria. */
export type OperadorComparacion = 'LT' | 'LE' | 'GT' | 'GE' | 'EQ' | 'EN';

export type ResultadoComparacion = 'CUMPLE' | 'NO_CUMPLE' | 'SIN_DATO';

/** Qué recibió una regla contra qué umbral, y si se cumplió. */
export interface Comparacion {
  etiqueta: string;
  /** Clave del parámetro; null en las condiciones fijas (no configurables). */
  clave: string | null;
  /** null cuando el dato no estaba (`SIN_DATO`). Las condiciones fijas pueden ser texto o booleano. */
  recibido: number | string | boolean | null;
  operador: OperadorComparacion;
  umbral: number | string | boolean | null;
  unidad: string;
  configurable: boolean;
  resultado: ResultadoComparacion;
}

export interface AccionTraza {
  tipo: string;
  motivo: string;
}

/** Lo que pasó con una regla en una evaluación. */
export interface TrazaRegla {
  ruleId: string;
  rama: RamaRegla;
  prioridad: number;
  estado: EstadoReglaTraza;
  comparaciones: Comparacion[];
  acciones: AccionTraza[];
  /** Regla que cortó la rama (sólo en `OMITIDA_RAMA_BLOQUEADA`). */
  bloqueadaPor: string | null;
  /** "Clase: mensaje" cuando la regla lanzó una excepción (`ERROR`). */
  error: string | null;
}

/** Última evaluación del motor para un sector y origen. Vive en memoria en el backend. */
export interface TrazaEvaluacion {
  sectorId: string;
  /** null si el backend no pudo resolver la macro-zona del sector. */
  zonaId: string | null;
  origen: OrigenEvaluacion;
  /** ISO-8601. */
  ts: string;
  parametrosHash: string;
  reglas: TrazaRegla[];
}

/* ---------- Pasada del riel (Demo Expo) ---------- */

export type EstadoPasada = 'EN_CURSO' | 'COMPLETADA' | 'FALLIDA' | 'CANCELADA';
export type TipoPaso = 'MOVER' | 'CAPTURAR' | 'HOME';
export type EstadoPaso = 'PENDIENTE' | 'EN_CURSO' | 'OK' | 'ERROR' | 'OMITIDO';

/** Diagnóstico de IA de la captura de un paso. `conf` es el score promedio × 100 (0–100). */
export interface DiagnosticoPaso {
  estado: string;
  /** `null` en No concluyente. */
  conf: number | null;
  /** `null` en No concluyente o en un diagnóstico anterior a los niveles. */
  confidence: ConfidenceLevel | null;
  sev: string;
  creadoEn: number;
}

export interface PasoPasada {
  n: number;
  tipo: TipoPaso;
  posicion: number;
  sectorId: string | null;
  estado: EstadoPaso;
  codigoError: string | null;
  /** Texto legible que arma el backend (el front sólo lo muestra). */
  detalle: string | null;
  commandId: string | null;
  ordenId: string | null;
  /** `estado` de la orden de captura tal cual: PENDIENTE, ENTREGADA, RECIBIDA, ERROR… */
  estadoOrden: string | null;
  capturaId: string | null;
  imagenUrl: string | null;
  diagnostico: DiagnosticoPaso | null;
  /** ms epoch */
  iniciadoEn: number | null;
  terminadoEn: number | null;
}

export interface Pasada {
  id: string;
  estado: EstadoPasada;
  /** ms epoch */
  iniciadaEn: number;
  finalizadaEn: number | null;
  cancelacionSolicitada: boolean;
  error: string | null;
  pasos: PasoPasada[];
}

/* ---------- Secuencias de actuadores (Demo Expo) ---------- */

export type TipoSecuencia = 'RIEGO' | 'MEDIASOMBRA' | 'LECTURA';
export type TipoPasoSecuencia =
  | 'ABRIR'
  | 'ESPERAR'
  | 'CERRAR'
  | 'DESPLEGAR'
  | 'ENROLLAR'
  | 'PEDIR'
  | 'ESPERAR_TELEMETRIA'
  | 'MOSTRAR';

/** Parámetros del pedido: `duracionSeg` es del riego y `esperaSeg` de la mediasombra. */
export interface ParametrosSecuencia {
  duracionSeg?: number | null;
  esperaSeg?: number | null;
  /** Sólo LECTURA: además de mostrar la lectura, la zona la responde como un pulso con motor. */
  ejecutarMotor?: boolean | null;
}

export interface PasoSecuencia {
  n: number;
  tipo: TipoPasoSecuencia;
  estado: EstadoPaso;
  codigoError: string | null;
  /** Texto legible que arma el backend (el front sólo lo muestra). */
  detalle: string | null;
  commandId: string | null;
  /** ms epoch; sólo en ESPERAR. */
  esperaHasta: number | null;
  iniciadoEn: number | null;
  terminadoEn: number | null;
}

/** La lectura de la zona que trajo una secuencia LECTURA. `ce` en dS/m; `uv` es % de luz. */
export interface LecturaSecuencia {
  recibidaEn: number;
  metricas: Record<string, number | null>;
}

export interface Secuencia {
  id: string;
  tipo: TipoSecuencia;
  estado: EstadoPasada;
  zonaId: string;
  sectorId: string | null;
  parametros: ParametrosSecuencia;
  /** ms epoch */
  iniciadaEn: number;
  finalizadaEn: number | null;
  cancelacionSolicitada: boolean;
  error: string | null;
  lectura: LecturaSecuencia | null;
  pasos: PasoSecuencia[];
}
