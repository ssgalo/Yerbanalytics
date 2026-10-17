import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DEFAULT_THRESHOLDS, buildThresholds } from './ruleConditions';
import {
  buildSimulatorPlan,
  diagnosisPrecheck,
  STEP_WAIT_MS,
  STEP_RULES,
  hintFor,
  expectedSeconds,
  waitUntilFinished,
  loadThresholds,
  pickTarget,
  runHappyPath,
  summarizeTrace,
  withWindowBypass,
  type HappyPathDeps,
} from './happyPath';
import type { DemoRule, EvaluationTrace, SectorRef, TracedRule } from './types';

const thresholds = buildThresholds([]);

const sectors: SectorRef[] = [
  { id: 'S-201', zonaId: 'MZ-2', zonaName: 'Zona 2' },
  { id: 'S-101', zonaId: 'MZ-1', zonaName: 'Zona 1' },
  { id: 'S-102', zonaId: 'MZ-1', zonaName: 'Zona 1' },
];

/* ---- target ---- */

test('target: first macro-zone (natural order) with sectors, and its first sector', () => {
  const t = pickTarget(sectors, [], 'REAL_NODE');
  assert.deepEqual(t, { zonaId: 'MZ-1', sectorId: 'S-101', serial: null });
});

test('target in simulator mode needs a simulated sensor of that zone', () => {
  assert.equal(pickTarget(sectors, [], 'SIMULATOR'), null);
  const t = pickTarget(sectors, [{ serial: 'SIM-2', zonaId: 'MZ-2' }, { serial: 'SIM-1', zonaId: 'MZ-1' }], 'SIMULATOR');
  assert.deepEqual(t, { zonaId: 'MZ-1', sectorId: 'S-101', serial: 'SIM-1' });
});

test('target: no sectors, no target', () => {
  assert.equal(pickTarget([], [], 'REAL_NODE'), null);
});

/* ---- simulator plan ---- */

test('plan: irrigation due (not critical), critical sector, UV above threshold, fresh AI diagnosis', () => {
  const { conditions } = buildSimulatorPlan(thresholds);
  assert.equal(conditions.conflictos.length, 0);
  const hum = conditions.telemetria.humSus;
  assert.ok(hum > thresholds.umbralCritico && hum < thresholds.umbralHumedad, `humSus ${hum}`);
  assert.ok(conditions.telemetria.temp > 35);
  assert.ok(conditions.clima && conditions.clima.uvIndex >= thresholds.uvUmbral);
  assert.equal(conditions.clima?.probLluviaPct, 5);
  assert.ok(conditions.diagnostico && (conditions.diagnostico.conf ?? 0) >= thresholds.confianzaMinima);
  assert.equal(conditions.needsCooldownReset, true);
  assert.equal(conditions.pulses, 1);
});

test('plan follows the live catalog, not hardcoded values', () => {
  const custom = { ...thresholds, umbralHumedad: 60, umbralCritico: 20, uvUmbral: 9, confianzaMinima: 90 };
  const { conditions } = buildSimulatorPlan(custom);
  assert.equal(conditions.telemetria.humSus, 40);
  assert.equal(conditions.clima?.uvIndex, 10);
  assert.equal(conditions.diagnostico?.conf, 95);
});

/* ---- window bypass ---- */

const windowRule = (bypassed: string[]): DemoRule => ({
  name: 'FueraDeVentanaRiegoRule',
  label: 'R-05',
  branch: 'RIEGO',
  priority: 8,
  enabled: true,
  cooldownKeys: [],
  bypassKeys: ['window'],
  cooldownMinutes: {},
  bypassed,
});

test('withWindowBypass adds the key once and keeps the others', () => {
  assert.deepEqual(withWindowBypass(windowRule([])), ['window']);
  assert.deepEqual(withWindowBypass(windowRule(['x'])), ['x', 'window']);
  assert.equal(withWindowBypass(windowRule(['window'])), null);
});

/* ---- trace readout ---- */

const rule = (p: Partial<TracedRule> & Pick<TracedRule, 'ruleId' | 'rama'>): TracedRule => ({
  estado: 'EVALUADA',
  acciones: [],
  bloqueadaPor: null,
  error: null,
  ...p,
});
const trace = (reglas: TracedRule[], ts: string | number = '2026-10-17T10:00:00Z'): EvaluationTrace => ({
  sectorId: 'S-101',
  zonaId: 'MZ-1',
  ts,
  reglas,
});

test('readout: the three rules acted', () => {
  const r = summarizeTrace(
    trace([
      rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'Déficit: riega 6 L' }] }),
      rule({ ruleId: 'SupplyRule', rama: 'INSUMO', acciones: [{ tipo: 'ACTIVAR_BOMBA', motivo: 'Dosificación autorizada' }] }),
      rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=30] Pico UV' }] }),
    ]),
  );
  assert.equal(r.riego.acted, true);
  assert.equal(r.riego.detail, 'Déficit: riega 6 L');
  assert.equal(r.insumo.acted, true);
  assert.equal(r.mediasombra.acted, true);
  assert.match(r.mediasombra.detail, /^Mediasombra cerrada \(30 % de apertura\)/);
  assert.equal(r.allActed, true);
});

test('readout: irrigation blocked says why (abort reason wins over noop)', () => {
  const r = summarizeTrace(
    trace([
      rule({ ruleId: 'FueraDeVentanaRiegoRule', rama: 'RIEGO', acciones: [{ tipo: 'ABORT_RIEGO', motivo: 'Fuera de la ventana de riego' }] }),
      rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', estado: 'NO_ALCANZADA' }),
    ]),
  );
  assert.equal(r.riego.acted, false);
  assert.equal(r.riego.detail, 'Fuera de la ventana de riego');
  assert.equal(r.allActed, false);
});

test('readout: supply skipped for lack of a new diagnosis', () => {
  const r = summarizeTrace(trace([rule({ ruleId: 'SupplyRule', rama: 'INSUMO', estado: 'OMITIDA_SIN_DIAGNOSTICO_NUEVO' })]));
  assert.equal(r.insumo.acted, false);
  assert.match(r.insumo.detail, /diagnóstico de IA nuevo/i);
});

test('readout: missing branch and rule error are reported', () => {
  const r = summarizeTrace(trace([rule({ ruleId: 'SupplyRule', rama: 'INSUMO', estado: 'ERROR', error: 'boom' })]));
  assert.equal(r.insumo.detail, 'boom');
  assert.equal(r.riego.acted, false);
  assert.match(r.riego.detail, /no se evaluó/i);
});

test('readout: shade already protective is not an action but explains itself', () => {
  const r = summarizeTrace(
    trace([rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'NOOP_INFO', motivo: 'ya en posición protectora (30%)' }] })]),
  );
  assert.equal(r.mediasombra.acted, false);
  assert.match(r.mediasombra.detail, /protectora/);
});

test('readout: opening at or above the firmware threshold (50) is open, not closed', () => {
  const r = summarizeTrace(
    trace([rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=50] Plan' }] })]),
  );
  assert.match(r.mediasombra.detail, /^Mediasombra abierta \(50 % de apertura\)/);
});

/* ---- thresholds with a visible warning ---- */

test('loadThresholds: catalog ok, no warning', async () => {
  const out = await loadThresholds(async () => [{ clave: 'mediasombra.uv-umbral', valor: '9' } as never]);
  assert.equal(out.thresholds.uvUmbral, 9);
  assert.equal(out.warning, null);
});

test('loadThresholds: a failing catalog (403) falls back to factory values AND says so', async () => {
  const out = await loadThresholds(async () => {
    throw new Error('Error 403 al consultar los parámetros del motor.');
  });
  assert.equal(out.thresholds.uvUmbral, 7);
  assert.match(out.warning ?? '', /403/);
  assert.match(out.warning ?? '', /fábrica/);
});

/* ---- precheck + hint ---- */

test('precheck: anomalous diagnosis above the minimum confidence is usable', () => {
  assert.equal(diagnosisPrecheck({ estado: 'Clorosis', conf: 80 }, 70).ok, true);
  assert.equal(diagnosisPrecheck({ estado: 'Sano', conf: 90 }, 70).ok, false);
  assert.equal(diagnosisPrecheck({ estado: 'Clorosis', conf: 50 }, 70).ok, false);
  assert.equal(diagnosisPrecheck(null, 70).ok, false);
});

test('hint says which mode will run', () => {
  assert.match(hintFor('SIMULATOR'), /simulador/i);
  assert.match(hintFor('REAL_NODE'), /ESP32/);
});

/* ---- orchestration: three pulses, one executor at a time ---- */

const WAIT = 777; // distinct from the trace poll interval so the tests can tell them apart
const POLL = 13;

function fakeDeps(over: Partial<HappyPathDeps> = {}, enabled: Record<string, boolean> = {}) {
  const calls: string[] = [];
  const state: Record<string, boolean> = {
    RiegoPorDeficitRule: true, DeficitCriticoRule: true, SupplyRule: true, ShadingRule: true, ...enabled,
  };
  let pulses = 0;
  const deps: HappyPathDeps = {
    zeroCooldowns: async () => void calls.push('zero'),
    getRules: async () => [
      windowRule([]),
      ...Object.keys(state).map((name) => ({ ...windowRule([]), name, enabled: state[name], bypassKeys: [] })),
    ],
    updateRule: async (name, u) => {
      if (u.enabled !== undefined) {
        state[name] = u.enabled;
        calls.push(`rule:${name}:${u.enabled ? 'on' : 'off'}`);
      } else calls.push(`bypass:${name}:${u.bypassed?.join(',')}`);
    },
    setWeather: async () => void calls.push('weather'),
    clearWeather: async () => void calls.push('clearWeather'),
    setOffline: async (on) => void calls.push(`offline:${on}`),
    getEmission: async () => false,
    setEmission: async (on) => void calls.push(`emission:${on}`),
    getPinned: async () => ({}),
    pinReadings: async (zona, m) => void calls.push(`pin:${zona}:${Object.keys(m).length === 0 ? 'none' : 'reading'}`),
    registerDiagnosis: async () => (calls.push('diagnosis'), { id: 'D-1' }),
    getSectorDiagnosis: async () => ({ estado: 'Clorosis', conf: 80 }),
    triggerPulse: async () => void calls.push(`pulse${++pulses}`),
    getTrace: async () =>
      trace(
        [
          rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'Riega 720 s' }] }),
          rule({ ruleId: 'SupplyRule', rama: 'INSUMO', acciones: [{ tipo: 'ACTIVAR_BOMBA', motivo: 'Dosifica 10 ml' }] }),
          rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=30] UV' }] }),
        ],
        pulses,
      ),
    sleep: async (ms) => void (ms === WAIT && calls.push('wait')),
    log: () => {},
    getActuators: async () => [{ sectorId: 'S-101', valve: 'Cerrada', pump: 'En espera' }],
    onStep: (key, status) => void calls.push(`step:${key}:${status}`),
    ...over,
  };
  return { deps, calls, state };
}

const opts = { stepWaitMs: WAIT, tracePollMs: POLL, maxTracePolls: 3, finishPollMs: 31 };
const target = { zonaId: 'MZ-1', sectorId: 'S-101', serial: 'SIM-1' };
const real = { ...target, serial: null };
const plain = (calls: string[]) => calls.filter((c) => !c.startsWith('step:'));

test('step plan: irrigation executors, then supply, then shade; the wait is the named constant', () => {
  assert.deepEqual(STEP_RULES.riego, ['RiegoPorDeficitRule', 'DeficitCriticoRule']);
  assert.deepEqual(STEP_RULES.insumo, ['SupplyRule']);
  assert.deepEqual(STEP_RULES.mediasombra, ['ShadingRule']);
  assert.equal(STEP_WAIT_MS, 5_000);
});

test('real node: three pulses in order, one executor group enabled each time, 5 s waits between, flags restored', async () => {
  const { deps, calls, state } = fakeDeps();
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.deepEqual(plain(calls), [
    'clearWeather', 'offline:false', 'pin:MZ-1:none',
    'rule:SupplyRule:off', 'rule:ShadingRule:off', 'pulse1', 'wait',
    'rule:RiegoPorDeficitRule:off', 'rule:DeficitCriticoRule:off', 'rule:SupplyRule:on', 'pulse2', 'wait',
    'rule:SupplyRule:off', 'rule:ShadingRule:on', 'pulse3',
    // restore: everything back to its previous (all enabled) state
    'rule:RiegoPorDeficitRule:on', 'rule:DeficitCriticoRule:on', 'rule:SupplyRule:on',
  ]);
  assert.deepEqual(Object.values(state), [true, true, true, true]);
  assert.equal(out.steps.length, 3);
  assert.ok(out.steps.every((s) => s.readout !== null));
  assert.equal(out.readout?.allActed, true);
});

test('previously disabled rules go back to disabled, not enabled', async () => {
  const { deps, state } = fakeDeps({}, { ShadingRule: false, SupplyRule: false });
  await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(state.ShadingRule, false);
  assert.equal(state.SupplyRule, false);
  assert.equal(state.RiegoPorDeficitRule, true);
});

test('flags are restored even if a pulse fails', async () => {
  const { deps, state } = fakeDeps({ triggerPulse: async () => { throw new Error('boom'); } });
  await assert.rejects(runHappyPath('REAL_NODE', real, thresholds, deps, opts), /boom/);
  assert.deepEqual(Object.values(state), [true, true, true, true]);
});

test('simulator: setup first, reading pinned for all three pulses, diagnosis right before step 2, cleanup at the end', async () => {
  const { deps, calls } = fakeDeps();
  const out = await runHappyPath('SIMULATOR', target, thresholds, deps, opts);
  const c = plain(calls);
  assert.deepEqual(c.slice(0, 5), ['zero', 'bypass:FueraDeVentanaRiegoRule:window', 'weather', 'pin:MZ-1:reading', 'emission:true']);
  assert.equal(c.filter((x) => x === 'diagnosis').length, 1);
  assert.ok(c.indexOf('pulse1') < c.indexOf('diagnosis'));
  assert.ok(c.indexOf('diagnosis') < c.indexOf('pulse2'));
  assert.equal(c.filter((x) => x.startsWith('pin:MZ-1:reading')).length, 1);
  assert.deepEqual(c.slice(-2), ['pin:MZ-1:none', 'emission:false']);
  assert.ok(out.leftActive.length > 0);
});

test('simulator: an error still releases the pin, emission and flags', async () => {
  const { deps, calls, state } = fakeDeps({ registerDiagnosis: async () => { throw new Error('sin captura'); } });
  await assert.rejects(runHappyPath('SIMULATOR', target, thresholds, deps, opts), /sin captura/);
  assert.deepEqual(plain(calls).slice(-2), ['pin:MZ-1:none', 'emission:false']);
  assert.deepEqual(Object.values(state), [true, true, true, true]);
});

test('simulator: emission already on stays on; a previous pin is restored', async () => {
  const pins: Record<string, number>[] = [];
  const { deps, calls } = fakeDeps({
    getEmission: async () => true,
    getPinned: async () => ({ humSus: 10 }),
    pinReadings: async (_z, m) => void pins.push(m),
  });
  await runHappyPath('SIMULATOR', target, thresholds, deps, opts);
  assert.ok(!plain(calls).some((c) => c.startsWith('emission:')));
  assert.deepEqual(pins[pins.length - 1], { humSus: 10 });
});

test('progress: en curso → esperando que termine → pausa 5 s → hecho; the last step has no wait', async () => {
  const { deps, calls } = fakeDeps();
  await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  const steps = calls.filter((c) => c.startsWith('step:'));
  assert.deepEqual(steps, [
    'step:riego:running', 'step:riego:finishing', 'step:riego:pause', 'step:riego:done',
    'step:insumo:running', 'step:insumo:finishing', 'step:insumo:pause', 'step:insumo:done',
    'step:mediasombra:running', 'step:mediasombra:done',
  ]);
});

test('each step reads its own branch from the trace of ITS pulse', async () => {
  let pulses = 0;
  const { deps } = fakeDeps({
    triggerPulse: async () => void pulses++,
    getTrace: async () =>
      trace(
        pulses === 1
          ? [rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'ok' }] }),
             rule({ ruleId: 'SupplyRule', rama: 'INSUMO', estado: 'OMITIDA_DESHABILITADA' })]
          : [rule({ ruleId: 'SupplyRule', rama: 'INSUMO', acciones: [{ tipo: 'ACTIVAR_BOMBA', motivo: 'bomba' }] }),
             rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=30] UV' }] })],
        pulses,
      ),
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(out.steps[0].readout?.acted, true);
  assert.equal(out.steps[1].readout?.acted, true);
  assert.equal(out.steps[2].readout?.acted, true);
});

test('real node warns when the sector has no usable AI diagnosis, and still runs the steps', async () => {
  const { deps, calls } = fakeDeps({ getSectorDiagnosis: async () => null });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(out.warnings.length, 1);
  assert.ok(plain(calls).includes('pulse3'));
});

test('a pulse in course (409) is waited out', async () => {
  let n = 0;
  const { deps, calls } = fakeDeps({
    triggerPulse: async () => {
      if (++n % 2 === 1) throw new Error('409 Ya hay una lectura en curso');
      calls.push(`pulse${n}`);
    },
    getTrace: async () => trace([], n),
  });
  await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.ok(n >= 6);
});

test('trace read failures (403) are counted as failed polls and named in the final message', async () => {
  let reads = 0;
  const { deps } = fakeDeps({
    getTrace: async () => {
      if (++reads === 1) return null;
      throw new Error('Error 403 al consultar la traza del motor.');
    },
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(out.readout, null);
  assert.match(out.warnings.join(' '), /403/);
  assert.match(out.warnings.join(' '), /reglas\.ver/);
});

test('simulator mode without a sensor serial is an error', async () => {
  const { deps } = fakeDeps();
  await assert.rejects(runHappyPath('SIMULATOR', { ...target, serial: null }, thresholds, deps, opts), /sensor/i);
});

/* ---- waiting for the action to FINISH ---- */

test('expectedSeconds: irrigation seconds / dose ml x 1 s from the motive, else the firmware maximum', () => {
  assert.equal(expectedSeconds('riego', 'Riega 6 L en 720 s'), 720);
  assert.equal(expectedSeconds('riego', 'sin dato'), 1200);
  assert.equal(expectedSeconds('insumo', 'Dosifica 10 ml'), 10);
  assert.equal(expectedSeconds('insumo', 'sin dato'), 60);
});

test('waitUntilFinished: polls a fake nursery Regando → Regando → Cerrada, then stops', async () => {
  const seq = [false, false, true];
  let n = 0;
  const slept: number[] = [];
  const out = await waitUntilFinished(async () => seq[n++], { pollMs: 50, timeoutMs: 10_000, sleep: async (ms) => void slept.push(ms) });
  assert.equal(out.finished, true);
  assert.deepEqual(slept, [50, 50]);
});

test('waitUntilFinished: gives up after the timeout and says it did not finish', async () => {
  let polls = 0;
  const out = await waitUntilFinished(async () => (polls++, false), { pollMs: 50, timeoutMs: 200, sleep: async () => {} });
  assert.equal(out.finished, false);
  assert.ok(polls >= 4 && polls <= 6);
});

function nurserySequence(field: 'valve' | 'pump', busy: string, idle: string, busyPolls: number) {
  let n = 0;
  return async () => [{ sectorId: 'S-101', valve: 'Cerrada', pump: 'En espera', [field]: n++ < busyPolls ? busy : idle }];
}

test('step 1 waits while the zone valves are Regando/En cola, then pauses 5 s', async () => {
  const { deps, calls } = fakeDeps({ getActuators: nurserySequence('valve', 'Regando', 'Cerrada', 3) });
  await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  const c = calls;
  assert.ok(c.indexOf('step:riego:finishing') < c.indexOf('step:riego:pause'));
  assert.ok(c.indexOf('step:riego:pause') < c.indexOf('wait'));
});

test('step 2 waits while the pump is Dosificando', async () => {
  let polls = 0;
  const { deps } = fakeDeps({
    getActuators: async () => [{ sectorId: 'S-101', valve: 'Cerrada', pump: polls++ < 3 ? 'Dosificando' : 'En espera' }],
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.ok(polls >= 4);
  assert.equal(out.warnings.length, 0);
});

test('a step that did not act skips the wait-for-finish and only does the 5 s pause', async () => {
  let asked = 0;
  const { deps, calls } = fakeDeps({
    getActuators: async () => (asked++, [{ sectorId: 'S-101', valve: 'Regando', pump: 'Dosificando' }]),
    getTrace: async () => trace([rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'NOOP_INFO', motivo: 'sin déficit' }] })], calls.filter((x) => x.startsWith('pulse')).length),
  });
  await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(asked, 0);
  assert.ok(calls.includes('step:riego:pause'));
  assert.ok(!calls.includes('step:riego:finishing'));
});

test('timeout: continues with a visible warning instead of hanging', async () => {
  const { deps, calls } = fakeDeps({
    getActuators: async () => [{ sectorId: 'S-101', valve: 'Regando', pump: 'En espera' }],
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, { ...opts, finishTimeoutExtraMs: 100, finishPollMs: 60 });
  assert.ok(out.warnings.some((w) => /no terminó/i.test(w)));
  assert.ok(calls.includes('pulse3'));
});

test('an error while waiting still restores the flags', async () => {
  const { deps, state } = fakeDeps({ getActuators: async () => { throw new Error('nursery caído'); } });
  await assert.rejects(runHappyPath('REAL_NODE', real, thresholds, deps, opts), /nursery caído/);
  assert.deepEqual(Object.values(state), [true, true, true, true]);
});

test('defaults stay in sync', () => {
  assert.equal(DEFAULT_THRESHOLDS['riego.umbral-humedad'], 45);
});
