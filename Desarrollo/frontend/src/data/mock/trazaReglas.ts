/* ============================================================
   Evaluación simulada del motor de reglas para la demo (modo `mock`).

   Espeja a las 9 reglas del backend y a `RuleOrchestrator.evaluate`: mismo orden de
   prioridad, mismas comparaciones, mismas reglas de corte por rama (ABORT_RIEGO y
   POSTPONE_RIEGO cortan RIEGO, ABORT_INSUMO corta INSUMO, ABORT_ALL corta todo). Los
   umbrales se leen del catálogo vigente, así que editar un parámetro en la demo cambia
   la traza como lo haría el sistema real.

   Es una función pura: la entrada trae todo lo que el motor real leería del contexto.
   ============================================================ */
import type {
  AccionTraza,
  CatalogoReglas,
  Comparacion,
  EstadoReglaTraza,
  OperadorComparacion,
  OrigenEvaluacion,
  TrazaEvaluacion,
  TrazaRegla,
} from '@/types/domain';

/** Lo que las reglas leen del contexto de un sector. `null` = el dato no está. */
export interface EntradaMotor {
  sectorId: string;
  zonaId: string;
  origen: OrigenEvaluacion;
  /** ISO-8601 del instante de la evaluación. */
  ts: string;
  /** Segundos desde la última lectura de la zona. */
  antiguedadSeg: number | null;
  bloqueoManual: boolean;
  humSus: number | null;
  lluviaPct: number | null;
  uvIndex: number | null;
  riegos24h: number;
  dosis24h: number;
  /** Estado de salud del sector (`ok`, `warning`, `critical`, `offline`). */
  estadoSector: string;
  confianza: number | null;
}

/** Acumulador de una regla: registra cada comparación igual que `Evaluacion` en el backend. */
class Ev {
  readonly comparaciones: Comparacion[] = [];

  constructor(private readonly catalogo: CatalogoReglas) {}

  /** Valor vigente de un parámetro numérico. */
  numero(clave: string): number {
    const p = this.catalogo.parametros.find((x) => x.clave === clave);
    if (!p) throw new Error(`Parámetro ${clave} no está en el catálogo`);
    return Number(p.valor);
  }

  /** Compara contra un parámetro del catálogo y registra la comparación. */
  comparar(etiqueta: string, recibido: number | null, op: OperadorComparacion, clave: string): boolean {
    const p = this.catalogo.parametros.find((x) => x.clave === clave);
    const umbral = this.numero(clave);
    const cumple = recibido !== null && aplicar(recibido, op, umbral);
    this.comparaciones.push({
      etiqueta,
      clave,
      recibido,
      operador: op,
      umbral,
      unidad: p?.unidad ?? '',
      configurable: true,
      resultado: recibido === null ? 'SIN_DATO' : cumple ? 'CUMPLE' : 'NO_CUMPLE',
    });
    return cumple;
  }

  /** Condición que no se edita (p. ej. `estado == critical`). */
  compararFijo(etiqueta: string, recibido: string | boolean, op: OperadorComparacion, umbral: string | boolean): boolean {
    const cumple = recibido === umbral;
    this.comparaciones.push({
      etiqueta,
      clave: null,
      recibido,
      operador: op,
      umbral,
      unidad: '',
      configurable: false,
      resultado: cumple ? 'CUMPLE' : 'NO_CUMPLE',
    });
    return cumple;
  }
}

function aplicar(a: number, op: OperadorComparacion, b: number): boolean {
  switch (op) {
    case 'LT': return a < b;
    case 'LE': return a <= b;
    case 'GT': return a > b;
    case 'GE': return a >= b;
    case 'EQ': return a === b;
  }
}

const accion = (tipo: string, motivo: string): AccionTraza => ({ tipo, motivo });
const noop = (motivo: string) => [accion('NOOP_INFO', motivo)];

type Evaluador = (ev: Ev, e: EntradaMotor) => AccionTraza[];

/** Cuerpo de cada regla, calcado de su clase en `engine/rules/`. */
const EVALUADORES: Record<string, Evaluador> = {
  ManualLockRule: (ev, e) =>
    ev.compararFijo('Bloqueo manual activo', e.bloqueoManual, 'EQ', true)
      ? [accion('ABORT_ALL', 'Bloqueo manual activo en el sector: se detiene toda actuación autónoma.')]
      : noop('Sin bloqueo manual.'),

  StaleSensorRule: (ev, e) => {
    const vieja = ev.comparar('Antigüedad de la última lectura', e.antiguedadSeg, 'GT', 'seguridad.antiguedad-max-lectura');
    // Sin lectura no hay nada que comparar, pero tampoco se puede regar a ciegas.
    return e.antiguedadSeg === null || vieja
      ? [accion('ABORT_RIEGO', `El nodo testigo de la macro-zona ${e.zonaId} no reportó dentro del umbral de antigüedad configurado. Actuación autónoma anulada por seguridad.`)]
      : noop('Telemetría fresca: el nodo reportó dentro del umbral configurado.');
  },

  WeatherOverrideRule: (ev, e) => {
    const lluvia = ev.comparar('Probabilidad de lluvia', e.lluviaPct, 'GE', 'riego.lluvia-probabilidad');
    if (e.lluviaPct === null) return noop('Pronóstico climático no disponible — el motor evalúa solo con sensores.');
    return lluvia
      ? [accion('POSTPONE_RIEGO', `Lluvia inminente probable (${e.lluviaPct}% ≥ umbral ${ev.numero('riego.lluvia-probabilidad')}%). Riego autónomo pospuesto.`)]
      : noop(`Probabilidad de lluvia ${e.lluviaPct}% — no se pospone el riego.`);
  },

  DailyVolumeLimitRule: (ev, e) =>
    ev.comparar('Riegos en las últimas 24 h', e.riegos24h, 'GE', 'riego.max-riegos-24h')
      ? [accion('ABORT_RIEGO', `El sector ya recibió ${e.riegos24h} riego(s) en las últimas 24 h: límite de volumen diario alcanzado.`)]
      : noop(`${e.riegos24h} riego(s) en las últimas 24 h — dentro del límite.`),

  DailyDoseLimitRule: (ev, e) =>
    ev.comparar('Dosificaciones en las últimas 24 h', e.dosis24h, 'GE', 'insumo.max-dosis-24h')
      ? [accion('ABORT_INSUMO', `El sector ya recibió ${e.dosis24h} dosis en las últimas 24 h: límite alcanzado.`)]
      : noop(`${e.dosis24h} dosis en las últimas 24 h — dentro del límite.`),

  IrrigationRule: (ev, e) => {
    const bajo = ev.comparar('Humedad de sustrato', e.humSus, 'LT', 'riego.umbral-humedad');
    const umbral = ev.numero('riego.umbral-humedad');
    if (e.humSus === null) return noop('Sin lectura de humedad de sustrato — no se puede evaluar riego.');
    if (!bajo) return noop(`Humedad de sustrato ${e.humSus}% dentro del rango aceptable (umbral: ${umbral}%). No se riega.`);
    if (ev.comparar('Riegos en las últimas 24 h', e.riegos24h, 'GE', 'riego.max-riegos-24h-sector')) {
      return [accion('ABORT_RIEGO', `Humedad ${e.humSus}% bajo el umbral (${umbral}%), pero el sector ya recibió ${e.riegos24h} riego(s) en las últimas 24 h. Riego autónomo bloqueado por límite operativo.`)];
    }
    return [accion('ACTIVAR_VALVULA', `Humedad de sustrato ${e.humSus}% bajo el umbral mínimo de ${umbral}%. [tiempo-max-seg=${ev.numero('riego.tiempo-max-apertura')}]`)];
  },

  SupplyRule: (ev, e) => {
    if (!ev.compararFijo('Estado del sector', e.estadoSector, 'EQ', 'critical')) {
      return noop('El sector no está en estado crítico — no se dosifica.');
    }
    return ev.comparar('Confianza del diagnóstico', e.confianza, 'GE', 'diagnostico.confianza-minima')
      ? [accion('ACTIVAR_BOMBA', `Sector crítico con diagnóstico confiable (${e.confianza}%). Se dosifica el insumo.`)]
      : noop('Diagnóstico no concluyente — no se dosifica.');
  },

  ShadingRule: (ev, e) =>
    ev.comparar('Índice UV pronosticado', e.uvIndex, 'GE', 'mediasombra.uv-umbral')
      ? [accion('MOVER_MEDIASOMBRA', `[apertura=${Math.min(ev.numero('mediasombra.apertura-proteccion-uv'), ev.numero('mediasombra.apertura-maxima'))}] Pico de UV: mediasombra en posición protectora.`)]
      : noop('Sin pico de UV: la apertura sigue el plan de rustificación.'),

  FollowUpRule: () => noop('Seguimiento post-acción: sin acciones pendientes de evaluar.'),
};

/** Huella estable de los valores vigentes (FNV-1a), como el `parametrosHash` del backend. */
function huella(catalogo: CatalogoReglas): string {
  let h = 0x811c9dc5;
  for (const p of [...catalogo.parametros].sort((a, b) => a.clave.localeCompare(b.clave))) {
    for (const ch of `${p.clave}=${p.valor};`) {
      h = Math.imul(h ^ ch.charCodeAt(0), 0x01000193) >>> 0;
    }
  }
  return h.toString(16).padStart(8, '0');
}

const omitida = (r: CatalogoReglas['reglas'][number], estado: EstadoReglaTraza, por: string): TrazaRegla => ({
  ruleId: r.id,
  rama: r.rama,
  prioridad: r.prioridad,
  estado,
  comparaciones: [],
  acciones: [],
  bloqueadaPor: por,
  error: null,
});

export function evaluarMotor(catalogo: CatalogoReglas, entrada: EntradaMotor): TrazaEvaluacion {
  const reglas: TrazaRegla[] = [];
  let abortAll: string | null = null;
  let abortRiego: string | null = null;
  let abortInsumo: string | null = null;

  for (const regla of [...catalogo.reglas].sort((a, b) => a.prioridad - b.prioridad)) {
    if (abortAll) {
      reglas.push(omitida(regla, 'NO_ALCANZADA', abortAll));
      continue;
    }
    if (regla.rama === 'RIEGO' && abortRiego) {
      reglas.push(omitida(regla, 'OMITIDA_RAMA_BLOQUEADA', abortRiego));
      continue;
    }
    if (regla.rama === 'INSUMO' && abortInsumo) {
      reglas.push(omitida(regla, 'OMITIDA_RAMA_BLOQUEADA', abortInsumo));
      continue;
    }

    const ev = new Ev(catalogo);
    const acciones = (EVALUADORES[regla.id] ?? (() => []))(ev, entrada);
    reglas.push({
      ruleId: regla.id,
      rama: regla.rama,
      prioridad: regla.prioridad,
      estado: 'EVALUADA',
      comparaciones: ev.comparaciones,
      acciones,
      bloqueadaPor: null,
      error: null,
    });

    for (const a of acciones) {
      if (a.tipo === 'ABORT_ALL') abortAll ??= regla.id;
      else if (a.tipo === 'ABORT_RIEGO' || a.tipo === 'POSTPONE_RIEGO') abortRiego ??= regla.id;
      else if (a.tipo === 'ABORT_INSUMO') abortInsumo ??= regla.id;
    }
  }

  return {
    sectorId: entrada.sectorId,
    zonaId: entrada.zonaId,
    origen: entrada.origen,
    ts: entrada.ts,
    parametrosHash: huella(catalogo),
    reglas,
  };
}
