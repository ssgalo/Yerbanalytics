/* ============================================================
   Happy path de la Demo Expo: riego + insumo + mediasombra en un solo botón.

   Módulo sin React ni fetch (las llamadas se inyectan en `HappyPathDeps`), para poder probar la
   decisión y la orquestación sin backend. NO duplica nada: el modo SIMULADOR combina los escenarios
   que ya existen (R01 + SUPPLY + SHADING) a través de `calculateConditions`, así que los umbrales
   salen del catálogo vivo y no hay números pegados acá.

   Dos modos, según el interruptor de fuente de telemetría del backend:
     SIMULATOR  el simulador GENERA todo: lectura, clima y diagnóstico de IA, y dispara un pulso con motor.
     REAL_NODE  no inyecta nada (sensor real, pronóstico real, diagnóstico de una pasada del riel previa):
                sólo se asegura de no dejar un clima simulado vivo y dispara el pulso.
   ============================================================ */
import { SHADE_DEPLOY_BELOW_OPENING_PCT } from '../server/contract';
import { buildThresholds, calculateConditions, type RuleConditionResult, type ThresholdMap } from './ruleConditions';
import { runPulses } from './pulseChain';
import type { DemoRule, DemoRuleUpdate, EvaluationTrace, ParametroRegla, SectorRef, SimulatedWeather } from './types';
import type { DiagnosisRequest } from './captureFlow';

export type HappyPathMode = 'SIMULATOR' | 'REAL_NODE';

/** R-05 (ventana horaria de riego): en modo simulador se saltea para que la demo ande a cualquier hora. */
export const WINDOW_RULE = 'FueraDeVentanaRiegoRule';
export const WINDOW_BYPASS_KEY = 'window';

/** Escenarios que se combinan: riego por déficit, dosificación y mediasombra por UV. */
export const HAPPY_PATH_SCENARIOS = ['R01', 'SUPPLY', 'SHADING'];

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

/** Lista de bypasses con `window` agregado, o `null` si ya estaba (no hace falta tocar nada). */
export function withWindowBypass(rule: DemoRule): string[] | null {
  return rule.bypassed.includes(WINDOW_BYPASS_KEY) ? null : [...rule.bypassed, WINDOW_BYPASS_KEY];
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

/** Resumen de la traza del Inspector: por rama, si actuó y por qué sí o por qué no. */
export function summarizeTrace(trace: EvaluationTrace): HappyPathReadout {
  const riego = branchReadout(trace, 'RIEGO', 'ACTIVAR_VALVULA', 'Riego');
  const insumo = branchReadout(trace, 'INSUMO', 'ACTIVAR_BOMBA', 'Insumo');
  const shade = branchReadout(trace, 'MEDIASOMBRA', 'MOVER_MEDIASOMBRA', 'Mediasombra');
  const opening = /\[apertura=(\d+)\]/.exec(shade.detail);
  // La orden lleva el % de APERTURA: por debajo del umbral del firmware la malla se despliega (queda cerrada).
  const mediasombra = shade.acted && opening
    ? {
        acted: true,
        detail: `Mediasombra ${Number(opening[1]) < SHADE_DEPLOY_BELOW_OPENING_PCT ? 'cerrada' : 'abierta'} (${opening[1]} % de apertura) · ${shade.detail.replace(opening[0], '').trim()}`,
      }
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

/* ---------------- orquestación ---------------- */

export type StepKey = 'riego' | 'insumo' | 'mediasombra';
/** `finishing` = esperando que termine la acción; `pause` = los 5 s entre pasos. */
export type StepStatus = 'pending' | 'running' | 'finishing' | 'pause' | 'done';

/** Orden de los pasos: una carga a la vez, para no sumar el arranque de todas (el ESP32 se reinicia por brownout). */
export const STEP_ORDER: StepKey[] = ['riego', 'insumo', 'mediasombra'];

/** Reglas ejecutoras (nombres de `GET /api/demo/rules`) que quedan encendidas en cada paso; el resto de las tres, apagadas. */
export const STEP_RULES: Record<StepKey, string[]> = {
  riego: ['RiegoPorDeficitRule', 'DeficitCriticoRule'],
  insumo: ['SupplyRule'],
  mediasombra: ['ShadingRule'],
};

/** Espera entre un paso y el siguiente (ms). */
export const STEP_WAIT_MS = 5_000;

const TOUCHED_RULES = STEP_ORDER.flatMap((k) => STEP_RULES[k]);

/** Segundos que se espera, a lo sumo, que termine la acción (más un margen): del motivo de la traza, o el tope del firmware. */
export function expectedSeconds(key: StepKey, detail: string): number {
  if (key === 'insumo') {
    const ml = /(\d+)\s*ml\b/.exec(detail)?.[1];
    return ml ? Number(ml) : 60; // 1 s por ml; tope de la bomba (LIMITE_INSUMO_SEG_MAX)
  }
  const sec = /(\d+)\s*s\b/.exec(detail)?.[1];
  return sec ? Number(sec) : 1200; // tope de la válvula (CONTRATO_VALVULA_DURACION_MAX_SEG)
}

/** Margen que se suma a la duración esperada antes de rendirse. */
export const FINISH_TIMEOUT_EXTRA_MS = 120_000;

/** Consulta cada `pollMs` hasta que `isFinished` dé true o se agote `timeoutMs` (tiempo acumulado de las esperas). */
export async function waitUntilFinished(
  isFinished: () => Promise<boolean>,
  opts: { pollMs: number; timeoutMs: number; sleep: (ms: number) => Promise<void> },
): Promise<{ finished: boolean }> {
  let elapsed = 0;
  for (;;) {
    if (await isFinished()) return { finished: true };
    if (elapsed >= opts.timeoutMs) return { finished: false };
    await opts.sleep(opts.pollMs);
    elapsed += opts.pollMs;
  }
}

export interface HappyPathDeps {
  zeroCooldowns(): Promise<unknown>;
  getRules(): Promise<DemoRule[]>;
  updateRule(name: string, update: DemoRuleUpdate): Promise<unknown>;
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
  /** Sectores de la zona con el estado de sus actuadores (snapshot del vivero). */
  getActuators(zonaId: string): Promise<{ sectorId: string; valve: string; pump: string }[]>;
  /** Avance de cada paso, para la tarjeta de resultado. */
  onStep?(key: StepKey, status: StepStatus): void;
}

export interface HappyPathOptions {
  tracePollMs?: number;
  maxTracePolls?: number;
  stepWaitMs?: number;
  /** Intervalo de consulta mientras se espera que termine la acción. */
  finishPollMs?: number;
  finishTimeoutExtraMs?: number;
}

export interface StepOutcome {
  key: StepKey;
  /** Lo que la traza de ESE pulso dice de la rama del paso; `null` si no llegó una traza nueva. */
  readout: RuleReadout | null;
}

export interface HappyPathOutcome {
  steps: StepOutcome[];
  /** Los tres pasos juntos; `null` si a alguno le faltó la traza. */
  readout: HappyPathReadout | null;
  /** Lo que la corrida dejó activo a propósito (se muestra en la tarjeta de resultado). */
  leftActive: string[];
  warnings: string[];
}

function instantMs(ts: string | number): number {
  return typeof ts === 'number' ? ts : Date.parse(ts);
}

/** Ejecuta el happy path completo: tres pulsos, uno por regla. Lanza si algo del armado falla; limpia siempre. */
export async function runHappyPath(
  mode: HappyPathMode,
  target: HappyTarget,
  thresholds: ThresholdMap,
  deps: HappyPathDeps,
  options: HappyPathOptions = {},
): Promise<HappyPathOutcome> {
  const warnings: string[] = [];
  const leftActive: string[] = [];
  const steps: StepOutcome[] = [];
  const stepWaitMs = options.stepWaitMs ?? STEP_WAIT_MS;
  const pollMs = options.tracePollMs ?? 2_000;
  const maxPolls = options.maxTracePolls ?? 45;
  const finishPollMs = options.finishPollMs ?? 2_000;
  const finishExtraMs = options.finishTimeoutExtraMs ?? FINISH_TIMEOUT_EXTRA_MS;
  const progress = (key: StepKey, status: StepStatus) => deps.onStep?.(key, status);

  let lastTs: number | null = await deps.getTrace(target.sectorId).then((t) => (t ? instantMs(t.ts) : null), () => null);

  // Estado previo de las reglas que se encienden y apagan (estado de demo): se devuelve al terminar, pase lo que pase.
  const rules = await deps.getRules();
  const previousEnabled = new Map<string, boolean>();
  for (const name of TOUCHED_RULES) {
    const rule = rules.find((r) => r.name === name);
    if (rule) previousEnabled.set(name, rule.enabled);
  }
  const currentEnabled = new Map(previousEnabled);
  const setEnabled = async (name: string, enabled: boolean) => {
    if (!currentEnabled.has(name) || currentEnabled.get(name) === enabled) return;
    await deps.updateRule(name, { enabled });
    currentEnabled.set(name, enabled);
  };
  const enableOnly = async (key: StepKey) => {
    for (const name of TOUCHED_RULES) await setEnabled(name, STEP_RULES[key].includes(name));
  };

  let restoreSimulation: (() => Promise<void>) | null = null;
  const errors: string[] = [];

  try {
    if (mode === 'SIMULATOR') {
      if (!target.serial) throw new Error(`No hay un sensor simulado para ${target.zonaId}: creá uno en Configuración.`);
      const { conditions } = buildSimulatorPlan(thresholds);

      await deps.zeroCooldowns();
      deps.log('✓ Cooldowns de demo → 0');
      leftActive.push('cooldowns en 0');

      const windowRule = rules.find((r) => r.name === WINDOW_RULE);
      const bypassed = windowRule ? withWindowBypass(windowRule) : null;
      if (windowRule && bypassed) {
        await deps.updateRule(WINDOW_RULE, { bypassed });
        deps.log('✓ Ventana horaria de riego (R-05) salteada: la demo anda a cualquier hora');
      }
      // No se restauran: el despacho del riego revalida las guardas al abrir la válvula, después de que el motor decide.
      if (windowRule) leftActive.push('bypass de la ventana horaria de riego');

      if (conditions.clima) {
        await deps.setWeather(conditions.clima);
        deps.log(`✓ Clima simulado: UV ${conditions.clima.uvIndex} (umbral ${thresholds.uvUmbral}) → la mediasombra se protege`);
        leftActive.push('clima simulado');
      }

      // El motor decide con la PRIMERA lectura que llega tras el LEER_AHORA del pulso. Publicar una lectura antes no sirve:
      // la fijamos en la zona (para los tres pulsos) y encendemos la emisión para que alguien responda.
      const previousPin = await deps.getPinned(target.zonaId);
      const previousEmission = await deps.getEmission();
      restoreSimulation = async () => {
        await deps.pinReadings(target.zonaId, previousPin);
        if (!previousEmission) await deps.setEmission(false);
      };
      await deps.pinReadings(target.zonaId, conditions.telemetria);
      deps.log(
        `✓ ${target.zonaId} contestará los pulsos con humSus ${conditions.telemetria.humSus} %, temp ${conditions.telemetria.temp} °C (lectura fijada)`,
      );
      if (!previousEmission) {
        await deps.setEmission(true);
        deps.log('✓ Emisión automática encendida para que la zona responda los pulsos');
      }
    } else {
      await deps.clearWeather();
      await deps.setOffline(false);
      deps.log('✓ Sin clima simulado: rige el pronóstico real');
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

    for (const [index, key] of STEP_ORDER.entries()) {
      progress(key, 'running');
      await enableOnly(key);
      // El diagnóstico nace justo antes del paso 2: así es «nuevo» para SupplyRule y ningún pulso anterior lo consume.
      if (key === 'insumo' && mode === 'SIMULATOR') {
        const diagnosis = buildSimulatorPlan(thresholds).conditions.diagnostico;
        if (diagnosis) {
          const created = await deps.registerDiagnosis({ ...diagnosis, sectorId: target.sectorId, zonaId: target.zonaId });
          deps.log(`✓ Diagnóstico IA ${created.id} en ${target.sectorId}: ${diagnosis.estado}, conf. ${diagnosis.conf} %`);
        }
      }
      await runPulses(1, {
        trigger: () => deps.triggerPulse(),
        sleep: deps.sleep,
        onPulse: () => deps.log(`✓ Pulso con motor (${key}): sólo corre ${STEP_RULES[key].join(' + ')}`),
      });

      // El pulso es asíncrono (202): se espera una traza distinta de la anterior. Se compara contra la traza previa
      // y no contra el reloj, así no depende de que el simulador y el backend tengan la hora sincronizada.
      let readout: RuleReadout | null = null;
      let lastError: string | null = null;
      for (let i = 0; i < maxPolls && !readout; i++) {
        try {
          const trace = await deps.getTrace(target.sectorId);
          if (trace && instantMs(trace.ts) !== lastTs) {
            lastTs = instantMs(trace.ts);
            readout = summarizeTrace(trace)[key];
            break;
          }
        } catch (e) {
          lastError = e instanceof Error ? e.message : String(e);
        }
        await deps.sleep(pollMs);
      }
      if (!readout) {
        const warning = lastError
          ? `No se pudo leer la traza del motor (${lastError}). Si es un 403, al rol Servicio le falta \`reglas.ver\`.`
          : 'El motor no publicó una traza nueva a tiempo: no se pudo leer qué hizo cada regla.';
        if (!warnings.includes(warning)) warnings.push(warning);
      }
      steps.push({ key, readout });

      // Antes de pasar a la carga siguiente: esperar que ESTA termine y sumar la pausa. El último paso no espera.
      if (index < STEP_ORDER.length - 1) {
        if (readout?.acted && key !== 'mediasombra') {
          progress(key, 'finishing');
          const seconds = expectedSeconds(key, readout.detail);
          deps.log(`… esperando que termine ${key === 'riego' ? 'el riego' : 'la dosificación'} (hasta ~${seconds} s)`);
          const busy = (a: { valve: string; pump: string }) =>
            key === 'riego' ? a.valve === 'Regando' || a.valve === 'En cola' : a.pump === 'Dosificando';
          const { finished } = await waitUntilFinished(
            async () => {
              const actuators = await deps.getActuators(target.zonaId);
              return !actuators.some((a) => (key === 'riego' ? true : a.sectorId === target.sectorId) && busy(a));
            },
            { pollMs: finishPollMs, timeoutMs: seconds * 1000 + finishExtraMs, sleep: deps.sleep },
          );
          if (!finished) {
            const warning = `${key === 'riego' ? 'El riego' : 'La dosificación'} no terminó en el plazo esperado: se sigue con el paso siguiente.`;
            warnings.push(warning);
            deps.log(`⚠ ${warning}`);
          }
        }
        progress(key, 'pause');
        deps.log(`… pausa de ${stepWaitMs / 1000} s`);
        await deps.sleep(stepWaitMs);
      }
      progress(key, 'done');
    }
  } finally {
    // Primero las reglas, después la lectura fijada y la emisión.
    for (const [name, enabled] of previousEnabled) {
      try {
        await setEnabled(name, enabled);
      } catch (e) {
        errors.push(`${name}: ${e instanceof Error ? e.message : String(e)}`);
      }
    }
    if (restoreSimulation) {
      try {
        await restoreSimulation();
      } catch (e) {
        errors.push(e instanceof Error ? e.message : String(e));
      }
    }
    deps.log(
      errors.length === 0
        ? '✓ Reglas, lectura fijada y emisión devueltas a su estado previo'
        : `⚠ No se pudo devolver todo a su estado previo: ${errors.join('; ')}`,
    );
  }

  const [r, i, m] = steps;
  const readout = r?.readout && i?.readout && m?.readout
    ? { riego: r.readout, insumo: i.readout, mediasombra: m.readout, allActed: r.readout.acted && i.readout.acted && m.readout.acted }
    : null;
  return { steps, readout, warnings, leftActive };
}
