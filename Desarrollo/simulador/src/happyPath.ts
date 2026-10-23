/* ============================================================
   Happy path de la Demo Expo: riego + insumo + mediasombra en un solo botón y UN solo pulso.

   El nodo serializa las cargas (cola de cargas del ESP32, `add-cola-actuadores-nodo`): el motor puede ordenar las
   tres acciones juntas sin que la fuente del prototipo caiga. Antes este módulo las escalonaba en tres pulsos; ya no.

   Módulo sin React ni fetch (las llamadas se inyectan en `HappyPathDeps`), para poder probar la
   decisión y la orquestación sin backend. NO duplica nada: el modo SIMULADOR combina los escenarios
   que ya existen (R01 + SUPPLY + SHADING) a través de `calculateConditions`, así que los umbrales
   salen del catálogo vivo y no hay números pegados acá.

   Dos modos, según el interruptor de fuente de telemetría del backend:
     SIMULATOR  el simulador GENERA todo: lectura, clima y diagnóstico de IA, y dispara un pulso con motor.
     REAL_NODE  no inyecta nada (sensor real, pronóstico real, diagnóstico de una pasada del riel previa):
                sólo se asegura de no dejar un clima simulado vivo y dispara el pulso.
   ============================================================ */
import { withBypasses } from './bypassFlow';
import { SHADE_DEPLOY_BELOW_OPENING_PCT } from '../server/contract';
import { buildThresholds, calculateConditions, type RuleConditionResult, type ThresholdMap } from './ruleConditions';
import { runPulses } from './pulseChain';
import type { DemoRule, DemoRuleUpdate, EvaluationTrace, ParametroRegla, SectorRef, SimulatedWeather } from './types';
import type { DiagnosisRequest } from './captureFlow';

export type HappyPathMode = 'SIMULATOR' | 'REAL_NODE';

/** R-05 (ventana horaria de riego): en modo simulador se saltea para que la demo ande a cualquier hora. */
export const WINDOW_RULE = 'FueraDeVentanaRiegoRule';
export const WINDOW_BYPASS_KEY = 'window';

/**
 * La dosificación v1 arranca RETIRADA en el backend (`yerbanalytics.reglas.retiradas`) hasta que entren las reglas de
 * aplicación de la v2. En modo simulador el happy path la enciende (es un interruptor de demo, como el bypass de R-05).
 */
export const SUPPLY_RULE = 'SupplyRule';

/**
 * Las precondiciones de aplicación (P-D1…P-D5). En modo simulador se saltean la ventana horaria (como R-05) y el límite de
 * una aplicación por día, para que la demo ande a cualquier hora y se pueda repetir. El resto (temperatura, lluvia,
 * humedad, stock) las deja cumplidas el escenario APPLICATION_PRECONDITIONS.
 */
export const PRECONDITIONS_RULE = 'ApplicationPreconditions';
export const PRECONDITIONS_BYPASS_KEYS = ['window', 'daily'];

/** Escenarios que se combinan: riego por déficit, dosificación (con sus precondiciones) y mediasombra por UV. */
export const HAPPY_PATH_SCENARIOS = ['R01', 'SUPPLY', 'SHADING', 'APPLICATION_PRECONDITIONS'];

export interface HappyTarget {
  zonaId: string;
  sectorId: string;
  /** Serial del sensor simulado que publica la lectura; sólo en modo simulador. */
  serial: string | null;
}

function zoneOrder(a: string, b: string): number {
  return a.localeCompare(b, undefined, { numeric: true });
}

/**
 * Primera macro-zona con sectores (orden natural: MZ-2 antes que MZ-10) y su primer sector.
 * En modo simulador además hace falta un sensor simulado de esa zona: se toma la primera que lo tenga.
 */
export function pickTarget(
  sectors: SectorRef[],
  sensors: { serial: string; zonaId: string }[],
  mode: HappyPathMode,
): HappyTarget | null {
  const zones = [...new Set(sectors.map((s) => s.zonaId))].sort(zoneOrder);
  for (const zonaId of zones) {
    const sector = sectors.filter((s) => s.zonaId === zonaId).sort((a, b) => zoneOrder(a.id, b.id))[0];
    if (mode === 'REAL_NODE') return { zonaId, sectorId: sector.id, serial: null };
    const sensor = sensors.find((s) => s.zonaId === zonaId);
    if (sensor) return { zonaId, sectorId: sector.id, serial: sensor.serial };
  }
  return null;
}

export function hintFor(mode: HappyPathMode): string {
  return mode === 'SIMULATOR'
    ? 'Modo simulador: el simulador genera la lectura, el clima y el diagnóstico de IA para que riego, insumo y mediasombra actúen.'
    : 'Modo ESP32 real: usa el sensor real, el pronóstico real y el diagnóstico de IA de una pasada del riel previa. No inyecta nada.';
}

/** Lo que el simulador inyecta en modo SIMULATOR: las tres reglas combinadas en UNA lectura coherente. */
export function buildSimulatorPlan(thresholds: ThresholdMap): { conditions: RuleConditionResult } {
  return { conditions: calculateConditions(HAPPY_PATH_SCENARIOS, thresholds) };
}

// Vive en `bypassFlow.ts` (lo comparte con los escenarios que piden bypasses); se re-exporta por compatibilidad.
export { withBypasses };

/** Lista de bypasses con `window` agregado, o `null` si ya estaba (no hace falta tocar nada). */
export function withWindowBypass(rule: DemoRule): string[] | null {
  return withBypasses(rule, [WINDOW_BYPASS_KEY]);
}

/* ---------------- lectura de la traza ---------------- */

export interface RuleReadout {
  acted: boolean;
  detail: string;
}

export interface HappyPathReadout {
  riego: RuleReadout;
  insumo: RuleReadout;
  mediasombra: RuleReadout;
  allActed: boolean;
}

const BLOCKING = ['ABORT_RIEGO', 'POSTPONE_RIEGO', 'ABORT_INSUMO', 'ABORT_MEDIASOMBRA', 'ABORT_ALL'];

const STATE_TEXT: Record<string, string> = {
  OMITIDA_SIN_DIAGNOSTICO_NUEVO: 'sin diagnóstico de IA nuevo (no hay uno o ya fue usado)',
  OMITIDA_DESHABILITADA: 'regla apagada',
  OMITIDA_RAMA_BLOQUEADA: 'la rama quedó bloqueada por una regla anterior',
  NO_ALCANZADA: 'una regla anterior cortó la evaluación',
};

function branchReadout(trace: EvaluationTrace, branch: string, actionType: string, label: string): RuleReadout {
  const rules = trace.reglas.filter((r) => r.rama.toUpperCase() === branch);
  if (rules.length === 0) return { acted: false, detail: `${label}: la rama no se evaluó` };

  const acting = rules.flatMap((r) => r.acciones).find((a) => a.tipo === actionType);
  if (acting) return { acted: true, detail: acting.motivo };

  const errored = rules.find((r) => r.error);
  if (errored) return { acted: false, detail: errored.error as string };
  const blocking = rules.flatMap((r) => r.acciones).find((a) => BLOCKING.includes(a.tipo));
  if (blocking) return { acted: false, detail: blocking.motivo };
  const blocked = rules.find((r) => r.bloqueadaPor);
  if (blocked) return { acted: false, detail: `bloqueada por ${blocked.bloqueadaPor}` };
  const noted = rules.flatMap((r) => r.acciones).find((a) => a.motivo);
  if (noted) return { acted: false, detail: noted.motivo };
  const skipped = rules.find((r) => STATE_TEXT[r.estado]);
  if (skipped) return { acted: false, detail: STATE_TEXT[skipped.estado] };
  return { acted: false, detail: 'sin acción' };
}

/** «Mediasombra cerrada/abierta» + el motivo, sin repetir lo que el motivo ya dice. */
function shadeDetail(openingPct: number, reason: string): string {
  const state = openingPct < SHADE_DEPLOY_BELOW_OPENING_PCT ? 'cerrada' : 'abierta';
  return /^Mediasombra (cerrada|abierta)/.test(reason) ? reason : `Mediasombra ${state} · ${reason}`;
}

/** Resumen de la traza del Inspector: por rama, si actuó y por qué sí o por qué no. */
export function summarizeTrace(trace: EvaluationTrace): HappyPathReadout {
  const riego = branchReadout(trace, 'RIEGO', 'ACTIVAR_VALVULA', 'Riego');
  const insumo = branchReadout(trace, 'INSUMO', 'ACTIVAR_BOMBA', 'Insumo');
  const shade = branchReadout(trace, 'MEDIASOMBRA', 'MOVER_MEDIASOMBRA', 'Mediasombra');
  // La mediasombra es de la macro-zona: su rama llega con la traza de la zona, sumada a la del sector. Un backend anterior
  // pegaba la apertura al motivo ([apertura=N]); el actual ya dice «Mediasombra cerrada/abierta».
  const opening = /\[apertura=(\d+)\]/.exec(shade.detail);
  // El hardware es binario: la orden lleva 100 (abierta) o 0 (cerrada) y el texto no muestra porcentajes.
  const mediasombra = shade.acted && opening
    ? { acted: true, detail: shadeDetail(Number(opening[1]), shade.detail.replace(opening[0], '').trim()) }
    : shade;
  return { riego, insumo, mediasombra, allActed: riego.acted && insumo.acted && mediasombra.acted };
}

/**
 * Umbrales vigentes del catálogo. Si no se puede leerlo (por ejemplo 403 por falta de `reglas.ver`) se usan los valores
 * de fábrica, pero con un aviso visible: de lo contrario el plan podría armarse con umbrales distintos de los reales.
 */
export async function loadThresholds(
  fetchCatalog: () => Promise<ParametroRegla[]>,
): Promise<{ thresholds: ThresholdMap; warning: string | null }> {
  try {
    return { thresholds: buildThresholds(await fetchCatalog()), warning: null };
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    return {
      thresholds: buildThresholds([]),
      warning: `No se pudo leer el catálogo de umbrales del backend (${reason}). Se usan valores de fábrica; ¿le falta \`reglas.ver\` al rol Servicio?`,
    };
  }
}

/* ---------------- pre-chequeo del diagnóstico (modo ESP32 real) ---------------- */

/**
 * Hay un diagnóstico anómalo con confianza suficiente. Lo que la API pública NO dice es si ya fue usado
 * por el motor, así que un "ok" no lo garantiza: la traza final lo confirma.
 */
export function diagnosisPrecheck(
  diagnosis: { estado: string; conf: number | null } | null,
  minConfidence: number,
): { ok: boolean; reason: string } {
  if (!diagnosis) return { ok: false, reason: 'el sector no tiene diagnóstico de IA' };
  if (diagnosis.estado === 'Sano' || diagnosis.estado === 'No concluyente') {
    return { ok: false, reason: `el diagnóstico vigente es «${diagnosis.estado}»: el insumo sólo actúa con uno anómalo` };
  }
  if (diagnosis.conf == null || diagnosis.conf < minConfidence) {
    return { ok: false, reason: `la confianza del diagnóstico (${diagnosis.conf ?? 's/d'} %) no llega al mínimo (${minConfidence} %)` };
  }
  return { ok: true, reason: `diagnóstico ${diagnosis.estado} (${diagnosis.conf} %)` };
}

/* ---------------- avance de cada acción ---------------- */

/**
 * Avance de una acción desde que el motor la ordena. `ordenada` = la decisión salió, el nodo todavía no avisó nada;
 * `en cola` = el nodo la tiene esperando turno (la fuente del prototipo no banca varias cargas a la vez, así que el
 * nodo las serializa: ver `add-cola-actuadores-nodo`); `en curso` = está actuando; `hecho` = terminó.
 */
export type ProgressLabel = 'sin acción' | 'ordenada' | 'en cola' | 'en curso' | 'hecho';

export interface ActionProgress {
  riego: ProgressLabel;
  insumo: ProgressLabel;
  mediasombra: ProgressLabel;
}

/** Estado de los actuadores de un sector, como lo deriva el backend (snapshot del vivero). */
export interface SectorActuators {
  sectorId: string;
  valve: string;
  pump: string;
}

/** Avance al salir la traza: lo que no actuó queda en `sin acción`; el resto, `ordenada`. */
export function initialProgress(readout: HappyPathReadout | null): ActionProgress {
  const of = (r: RuleReadout | undefined): ProgressLabel => (r?.acted ? 'ordenada' : 'sin acción');
  return { riego: of(readout?.riego), insumo: of(readout?.insumo), mediasombra: of(readout?.mediasombra) };
}

function advance(prev: ProgressLabel, state: string, queued: string, running: string): ProgressLabel {
  if (prev === 'sin acción' || prev === 'hecho') return prev;
  if (state === queued) return 'en cola';
  if (state === running) return 'en curso';
  // Ya no está ni en cola ni actuando: si antes lo estaba, terminó; si todavía no apareció, sigue `ordenada`.
  return prev === 'ordenada' ? prev : 'hecho';
}

/**
 * Siguiente avance a partir del snapshot: válvula `En cola` → en cola, `Regando` → en curso, `Cerrada` → hecho (si ya
 * había aparecido); bomba `En cola` / `Dosificando` / `En espera`. La mediasombra no tiene estado derivado del nodo:
 * queda `ordenada` (lo que dice la traza).
 */
export function nextProgress(prev: ActionProgress, sector: SectorActuators | undefined): ActionProgress {
  if (!sector) return prev;
  return {
    riego: advance(prev.riego, sector.valve, 'En cola', 'Regando'),
    insumo: advance(prev.insumo, sector.pump, 'En cola', 'Dosificando'),
    mediasombra: prev.mediasombra,
  };
}

/** Terminó todo lo que tenía para terminar (la mediasombra no se sigue). */
export function progressDone(progress: ActionProgress): boolean {
  return (progress.riego === 'sin acción' || progress.riego === 'hecho')
    && (progress.insumo === 'sin acción' || progress.insumo === 'hecho');
}

/** Lee el snapshot y devuelve el avance siguiente. Un snapshot que falla deja el avance como estaba. */
export async function readProgress(
  prev: ActionProgress,
  sectorId: string,
  getActuators: () => Promise<SectorActuators[]>,
): Promise<ActionProgress> {
  try {
    return nextProgress(prev, (await getActuators()).find((a) => a.sectorId === sectorId));
  } catch {
    return prev;
  }
}

/* ---------------- orquestación ---------------- */

export interface HappyPathDeps {
  zeroCooldowns(): Promise<unknown>;
  getRules(): Promise<DemoRule[]>;
  updateRule(name: string, update: DemoRuleUpdate): Promise<unknown>;
  /** Registra o repone los tanques de insumo de la zona y devuelve las líneas para el registro de la corrida. */
  ensureTankStock(zonaId: string): Promise<string[]>;
  setWeather(weather: SimulatedWeather): Promise<unknown>;
  clearWeather(): Promise<void>;
  setOffline(on: boolean): Promise<unknown>;
  getEmission(): Promise<boolean>;
  setEmission(on: boolean): Promise<unknown>;
  /** Lecturas fijadas hoy en la zona (unidades de la UI). */
  getPinned(zonaId: string): Promise<Record<string, number>>;
  pinReadings(zonaId: string, metrics: Record<string, number>): Promise<unknown>;
  registerDiagnosis(request: DiagnosisRequest): Promise<{ id: string }>;
  getSectorDiagnosis(sectorId: string): Promise<{ estado: string; conf: number | null } | null>;
  triggerPulse(): Promise<void>;
  getTrace(sectorId: string): Promise<EvaluationTrace | null>;
  sleep(ms: number): Promise<void>;
  log(line: string): void;
}

export interface HappyPathOptions {
  tracePollMs?: number;
  maxTracePolls?: number;
}

export interface HappyPathOutcome {
  /** Lo que la traza del pulso dice de cada rama; `null` si no llegó una traza nueva a tiempo. */
  readout: HappyPathReadout | null;
  /** Lo que la corrida dejó activo a propósito (se muestra en la tarjeta de resultado). */
  leftActive: string[];
  warnings: string[];
}

function instantMs(ts: string | number): number {
  return typeof ts === 'number' ? ts : Date.parse(ts);
}

/**
 * Ejecuta el happy path: UN pulso con motor y las tres reglas tal como están (el nodo serializa las cargas, así que no
 * hace falta escalonarlas). No enciende ni apaga reglas ni espera que una acción termine: el avance de cada una lo lee
 * la tarjeta del snapshot (`readProgress`). Lanza si algo del armado falla; limpia siempre.
 */
export async function runHappyPath(
  mode: HappyPathMode,
  target: HappyTarget,
  thresholds: ThresholdMap,
  deps: HappyPathDeps,
  options: HappyPathOptions = {},
): Promise<HappyPathOutcome> {
  const warnings: string[] = [];
  const leftActive: string[] = [];
  const pollMs = options.tracePollMs ?? 2_000;
  const maxPolls = options.maxTracePolls ?? 45;

  let lastTs: number | null = await deps.getTrace(target.sectorId).then((t) => (t ? instantMs(t.ts) : null), () => null);

  let restoreSimulation: (() => Promise<void>) | null = null;
  const errors: string[] = [];

  try {
    if (mode === 'SIMULATOR') {
      if (!target.serial) throw new Error(`No hay un sensor simulado para ${target.zonaId}: creá uno en Configuración.`);
      const { conditions } = buildSimulatorPlan(thresholds);

      await deps.zeroCooldowns();
      deps.log('✓ Cooldowns de demo → 0');
      leftActive.push('cooldowns en 0');

      const rules = await deps.getRules();
      const windowRule = rules.find((r) => r.name === WINDOW_RULE);
      const bypassed = windowRule ? withWindowBypass(windowRule) : null;
      if (windowRule && bypassed) {
        await deps.updateRule(WINDOW_RULE, { bypassed });
        deps.log('✓ Ventana horaria de riego (R-05) salteada: la demo anda a cualquier hora');
      }
      // No se restaura: el despacho del riego revalida las guardas al abrir la válvula, después de que el motor decide.
      if (windowRule) leftActive.push('bypass de la ventana horaria de riego');

      // La dosificación v1 arranca retirada y la aplicación pasa por las precondiciones de la v2: se enciende la regla,
      // se saltean la ventana horaria y el límite diario, y se asegura stock en los tanques de la zona.
      const supplyRule = rules.find((r) => r.name === SUPPLY_RULE);
      if (supplyRule && !supplyRule.enabled) {
        await deps.updateRule(SUPPLY_RULE, { enabled: true });
        deps.log('✓ Dosificación (SupplyRule) encendida: arranca retirada hasta que entren las reglas de aplicación de la v2');
        leftActive.push('dosificación encendida');
      }
      const preconditions = rules.find((r) => r.name === PRECONDITIONS_RULE);
      const preconditionsBypass = preconditions ? withBypasses(preconditions, PRECONDITIONS_BYPASS_KEYS) : null;
      if (preconditions && preconditionsBypass) {
        await deps.updateRule(PRECONDITIONS_RULE, { bypassed: preconditionsBypass });
        deps.log('✓ Ventana horaria y límite diario de aplicación salteados: la demo anda a cualquier hora y se puede repetir');
      }
      if (preconditions) leftActive.push('bypass de la ventana y del límite diario de aplicación');
      if (conditions.ensureTankStock) {
        for (const line of await deps.ensureTankStock(target.zonaId)) deps.log(line);
      }

      if (conditions.clima) {
        await deps.setWeather(conditions.clima);
        deps.log(`✓ Clima simulado: UV ${conditions.clima.uvIndex} (umbral ${thresholds.uvUmbral}) → la mediasombra se protege`);
        leftActive.push('clima simulado');
      }

      // El motor decide con la PRIMERA lectura que llega tras el LEER_AHORA del pulso. Publicar una lectura antes no sirve:
      // la fijamos en la zona y encendemos la emisión para que alguien responda.
      const previousPin = await deps.getPinned(target.zonaId);
      const previousEmission = await deps.getEmission();
      restoreSimulation = async () => {
        await deps.pinReadings(target.zonaId, previousPin);
        if (!previousEmission) await deps.setEmission(false);
      };
      await deps.pinReadings(target.zonaId, conditions.telemetria);
      deps.log(
        `✓ ${target.zonaId} contestará el pulso con humSus ${conditions.telemetria.humSus} %, temp ${conditions.telemetria.temp} °C (lectura fijada)`,
      );
      if (!previousEmission) {
        await deps.setEmission(true);
        deps.log('✓ Emisión automática encendida para que la zona responda el pulso');
      }

      // El diagnóstico nace justo antes del pulso: así es «nuevo» para SupplyRule y ningún pulso anterior lo consumió.
      const diagnosis = conditions.diagnostico;
      if (diagnosis) {
        const created = await deps.registerDiagnosis({ ...diagnosis, sectorId: target.sectorId, zonaId: target.zonaId });
        deps.log(`✓ Diagnóstico IA ${created.id} en ${target.sectorId}: ${diagnosis.estado}, conf. ${diagnosis.conf} %`);
      }
    } else {
      await deps.clearWeather();
      await deps.setOffline(false);
      deps.log('✓ Sin clima simulado: rige el pronóstico real');
      // No se enciende nada (este modo no toca reglas), pero si la dosificación sigue retirada el insumo nunca va a actuar.
      const supplyRule = (await deps.getRules()).find((r) => r.name === SUPPLY_RULE);
      if (supplyRule && !supplyRule.enabled) {
        const warning = 'La dosificación (SupplyRule) está retirada: el insumo no va a actuar. Encendela en el panel de reglas o arrancá el backend con YERBANALYTICS_REGLAS_RETIRADAS vacío.';
        warnings.push(warning);
        deps.log(`⚠ ${warning}`);
      }
      // Nada debe pisar al nodo real: se suelta cualquier lectura fijada que haya quedado.
      await deps.pinReadings(target.zonaId, {});
      const check = diagnosisPrecheck(await deps.getSectorDiagnosis(target.sectorId), thresholds.confianzaMinima);
      if (check.ok) {
        deps.log(`✓ ${target.sectorId}: ${check.reason}`);
      } else {
        const warning = `Sin diagnóstico utilizable en ${target.sectorId} (${check.reason}): la regla de insumo se va a omitir. Hacé antes una pasada del riel.`;
        warnings.push(warning);
        deps.log(`⚠ ${warning}`);
      }
    }

    await runPulses(1, {
      trigger: () => deps.triggerPulse(),
      sleep: deps.sleep,
      onPulse: () => deps.log('✓ Pulso con motor: riego, insumo y mediasombra se evalúan juntos (el nodo las ejecuta de a una)'),
    });

    // El pulso es asíncrono (202): se espera una traza distinta de la anterior. Se compara contra la traza previa
    // y no contra el reloj, así no depende de que el simulador y el backend tengan la hora sincronizada.
    let readout: HappyPathReadout | null = null;
    let lastError: string | null = null;
    for (let i = 0; i < maxPolls && !readout; i++) {
      try {
        const trace = await deps.getTrace(target.sectorId);
        if (trace && instantMs(trace.ts) !== lastTs) {
          lastTs = instantMs(trace.ts);
          readout = summarizeTrace(trace);
          break;
        }
      } catch (e) {
        lastError = e instanceof Error ? e.message : String(e);
      }
      await deps.sleep(pollMs);
    }
    if (!readout) {
      warnings.push(
        lastError
          ? `No se pudo leer la traza del motor (${lastError}). Si es un 403, al rol Servicio le falta \`reglas.ver\`.`
          : 'El motor no publicó una traza nueva a tiempo: no se pudo leer qué hizo cada regla.',
      );
    }
    return { readout, warnings, leftActive };
  } finally {
    if (restoreSimulation) {
      try {
        await restoreSimulation();
      } catch (e) {
        errors.push(e instanceof Error ? e.message : String(e));
      }
    }
    deps.log(
      errors.length === 0
        ? '✓ Lectura fijada y emisión devueltas a su estado previo'
        : `⚠ No se pudo devolver todo a su estado previo: ${errors.join('; ')}`,
    );
  }
}
