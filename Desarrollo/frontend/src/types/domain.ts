/* ============================================================
   Tipos del dominio Yerbanalytics.
   Contratos compartidos por la capa de datos y la UI.
   ============================================================ */

/** Estado de salud de un sector / métrica. */
export type Status = 'ok' | 'warning' | 'critical' | 'offline';

/** Severidad de un diagnóstico. '—' = no aplica. */
export type Severity = 'Alta' | 'Media' | 'Baja' | '—';

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
  conf: number | null;
  sev: Severity;
}

/** Estado de los actuadores físicos del sector. */
export interface Actuadores {
  /** Electroválvula: `Regando` (abierta), `En cola` (espera su turno en la tanda de la macro-zona) o `Cerrada`. */
  valve: string;
  pump: string;
  shade: number;
}

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
}

/** Lectura sensada de una macro-zona: lo que reportó su nodo testigo. */
export interface LecturaZona {
  /** Las 10 métricas, ya evaluadas contra sus umbrales. */
  metrics: Metric[];
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
  conf: number;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
  /** Gradiente CSS de respaldo. NO es una imagen: se usa cuando no hay captura asociada. */
  thumb: string;
  time: string;
  concluyente: boolean;
  /**
   * Ruta de la fotografía real de la captura que originó el diagnóstico. Viene vacía en los
   * diagnósticos que se derivan del estado del sector, y entonces la vista usa `thumb`.
   */
  imagenUrl?: string | null;
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

/** Tile de una métrica en el panel de sensado de la macro-zona (con sparkline). */
export interface MetricTile {
  key: string;
  label: string;
  value: string;
  unit: string;
  status: Status;
  color: string;
  line: string;
  ideal: string;
  soft: string;
  grupo: GrupoMetrica;
  /** Rango provisional, pendiente de validación agronómica. */
  provisional: boolean;
}

/** Serie histórica de una métrica de macro-zona para el gráfico del panel. */
export interface SerieMetrica {
  /** Path SVG de la línea. */
  line: string;
  /** Path SVG del área bajo la línea. */
  area: string;
  /** Extremos de la serie, ya formateados con la unidad. */
  min: string;
  max: string;
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
}

/** Nivel de una alerta del motor (eventos `Alerta` del historial). */
export type NivelAlerta = 'INFO' | 'WARNING' | 'CRITICAL';

/** Presentación del diagnóstico en el detalle. */
export interface DiagnosisDetail {
  estado: string;
  conf: number | null;
  sev: Severity;
  sevSoft: string;
  sevInk: string;
  thumb: string;
  concluyente: boolean;
  hasFoto: boolean;
  imagenUrl?: string;
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
  /** Bandas todavía sin validar con el vivero (métricas de la sonda de suelo). */
  provisional: boolean;
}

/**
 * Límites operativos y parámetros de seguimiento (HU-15). El volumen de riego y la
 * apertura máx. de mediasombra no viven acá: son parámetros del catálogo del motor de reglas
 * (`riego.volumen-max-evento`, `mediasombra.apertura-maxima`).
 */
export interface ConfigOperativa {
  insumoDosisMax24hMl: number;
  seguimientoLatenciaMin: number;
  seguimientoDeltaMin: number;
  intervaloSensadoMinutos: number;
  intervaloEvaluacionMinutos: number;
  updatedBy: string | null;
  updatedTs: number | null;
}

/** Etapa del plan de rustificación (HU-15 CA-06). */
export interface RustificacionEtapa {
  orden: number;
  diaDesde: number;
  diaHasta: number;
  aperturaPct: number;
}

/** Configuración agronómica completa (HU-15). Espejo del DTO `Configuracion`. */
export interface Configuracion {
  umbrales: MetricThreshold[];
  operativa: ConfigOperativa;
  rustificacion: RustificacionEtapa[];
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
  falla: string | null;
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

/** Una regla del motor con las claves de los parámetros que declara. */
export interface ReglaCatalogo {
  id: string;
  label: string;
  rama: RamaRegla;
  prioridad: number;
  parametros: string[];
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

export type OrigenEvaluacion = 'TELEMETRIA' | 'BARRIDO';

export type EstadoReglaTraza = 'EVALUADA' | 'OMITIDA_RAMA_BLOQUEADA' | 'NO_ALCANZADA' | 'ERROR';

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

/** Diagnóstico de IA de la captura de un paso. `conf` ya es un porcentaje 0–100. */
export interface DiagnosticoPaso {
  estado: string;
  conf: number;
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
