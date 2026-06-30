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
}

/** Lectura concreta de una métrica en un sector. */
export interface Metric {
  key: string;
  label: string;
  unit: string;
  raw: number;
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
  valve: string;
  pump: string;
  shade: number;
}

/** Un sector del vivero (~100 tubetes). */
export interface Sector {
  id: string;
  zona: string;
  zonaName: string;
  n: number;
  status: Status;
  color: string;
  statusLabel: string;
  tip: string;
  metrics: Metric[];
  diagnosis: Diagnosis;
  reason: string;
  actuadores: Actuadores;
  ago: string;
  stale: boolean;
}

/** Macro-zona (100 sectores). */
export interface Zona {
  id: string;
  name: string;
  sub: string;
  sectors: Sector[];
  sano: number;
  alerta: number;
  off: number;
  total: number;
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
  thumb: string;
  time: string;
  concluyente: boolean;
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

/** Tile de métrica en el detalle de sector (con sparkline). */
export interface MetricTile {
  label: string;
  value: string;
  unit: string;
  status: Status;
  color: string;
  line: string;
  ideal: string;
  soft: string;
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
  tipo: string; // 'Riego' | 'Insumo' | 'Mediasombra'
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
}

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
}

/** Detalle derivado de un sector (gráficos, tiles, historial...). */
export interface SectorDetail {
  mainLine: string;
  mainArea: string;
  mainMin: string;
  mainMax: string;
  metricTiles: MetricTile[];
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
}

/** Límites operativos de actuadores y parámetros de seguimiento (HU-15). */
export interface ConfigOperativa {
  riegoTiempoMaxSeg: number;
  riegoVolMaxDiarioMl: number;
  insumoDosisMax24hMl: number;
  mediasombraAperturaMaxPct: number;
  seguimientoLatenciaMin: number;
  seguimientoDeltaMin: number;
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
