import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildThresholds, calculateConditions } from '../ruleConditions';
import type { DemoRule } from '../types';
import { SCENARIOS, rulesWithoutScenario } from './index';

const thresholds = buildThresholds([]);

test('E-01 · riego sin efecto: la humedad queda fijada en el valor del riego, así la verificación no ve subida', () => {
  const r = calculateConditions(['IRRIGATION_NO_EFFECT'], thresholds);

  // Entre el umbral crítico (35) y el de riego (45): riega R-01 y la verificación lee lo mismo (delta 0 < 8).
  assert.equal(r.pinnedMetrics.humSus, 40);
  assert.equal(r.telemetria.humSus, 40);
  assert.equal(r.needsCooldownReset, true, 'los cooldowns en 0 incluyen la espera de la verificación');
  assert.equal(r.diagnostico, null);
  assert.ok(r.resumen.some((line) => line.includes('verificación')));
});

test('E-01 · riego sin efecto: R-01 y S-06 quedan como las reglas que ejercita', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'IRRIGATION_NO_EFFECT')!;

  assert.deepEqual(scenario.backendRules, ['RiegoPorDeficitRule', 'IneffectiveActionRule', 'EffectivenessService']);
  assert.equal(scenario.branch, 'RIEGO');
});

test('E-02 · tratamiento sin efecto: una clorosis de severidad Alta que el fertilizante no mejora', () => {
  const r = calculateConditions(['TREATMENT_NO_EFFECT'], thresholds);

  assert.equal(r.diagnostico?.estado, 'Clorosis');
  assert.equal(r.diagnostico?.sev, 'Alta', 'la severidad más alta: no puede bajar un nivel');
  assert.deepEqual(r.pinnedMetrics, { humSus: 60, ce: 0.8, phSuelo: 5.5 });
  assert.equal(r.ensureTankStock, true);
  assert.equal(r.needsCooldownReset, true);
  assert.deepEqual(r.bypassRules, { ApplicationPreconditions: ['window'] });
});

test('el tratamiento sin efecto no convive con otro escenario que registre su propio diagnóstico', () => {
  const r = calculateConditions(['TREATMENT_NO_EFFECT', 'CHLOROSIS_NUTRITIONAL'], thresholds);

  assert.equal(r.conflictos.length, 1);
});

test('el servicio de efectividad y la regla S-06 tienen escenario', () => {
  const rules = ['EffectivenessService', 'IneffectiveActionRule'].map(
    (name) => ({ name, label: name, branch: 'RIEGO', priority: 1, enabled: true, cooldownKeys: [], bypassKeys: [] }) as unknown as DemoRule,
  );

  assert.deepEqual(rulesWithoutScenario(rules), []);
});
