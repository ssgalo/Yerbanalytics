/* ============================================================
   Evaluación simulada del motor de reglas para la demo (modo `mock`).

   Espeja a las 13 reglas del backend y a `RuleOrchestrator.evaluate`: mismo orden de
   prioridad, mismas comparaciones, mismas reglas de corte por rama (ABORT_RIEGO y
   POSTPONE_RIEGO cortan RIEGO, ABORT_INSUMO corta INSUMO, ABORT_ALL corta todo). Los
   umbrales se leen del catálogo vigente, así que editar un parámetro en la demo cambia
   la traza como lo haría el sistema real.

   Riego (reglas_v2): R-04 saturado (2) → ciclo de lectura (3) → R-02 déficit crítico (4) →
   R-05 ventana (6) → R-06 pausa (7) → R-03 lluvia (8) → R-01 déficit (10). R-02 gana: corre
   antes y el orquestador conserva sus acciones aunque después se corte la rama; R-05/R-06/R-03
   sólo actúan cuando aplica R-01 (`crítico ≤ humedad < umbral`). R-04 va ANTES de la guarda de ciclo:
   recién regado el sector queda "ya regado en este ciclo" y la humedad sube; si el ciclo cortara primero,
   la saturación (su alerta) nunca se evaluaría.

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
  /** Segundos desde la última humedad de sustrato recibida; si no se informa, la de la lectura. */
  antiguedadHumSusSeg?: number | null;
  bloqueoManual: boolean;
  humSus: number | null;
  /** Probabilidad máxima de lluvia (%) en la ventana del pronóstico. */
  lluviaPct: number | null;
  /** Lluvia acumulada (mm) en la ventana del pronóstico; si no se informa, no hay dato. */
  lluviaMm?: number | null;
  uvIndex: number | null;
  dosis24h: number;
  /** Estado de salud del sector (`ok`, `warning`, `critical`, `offline`). */
  estadoSector: string;
  confianza: number | null;
  /** Epoch ms del último riego despachado al sector; null/ausente = nunca. */
  ultimoRiegoMs?: number | null;
  /** Epoch ms del último riego ordenado por R-02 (déficit crítico). */
  ultimoRiegoCriticoMs?: number | null;
  /** Epoch ms de la última aplicación de insumo (R-06). */
  ultimaAplicacionMs?: number | null;
  /** Epoch ms hasta el que el sector tiene un riego abierto; null/ausente = no está regando. */
  riegoEnCursoHastaMs?: number | null;
  /** Intervalo de sensado en minutos (el ciclo de lectura del riego). Por defecto 240. */
  cicloMin?: number;
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

  /** Valor vigente de un parámetro de ventana horaria ("06:00-18:00"). */
  ventana(clave: string): string {
    const p = this.catalogo.parametros.find((x) => x.clave === clave);
    if (!p) throw new Error(`Parámetro ${clave} no está en el catálogo`);
    return p.valor;
  }

  /** Registra "hora ∈ ventana" (operador `EN`): recibido "HH:mm", umbral la ventana vigente. */
  compararVentana(etiqueta: string, minuto: number | null, clave: string): boolean {
    const p = this.catalogo.parametros.find((x) => x.clave === clave);
    const texto = this.ventana(clave);
    const cumple = minuto !== null && dentroDeVentana(minuto, parseVentana(texto));
    this.comparaciones.push({
      etiqueta,
      clave,
      recibido: minuto === null ? null : hhmm(minuto),
      operador: 'EN',
      umbral: texto,
      unidad: p?.unidad ?? '',
      configurable: true,
      resultado: minuto === null ? 'SIN_DATO' : cumple ? 'CUMPLE' : 'NO_CUMPLE',
    });
    return cumple;
  }

  /** Condición que no se edita (p. ej. `estado == critical`). */
  compararFijo(
    etiqueta: string,
    recibido: string | boolean | number | null,
    op: OperadorComparacion,
    umbral: string | boolean | number,
  ): boolean {
    const sinDato = recibido === null;
    const cumple =
      !sinDato && (typeof recibido === 'number' && typeof umbral === 'number' ? aplicar(recibido, op, umbral) : recibido === umbral);
    this.comparaciones.push({
      etiqueta,
      clave: null,
      recibido,
      operador: op,
      umbral,
      unidad: '',
      configurable: false,
      resultado: sinDato ? 'SIN_DATO' : cumple ? 'CUMPLE' : 'NO_CUMPLE',
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
    case 'EN': throw new Error('El operador EN sólo aplica a ventanas horarias.');
  }
}

// --- Hora local del vivero (America/Argentina/Buenos_Aires, UTC-3 sin horario de verano) ---

const OFFSET_VIVERO_MS = -3 * 3_600_000;
const MIN_MS = 60_000;

/** Minutos desde la medianoche local (0–1439) de un instante. */
function minutosDelDia(ms: number): number {
  const local = new Date(ms + OFFSET_VIVERO_MS);
  return local.getUTCHours() * 60 + local.getUTCMinutes();
}

const hhmm = (min: number): string =>
  `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

/** "HH:mm" de un instante en la hora local del vivero. */
export const horaLocal = (ms: number): string => hhmm(minutosDelDia(ms));

function parseVentana(texto: string): { desde: number; hasta: number } {
  const [d, h] = texto.split('-');
  const min = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
  return { desde: min(d), hasta: min(h) };
}

/** Como `VentanaHoraria.contieneHastaElMinuto`: [desde, hasta) más el minuto de la hora de fin. */
function dentroDeVentana(minuto: number, v: { desde: number; hasta: number }): boolean {
  const contiene = v.desde < v.hasta ? minuto >= v.desde && minuto < v.hasta : minuto >= v.desde || minuto < v.hasta;
  return contiene || minuto === v.hasta;
}

/** Inicio (epoch ms) del ciclo de lectura que contiene `ms`: franjas de `pasoMin` ancladas a las 02:00 locales. */
export function inicioDeCiclo(ms: number, cicloMin: number): number {
  const paso = Math.min(360, Math.max(60, cicloMin));
  const local = ms + OFFSET_VIVERO_MS;
  const dia = Math.floor(local / 86_400_000) * 86_400_000;
  let ancla = dia + 2 * 3_600_000;
  if (local < ancla) ancla -= 86_400_000;
  const transcurridos = Math.floor((local - ancla) / MIN_MS);
  return ancla + Math.floor(transcurridos / paso) * paso * MIN_MS - OFFSET_VIVERO_MS;
}

// --- Cálculo del riego (CalculoRiego.java) ---

/** Duración máxima de la válvula del contrato MQTT (`ContratoNodo.DURACION_VALVULA_MAX_SEG`). */
const DURACION_VALVULA_MAX_SEG = 1200;

interface PlanRiego {
  volumenL: number;
  duracionSeg: number;
  recortado: boolean;
}

/** Redondea a 2 decimales HALF_UP sin el ruido del punto flotante (21 × 0,2 = 4,2000000000000002). */
const redondear2 = (v: number): number => Math.round(Number((v * 100).toFixed(6))) / 100;

function planDeRiego(volumen: number, caudalLh: number): PlanRiego {
  const v = redondear2(volumen);
  if (v <= 0) return { volumenL: 0, duracionSeg: 0, recortado: false };
  const t = Math.ceil(Number(((v * 3600) / caudalLh).toFixed(6)));
  const recortado = t > DURACION_VALVULA_MAX_SEG;
  return { volumenL: v, duracionSeg: recortado ? DURACION_VALVULA_MAX_SEG : t, recortado };
}

/** Como el `%.0f` de los motivos del backend: sin decimales de más. */
const r0 = (v: number | null) => (v === null ? '—' : String(Math.round(v)));

/** Número sin ceros de más y con coma decimal: 44 → "44", 4.2 → "4,2" (`RiegoRuleSupport.num`). */
const num = (v: number): string => String(Number(v.toFixed(6))).replace('.', ',');

/** "4,2 L (504 s)". */
const volumenYTiempo = (p: PlanRiego): string => `${num(p.volumenL)} L (${p.duracionSeg} s)`;

const accion = (tipo: string, motivo: string): AccionTraza => ({ tipo, motivo });
const noop = (motivo: string) => [accion('NOOP_INFO', motivo)];

/** `true` si aplica R-01: `crítico ≤ humedad < umbral`. Registra las DOS comparaciones, siempre (`RiegoRuleSupport.aplicaR01`). */
function aplicaR01(ev: Ev, humedad: number | null): boolean {
  const sobreElCritico = ev.comparar('Humedad de sustrato', humedad, 'GE', 'riego.umbral-critico');
  const bajoElUmbral = ev.comparar('Humedad de sustrato', humedad, 'LT', 'riego.umbral-humedad');
  return sobreElCritico && bajoElUmbral;
}

/** Motivo común de las compuertas (R-03/R-05/R-06) cuando R-01 no aplica. */
function noAplica(ev: Ev, humedad: number | null): string {
  if (humedad === null) return 'No aplica: sin lectura de humedad de sustrato.';
  const critico = ev.numero('riego.umbral-critico');
  if (humedad < critico) {
    return `No aplica: la humedad ${num(humedad)}% está bajo el umbral crítico (${num(critico)}%), lo cubre el déficit crítico (R-02).`;
  }
  return `No aplica: la humedad ${num(humedad)}% no está bajo el umbral de riego (${num(ev.numero('riego.umbral-humedad'))}%), no hay déficit.`;
}

type Evaluador = (ev: Ev, e: EntradaMotor) => AccionTraza[];

/** Cuerpo de cada regla, calcado de su clase en `engine/rules/`. */
const EVALUADORES: Record<string, Evaluador> = {
  ManualLockRule: (ev, e) =>
    ev.compararFijo('Bloqueo manual activo', e.bloqueoManual, 'EQ', true)
      ? [accion('ABORT_ALL', 'Bloqueo manual activo en el sector: se detiene toda actuación autónoma.')]
      : noop('Sin bloqueo manual.'),

  StaleSensorRule: (ev, e) => {
    const vieja = ev.comparar('Antigüedad de la última lectura', e.antiguedadSeg, 'GT', 'seguridad.antiguedad-max-lectura');
    // La humedad de sustrato se mide aparte: si la sonda falla, el resto de la lectura sigue fresco.
    const antiguedadHum = e.antiguedadHumSusSeg === undefined ? e.antiguedadSeg : e.antiguedadHumSusSeg;
    const humVieja = ev.comparar('Antigüedad de la humedad de sustrato', antiguedadHum, 'GT', 'seguridad.antiguedad-max-lectura');
    // Sin lectura no hay nada que comparar, pero tampoco se puede regar a ciegas.
    if (e.antiguedadSeg === null || vieja) {
      return [accion('ABORT_RIEGO', `El nodo testigo de la macro-zona ${e.zonaId} no reportó dentro del umbral de antigüedad configurado. Actuación autónoma anulada por seguridad.`)];
    }
    if (antiguedadHum === null || humVieja) {
      return [accion('ABORT_RIEGO', `La macro-zona ${e.zonaId} reporta, pero la humedad de sustrato no se actualizó dentro del umbral de antigüedad configurado (¿falla de la sonda?). Riego autónomo anulado por seguridad.`)];
    }
    return noop('Telemetría fresca: el nodo reportó dentro del umbral configurado.');
  },

  // Protección contra el riego en bucle: a lo sumo un riego por sector y ciclo de lectura.
  CicloLecturaRiegoRule: (ev, e) => {
    const ahora = Date.parse(e.ts);
    const inicio = inicioDeCiclo(ahora, e.cicloMin ?? 240);
    const minDesdeRiego = e.ultimoRiegoMs == null ? null : (ahora - e.ultimoRiegoMs) / MIN_MS;
    const minDeCiclo = (ahora - inicio) / MIN_MS;
    const etiquetaRiego = 'Minutos desde el último riego (contra los del ciclo en curso)';
    const yaRegado = ev.compararFijo(etiquetaRiego, minDesdeRiego, 'LE', minDesdeRiego === null ? 0 : minDeCiclo);
    const restante = e.riegoEnCursoHastaMs == null ? 0 : Math.max(0, (e.riegoEnCursoHastaMs - ahora) / 1000);
    const enCurso = ev.compararFijo('Segundos restantes del riego en curso', restante, 'GT', 0);
    // Ya regó en el ciclo: corta sólo a R-01. Bajo el umbral crítico decide R-02, que no tiene guarda de ciclo
    // (sólo su tope de horas); sin lectura de humedad corta (sin dato no se arriesga).
    const deficitCritico = yaRegado && ev.comparar('Humedad de sustrato', e.humSus, 'LT', 'riego.umbral-critico');
    if (yaRegado && !deficitCritico) {
      return [accion('ABORT_RIEGO', `El sector ya se regó en este ciclo de lectura (último riego a las ${horaLocal(e.ultimoRiegoMs!)}, el ciclo empezó a las ${horaLocal(inicio)}). Un riego por sector y ciclo.`)];
    }
    // Un riego en curso corta a todos, R-02 incluida.
    if (enCurso) {
      return [accion('ABORT_RIEGO', `El sector tiene un riego en curso hasta las ${horaLocal(e.riegoEnCursoHastaMs!)}: no se riega de nuevo.`)];
    }
    if (deficitCritico) {
      return noop('El sector ya se regó en este ciclo de lectura, pero hay déficit crítico: la guarda de ciclo no aplica a R-02 (sólo su tope de horas).');
    }
    return noop('El sector no regó en este ciclo de lectura ni tiene un riego en curso.');
  },

  // R-04: sustrato saturado.
  SustratoSaturadoRule: (ev, e) => {
    const saturado = ev.comparar('Humedad de sustrato', e.humSus, 'GE', 'riego.saturacion-bloqueo');
    if (e.humSus === null) return noop('Sin lectura de humedad de sustrato: no se evalúa la saturación.');
    const bloqueo = ev.numero('riego.saturacion-bloqueo');
    if (!saturado) {
      return noop(`Humedad de sustrato ${num(e.humSus)}% bajo el umbral de saturación (${num(bloqueo)}%): no se bloquea el riego.`);
    }
    const aborta = accion('ABORT_RIEGO', `Humedad de sustrato ${num(e.humSus)}% en o sobre el umbral de saturación (${num(bloqueo)}%): riego autónomo bloqueado.`);
    if (!ev.comparar('Humedad de sustrato', e.humSus, 'GE', 'riego.saturacion-alerta')) return [aborta];
    return [
      aborta,
      accion('ALERTA', `Humedad de sustrato ${num(e.humSus)}% en o sobre el umbral de alerta (${num(ev.numero('riego.saturacion-alerta'))}%).`),
    ];
  },

  // R-02: déficit crítico. Gana a R-05, R-06 y R-03 porque corre antes.
  DeficitCriticoRule: (ev, e) => {
    const critico = ev.comparar('Humedad de sustrato', e.humSus, 'LT', 'riego.umbral-critico');
    if (!critico) {
      return noop(
        e.humSus === null
          ? 'Sin lectura de humedad de sustrato: no se evalúa el déficit crítico.'
          : `Humedad de sustrato ${num(e.humSus)}% sin déficit crítico (umbral ${num(ev.numero('riego.umbral-critico'))}%).`,
      );
    }
    // Tope: un riego crítico por sector cada N horas (sin riego crítico previo no hay con qué comparar).
    if (e.ultimoRiegoCriticoMs != null) {
      const horas = (Date.parse(e.ts) - e.ultimoRiegoCriticoMs) / 3_600_000;
      if (ev.comparar('Horas desde el último riego por déficit crítico', horas, 'LT', 'riego.exceptuado-bloqueo')) {
        return [accion('ABORT_RIEGO', `Déficit crítico (${num(e.humSus!)}%), pero el sector ya recibió un riego crítico a las ${horaLocal(e.ultimoRiegoCriticoMs)}: se respeta el tope de un riego cada ${num(ev.numero('riego.exceptuado-bloqueo'))} h para no regar sin fin con un sensor que pueda estar fallando.`)];
      }
    }
    const plan = planDeRiego(ev.numero('riego.volumen-max-evento'), ev.numero('riego.caudal-emisor'));
    if (plan.duracionSeg < 1) {
      return noop(`Déficit crítico (${num(e.humSus!)}%), pero el volumen calculado es 0 L: no se ordena el riego.`);
    }
    return [
      accion(
        'ACTIVAR_VALVULA',
        `Déficit hídrico crítico: humedad de sustrato ${num(e.humSus!)}% bajo ${num(ev.numero('riego.umbral-critico'))}%. Regar ${volumenYTiempo(plan)} con el volumen máximo, a cualquier hora${plan.recortado ? ' (duración recortada al máximo de la válvula)' : ''}.`,
      ),
      accion('ALERTA', `Humedad de sustrato ${num(e.humSus!)}% bajo el umbral crítico.`),
    ];
  },

  // R-05: fuera de la ventana horaria. Sólo actúa cuando aplica R-01.
  FueraDeVentanaRiegoRule: (ev, e) => {
    if (!aplicaR01(ev, e.humSus)) return noop(noAplica(ev, e.humSus));
    const dentro = ev.compararVentana('Hora local', minutosDelDia(Date.parse(e.ts)), 'riego.ventana-normal');
    return dentro
      ? noop('Dentro de la ventana de riego.')
      : [accion('ABORT_RIEGO', `Fuera de la ventana de riego (${ev.ventana('riego.ventana-normal')}): el riego por déficit espera a la próxima lectura dentro de ella. Sólo el déficit crítico riega de noche.`)];
  },

  // R-06: pausa tras una aplicación de insumo. Sólo actúa cuando aplica R-01.
  PausaTrasAplicacionRule: (ev, e) => {
    if (!aplicaR01(ev, e.humSus)) return noop(noAplica(ev, e.humSus));
    if (e.ultimaAplicacionMs == null) return noop('El sector no tiene aplicaciones recientes de insumo.');
    const horas = (Date.parse(e.ts) - e.ultimaAplicacionMs) / 3_600_000;
    if (ev.comparar('Horas desde la última aplicación de insumo', horas, 'LT', 'riego.pausa-tras-aplicacion')) {
      return [accion('ABORT_RIEGO', `Al sector se le aplicó un insumo a las ${horaLocal(e.ultimaAplicacionMs)} (hace menos de ${num(ev.numero('riego.pausa-tras-aplicacion'))} h): no se riega para no lavar el producto.`)];
    }
    return noop('La pausa tras la última aplicación de insumo ya se cumplió.');
  },

  // R-03: posponer por lluvia prevista. Sólo actúa cuando aplica R-01.
  PosponerPorLluviaRule: (ev, e) => {
    if (!aplicaR01(ev, e.humSus)) return noop(noAplica(ev, e.humSus));
    const horas = Math.round(ev.numero('riego.lluvia-ventana'));
    const probMax = e.lluviaPct;
    const mmTotal = e.lluviaMm ?? null;
    // Una hora sin dato no es una hora seca: si falta la probabilidad o los mm, esa comparación queda SIN_DATO y no pospone.
    const probable = ev.comparar(`Probabilidad máxima de lluvia en las próximas ${horas} h`, probMax, 'GE', 'riego.lluvia-probabilidad');
    const abundante = ev.comparar(`Lluvia acumulada en las próximas ${horas} h`, mmTotal, 'GE', 'riego.lluvia-mm');
    if (probMax === null || mmTotal === null) {
      return noop('Pronóstico de lluvia incompleto o no disponible: se asume que no llueve y el riego sigue su curso.');
    }
    if (probable && abundante) {
      const detalle = `probabilidad máxima ${num(probMax)}% y ${num(mmTotal)} mm en las próximas ${horas} h`;
      return [
        accion('POSTPONE_RIEGO', `Riego pospuesto por lluvia prevista (${detalle}). En la próxima lectura se decide de nuevo.`),
        accion('ALERTA', `Lluvia prevista: ${detalle}.`),
      ];
    }
    return noop(`Lluvia prevista insuficiente para posponer (probabilidad máxima ${num(probMax)}%, ${num(mmTotal)} mm en ${horas} h).`);
  },

  // R-01: riego por déficit hídrico.
  RiegoPorDeficitRule: (ev, e) => {
    const critico = ev.numero('riego.umbral-critico');
    const umbral = ev.numero('riego.umbral-humedad');
    if (!aplicaR01(ev, e.humSus)) {
      return noop(
        e.humSus !== null && e.humSus < critico
          ? `Humedad ${num(e.humSus)}% bajo el umbral crítico: lo cubre R-02 (déficit crítico).`
          : noAplica(ev, e.humSus),
      );
    }
    const deficit = ev.numero('riego.humedad-objetivo') - e.humSus!;
    const plan = planDeRiego(
      Math.min(Math.max(deficit * ev.numero('riego.litros-por-punto'), 0), ev.numero('riego.volumen-max-evento')),
      ev.numero('riego.caudal-emisor'),
    );
    if (plan.duracionSeg < 1) {
      return noop(`Humedad de sustrato ${num(e.humSus!)}% bajo el umbral, pero el volumen calculado es 0 L: no se riega.`);
    }
    return [
      accion(
        'ACTIVAR_VALVULA',
        `Humedad de sustrato ${num(e.humSus!)}% bajo el umbral de riego (${num(umbral)}%). Regar ${volumenYTiempo(plan)} hasta la humedad objetivo${plan.recortado ? ' (duración recortada al máximo de la válvula)' : ''}.`,
      ),
    ];
  },

  DailyDoseLimitRule: (ev, e) =>
    ev.comparar('Dosificaciones en las últimas 24 h', e.dosis24h, 'GE', 'insumo.max-dosis-24h')
      ? [accion('ABORT_INSUMO', `El sector ya recibió ${e.dosis24h} dosis en las últimas 24 h: límite alcanzado.`)]
      : noop(`${e.dosis24h} dosis en las últimas 24 h — dentro del límite.`),

  SupplyRule: (ev, e) => {
    if (!ev.compararFijo('Estado del sector', e.estadoSector, 'EQ', 'critical')) {
      return noop('El sector no está en estado crítico — no se dosifica.');
    }
    return ev.comparar('Confianza del diagnóstico', e.confianza, 'GE', 'diagnostico.confianza-minima')
      ? [accion('ACTIVAR_BOMBA', `Sector crítico con diagnóstico confiable (${r0(e.confianza)}%). Se dosifica el insumo.`)]
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
