import assert from 'node:assert/strict';
import { test } from 'node:test';
import { DEFAULT_THRESHOLDS, buildThresholds } from './ruleConditions';
import {
  buildSimulatorPlan,
  diagnosisPrecheck,
  hintFor,
  initialProgress,
  loadThresholds,
  nextProgress,
  progressDone,
  readProgress,
  pickTarget,
  runHappyPath,
  summarizeTrace,
  withWindowBypass,
  type ActionProgress,
  type HappyPathDeps,
  withBypasses,
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
  assert.equal(conditions.telemetria.humAmb, 98);
  assert.ok(conditions.clima && conditions.clima.uvIndex >= thresholds.uvUmbral);
  assert.equal(conditions.clima?.probLluviaPct, 0, 'P-D4: el modificador de aplicación deja el día sin lluvia');
  assert.ok(conditions.telemetria.temp <= 30, 'P-D3: con calor no se aplica un insumo');
  assert.equal(conditions.ensureTankStock, true);
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

const rule_ = (name: string, enabled: boolean, bypassed: string[]): DemoRule => ({
  name,
  label: name,
  branch: 'INSUMO',
  priority: 12,
  enabled,
  cooldownKeys: [],
  bypassKeys: ['window', 'daily'],
  cooldownMinutes: {},
  bypassed,
});

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

test('withBypasses adds only the keys that are missing, once, and keeps the others', () => {
  assert.deepEqual(withBypasses(rule_('P', true, []), ['window', 'daily']), ['window', 'daily']);
  assert.deepEqual(withBypasses(rule_('P', true, ['x', 'window']), ['window', 'daily']), ['x', 'window', 'daily']);
  assert.equal(withBypasses(rule_('P', true, ['daily', 'window']), ['window', 'daily']), null);
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
      rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=0] Mediasombra cerrada por pico de radiación UV (índice 9.5 ≥ umbral 7.0).' }] }),
    ]),
  );
  assert.equal(r.riego.acted, true);
  assert.equal(r.riego.detail, 'Déficit: riega 6 L');
  assert.equal(r.insumo.acted, true);
  assert.equal(r.mediasombra.acted, true);
  assert.match(r.mediasombra.detail, /^Mediasombra cerrada por pico de radiación UV/);
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
    trace([rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=100] Plan de rustificación: día 3 — mediasombra abierta.' }] })]),
  );
  assert.match(r.mediasombra.detail, /^Mediasombra abierta · Plan de rustificación/);
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

/* ---- orchestration: ONE pulse with the three rules as they are (the node queues the loads) ---- */

const POLL = 13;

function fakeDeps(over: Partial<HappyPathDeps> = {}) {
  const calls: string[] = [];
  let pulses = 0;
  const deps: HappyPathDeps = {
    zeroCooldowns: async () => void calls.push('zero'),
    getRules: async () => [windowRule([]), rule_('SupplyRule', false, []), rule_('ApplicationPreconditions', true, [])],
    updateRule: async (name, u) => void calls.push(`update:${name}:${u.enabled !== undefined ? 'enabled' : `bypass=${u.bypassed?.join(',')}`}`),
    ensureTankStock: async () => (calls.push('stock'), []),
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
          rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=0] UV' }] }),
        ],
        pulses,
      ),
    sleep: async () => {},
    log: () => {},
    ...over,
  };
  return { deps, calls };
}

const opts = { tracePollMs: POLL, maxTracePolls: 3 };
const target = { zonaId: 'MZ-1', sectorId: 'S-101', serial: 'SIM-1' };
const real = { ...target, serial: null };

test('real node: exactly ONE pulse, no rule toggled, nothing waited for', async () => {
  const sleeps: number[] = [];
  const { deps, calls } = fakeDeps({ sleep: async (ms) => void sleeps.push(ms) });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.deepEqual(calls, ['clearWeather', 'offline:false', 'pin:MZ-1:none', 'pulse1']);
  assert.ok(!calls.some((c) => c.endsWith(':enabled')), 'no rule is switched on or off');
  assert.ok(!calls.includes('stock'), 'the real tanks are not touched');
  assert.deepEqual(sleeps, [], 'no pause between steps, no wait for an action to finish');
  assert.equal(out.readout?.allActed, true);
});

test('simulator: setup, diagnosis BEFORE the single pulse, cleanup at the end', async () => {
  const { deps, calls } = fakeDeps();
  const out = await runHappyPath('SIMULATOR', target, thresholds, deps, opts);
  assert.deepEqual(calls, [
    'zero', 'update:FueraDeVentanaRiegoRule:bypass=window',
    // La dosificación arranca retirada: se enciende, se saltea la ventana y el límite diario, y se asegura el stock.
    'update:SupplyRule:enabled', 'update:ApplicationPreconditions:bypass=window,daily', 'stock',
    'weather', 'pin:MZ-1:reading', 'emission:true',
    'diagnosis', 'pulse1',
    'pin:MZ-1:none', 'emission:false',
  ]);
  assert.ok(out.leftActive.length > 0);
  assert.equal(out.readout?.allActed, true);
});

test('real node warns when the supply rule is still retired, because the input would never act', async () => {
  const { deps, calls } = fakeDeps();
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.ok(out.warnings.some((w) => /SupplyRule/.test(w)));
  assert.ok(!calls.some((c) => c.startsWith('update:')), 'it only warns, it does not switch anything');
});

test('real node does not warn when the supply rule is already on', async () => {
  const { deps } = fakeDeps({ getRules: async () => [rule_('SupplyRule', true, [])] });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.ok(!out.warnings.some((w) => /SupplyRule/.test(w)));
});

test('simulator: a supply rule already on and the bypasses already set are not touched again', async () => {
  const { deps, calls } = fakeDeps({
    getRules: async () => [
      windowRule(['window']), rule_('SupplyRule', true, []), rule_('ApplicationPreconditions', true, ['window', 'daily']),
    ],
  });
  await runHappyPath('SIMULATOR', target, thresholds, deps, opts);
  assert.ok(!calls.some((c) => c.startsWith('update:')));
});

test('simulator: an error still releases the pin and the emission', async () => {
  const { deps, calls } = fakeDeps({ registerDiagnosis: async () => { throw new Error('sin captura'); } });
  await assert.rejects(runHappyPath('SIMULATOR', target, thresholds, deps, opts), /sin captura/);
  assert.deepEqual(calls.slice(-2), ['pin:MZ-1:none', 'emission:false']);
  assert.ok(!calls.includes('pulse1'));
});

test('simulator: emission already on stays on; a previous pin is restored', async () => {
  const pins: Record<string, number>[] = [];
  const { deps, calls } = fakeDeps({
    getEmission: async () => true,
    getPinned: async () => ({ humSus: 10 }),
    pinReadings: async (_z, m) => void pins.push(m),
  });
  await runHappyPath('SIMULATOR', target, thresholds, deps, opts);
  assert.ok(!calls.some((c) => c.startsWith('emission:')));
  assert.deepEqual(pins[pins.length - 1], { humSus: 10 });
});

test('the readout comes from the trace of THE pulse (a trace older than the pulse is not it)', async () => {
  let pulses = 0;
  let reads = 0;
  const { deps } = fakeDeps({
    triggerPulse: async () => void pulses++,
    getTrace: async () => {
      reads++;
      // before the pulse (and the first poll after it) the trace is the old one; then the new one arrives
      const fresh = pulses > 0 && reads > 2;
      return trace(
        fresh
          ? [rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'nuevo' }] })]
          : [],
        fresh ? 2 : 1,
      );
    },
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, { tracePollMs: POLL, maxTracePolls: 5 });
  assert.equal(out.readout?.riego.detail, 'nuevo');
});

test('real node warns when the sector has no usable AI diagnosis, and still runs the pulse', async () => {
  const { deps, calls } = fakeDeps({
    getSectorDiagnosis: async () => null,
    getRules: async () => [rule_('SupplyRule', true, [])],
  });
  const out = await runHappyPath('REAL_NODE', real, thresholds, deps, opts);
  assert.equal(out.warnings.length, 1);
  assert.ok(calls.includes('pulse1'));
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
  assert.ok(n >= 2);
  assert.ok(calls.includes('pulse2'));
});

test('trace read failures (403) are named in the final message', async () => {
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

/* ---- progress of each action, read from the nursery snapshot ---- */

const allActed = summarizeTrace(
  trace([
    rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'Riega 720 s' }] }),
    rule({ ruleId: 'SupplyRule', rama: 'INSUMO', acciones: [{ tipo: 'ACTIVAR_BOMBA', motivo: 'Dosifica 10 ml' }] }),
    rule({ ruleId: 'ShadingRule', rama: 'MEDIASOMBRA', acciones: [{ tipo: 'MOVER_MEDIASOMBRA', motivo: '[apertura=0] UV' }] }),
  ]),
);

const snap = (valve: string, pump: string) => ({ sectorId: 'S-101', valve, pump });

test('progress starts as ordered for what acted and "sin acción" for what did not', () => {
  assert.deepEqual(initialProgress(allActed), { riego: 'ordenada', insumo: 'ordenada', mediasombra: 'ordenada' });
  const partial = summarizeTrace(trace([rule({ ruleId: 'RiegoPorDeficitRule', rama: 'RIEGO', acciones: [{ tipo: 'ACTIVAR_VALVULA', motivo: 'x' }] })]));
  assert.deepEqual(initialProgress(partial), { riego: 'ordenada', insumo: 'sin acción', mediasombra: 'sin acción' });
  assert.deepEqual(initialProgress(null), { riego: 'sin acción', insumo: 'sin acción', mediasombra: 'sin acción' });
});

test('progress: valve Regando / pump En cola → riego en curso, insumo en cola; shade stays ordered', () => {
  const p = nextProgress(initialProgress(allActed), snap('Regando', 'En cola'));
  assert.deepEqual(p, { riego: 'en curso', insumo: 'en cola', mediasombra: 'ordenada' });
});

test('progress walks en cola → en curso → hecho as the node serializes the loads', () => {
  const steps: [string, string, ActionProgress][] = [
    ['Regando', 'En cola', { riego: 'en curso', insumo: 'en cola', mediasombra: 'ordenada' }],
    ['Cerrada', 'Dosificando', { riego: 'hecho', insumo: 'en curso', mediasombra: 'ordenada' }],
    ['Cerrada', 'En espera', { riego: 'hecho', insumo: 'hecho', mediasombra: 'ordenada' }],
  ];
  let p = initialProgress(allActed);
  for (const [valve, pump, expected] of steps) {
    p = nextProgress(p, snap(valve, pump));
    assert.deepEqual(p, expected);
  }
  assert.equal(progressDone(p), true);
});

test('progress: a closed valve that never showed up is not "hecho" yet (the order may still be on its way)', () => {
  const p = nextProgress(initialProgress(allActed), snap('Cerrada', 'En espera'));
  assert.deepEqual(p, { riego: 'ordenada', insumo: 'ordenada', mediasombra: 'ordenada' });
  assert.equal(progressDone(p), false);
});

test('progress: a queued action that leaves the queue without running is closed, not stuck', () => {
  let p = nextProgress(initialProgress(allActed), snap('En cola', 'En espera'));
  assert.equal(p.riego, 'en cola');
  p = nextProgress(p, snap('Cerrada', 'En espera'));
  assert.equal(p.riego, 'hecho');
});

test('progress: what did not act is never reported as moving', () => {
  const noRiego = summarizeTrace(trace([rule({ ruleId: 'SupplyRule', rama: 'INSUMO', acciones: [{ tipo: 'ACTIVAR_BOMBA', motivo: 'x' }] })]));
  const p = nextProgress(initialProgress(noRiego), snap('Regando', 'Dosificando'));
  assert.equal(p.riego, 'sin acción');
  assert.equal(p.insumo, 'en curso');
});

test('progress ignores a snapshot without the sector, and readProgress survives a failing snapshot', async () => {
  const start = initialProgress(allActed);
  assert.equal(nextProgress(start, undefined), start);
  assert.deepEqual(await readProgress(start, 'S-101', async () => { throw new Error('boom'); }), start);
  const read = await readProgress(start, 'S-101', async () => [snap('Regando', 'En cola'), { sectorId: 'S-102', valve: 'Cerrada', pump: 'En espera' }]);
  assert.equal(read.riego, 'en curso');
});

test('defaults stay in sync', () => {
  assert.equal(DEFAULT_THRESHOLDS['riego.umbral-humedad'], 45);
});
