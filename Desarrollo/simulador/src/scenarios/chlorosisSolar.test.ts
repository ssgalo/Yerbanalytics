import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildThresholds, calculateConditions, DEFAULT_THRESHOLDS } from '../ruleConditions';
import type { DemoRule } from '../types';
import { SCENARIOS, rulesWithoutScenario } from './index';

const thresholds = buildThresholds([]);

/* ------------------------------------------------------------------ clorosis (F-C1, F-C2) */

test('F-C2: a chlorosis diagnosis over normal substrate, EC and pH, pinned on the zone readings, with tank stock', () => {
  const r = calculateConditions(['CHLOROSIS_NUTRITIONAL'], thresholds);

  assert.equal(r.diagnostico?.estado, 'Clorosis');
  assert.equal(r.diagnostico?.sev, 'Media');
  assert.ok((r.diagnostico?.conf ?? 0) > thresholds.confianzaMinima);
  assert.deepEqual(r.pinnedMetrics, { humSus: 60, ce: 0.8, phSuelo: 5.5 });
  assert.equal(r.ensureTankStock, true);
  assert.equal(r.needsCooldownReset, true);
  assert.deepEqual(r.bypassRules, {});
});

test('F-C2: the pinned readings clear every cause of F-C1 whatever the catalog says within its ranges', () => {
  const r = calculateConditions(['CHLOROSIS_NUTRITIONAL'], thresholds);

  assert.ok(r.pinnedMetrics.humSus <= 70, 'under the lowest waterlogging threshold (70 %)');
  assert.ok(r.pinnedMetrics.ce < 1.5, 'under the lowest salinity threshold (1,5 dS/m)');
  assert.ok(r.pinnedMetrics.phSuelo < 6.0, 'under the lowest iron-chlorosis threshold (6,0)');
});

test('F-C1: the same diagnosis over a high pH (6,8) and no tank stock to load', () => {
  const r = calculateConditions(['CHLOROSIS_NON_NUTRITIONAL'], thresholds);

  assert.equal(r.diagnostico?.estado, 'Clorosis');
  assert.equal(r.pinnedMetrics.phSuelo, 6.8);
  assert.equal(r.ensureTankStock, false);
  assert.ok(r.pinnedMetrics.humSus <= 70 && r.pinnedMetrics.ce < 1.5, 'the pH is the only cause');
});

test('F-C1 follows the iron-chlorosis threshold of the catalog and falls back to the v2 value', () => {
  assert.equal(DEFAULT_THRESHOLDS['clorosis.ph-ferrica'], 6.5);
  assert.equal(buildThresholds([]).phFerrica, 6.5);
  assert.equal(calculateConditions(['CHLOROSIS_NON_NUTRITIONAL'], { ...thresholds, phFerrica: 7 }).pinnedMetrics.phSuelo, 7.3);
});

/* ------------------------------------------------------------------ estrés solar (F-S1, F-S3) */

test('F-S1: high light and a substrate under the replenishment level, with the window and samples bypasses', () => {
  const r = calculateConditions(['SOLAR_STRESS'], thresholds);

  assert.equal(r.diagnostico?.estado, 'Estrés solar');
  assert.equal(r.pinnedMetrics.uv, 88);
  assert.equal(r.pinnedMetrics.humSus, 50);
  assert.deepEqual(r.bypassRules, { SolarStressRule: ['window', 'samples'] });
  assert.equal(r.needsCooldownReset, true);
});

test('F-S1: the substrate is between the irrigation threshold and the replenishment level, so only F-S1 waters', () => {
  const r = calculateConditions(['SOLAR_STRESS'], thresholds);

  assert.ok(r.pinnedMetrics.humSus < thresholds.humedadReposicion);
  assert.ok(r.pinnedMetrics.humSus >= thresholds.umbralHumedad);
  assert.ok(r.pinnedMetrics.uv >= thresholds.luzAlta);
});

test('F-S1 follows the catalog thresholds of the light and the replenishment level', () => {
  const r = calculateConditions(['SOLAR_STRESS'], { ...thresholds, luzAlta: 95, humedadReposicion: 65 });

  assert.equal(r.pinnedMetrics.uv, 100, 'capped at 100 %');
  assert.equal(r.pinnedMetrics.humSus, 60);
});

test('F-S3: low light and a mild temperature (40 %, 24 °C) with the samples bypass', () => {
  const r = calculateConditions(['SOLAR_STRESS_UNEXPLAINED'], thresholds);

  assert.equal(r.diagnostico?.estado, 'Estrés solar');
  assert.deepEqual(r.pinnedMetrics, { uv: 40, temp: 24 });
  assert.deepEqual(r.bypassRules, { SolarStressUnexplainedRule: ['samples'] });
  assert.equal(r.ensureTankStock, false);
});

test('F-S3 stays under the thresholds even when the agronomist moves them', () => {
  for (const [luzBaja, tempBaja] of [[40, 25], [60, 30], [80, 32]]) {
    const r = calculateConditions(['SOLAR_STRESS_UNEXPLAINED'], { ...thresholds, luzBaja, tempBaja });

    assert.ok(r.pinnedMetrics.uv < luzBaja, `uv ${r.pinnedMetrics.uv} < ${luzBaja}`);
    assert.ok(r.pinnedMetrics.temp < tempBaja, `temp ${r.pinnedMetrics.temp} < ${tempBaja}`);
  }
});

test('the solar stress thresholds come from the catalog and fall back to the v2 values', () => {
  const t = buildThresholds([]);

  assert.deepEqual([t.luzAlta, t.humedadReposicion, t.luzBaja, t.tempBaja], [80, 55, 60, 30]);
  const fromCatalog = buildThresholds([
    { clave: 'estres.luz-alta', valor: '90' } as unknown as Parameters<typeof buildThresholds>[0][number],
  ]);
  assert.equal(fromCatalog.luzAlta, 90);
});

/* ------------------------------------------------------------------ conflictos y registro */

test('the chlorosis and solar-stress scenarios conflict with a silent node and a lock', () => {
  for (const key of ['CHLOROSIS_NUTRITIONAL', 'CHLOROSIS_NON_NUTRITIONAL', 'SOLAR_STRESS', 'SOLAR_STRESS_UNEXPLAINED']) {
    for (const other of ['NODE_OFFLINE', 'LOCK']) {
      for (const selected of [[key, other], [other, key]]) {
        assert.equal(calculateConditions(selected, thresholds).conflictos.length, 1, selected.join('+'));
      }
    }
  }
});

test('F-S1 wants the substrate just under the replenishment level and conflicts with the scenarios that set it otherwise', () => {
  for (const other of ['R01', 'R02', 'R04', 'FUNGAL_CONDITION']) {
    assert.equal(calculateConditions(['SOLAR_STRESS', other], thresholds).conflictos.length, 1, other);
  }
});

test('each chlorosis and solar-stress scenario exercises its own rule on the right branch and needs a diagnosis', () => {
  const byKey = (key: string) => SCENARIOS.find((s) => s.key === key)!;

  assert.deepEqual(byKey('CHLOROSIS_NUTRITIONAL').backendRules, ['ChlorosisNutritionalRule']);
  assert.deepEqual(byKey('CHLOROSIS_NON_NUTRITIONAL').backendRules, ['ChlorosisNonNutritionalRule']);
  assert.deepEqual(byKey('SOLAR_STRESS').backendRules, ['SolarStressRule']);
  assert.deepEqual(byKey('SOLAR_STRESS_UNEXPLAINED').backendRules, ['SolarStressUnexplainedRule']);
  assert.equal(byKey('SOLAR_STRESS').branch, 'RIEGO');
  assert.equal(byKey('SOLAR_STRESS_UNEXPLAINED').branch, 'SEGUIMIENTO');
  for (const key of ['CHLOROSIS_NUTRITIONAL', 'CHLOROSIS_NON_NUTRITIONAL', 'SOLAR_STRESS', 'SOLAR_STRESS_UNEXPLAINED']) {
    assert.equal(byKey(key).needsDiagnosis, true, key);
  }
});

test('with these scenarios every rule of the chlorosis and solar-stress change is known to the simulator', () => {
  const demoRules = ['ChlorosisNonNutritionalRule', 'ChlorosisNutritionalRule', 'SolarStressRule', 'SolarStressUnexplainedRule'].map(
    (name) => ({ name }) as unknown as DemoRule,
  );

  assert.deepEqual(rulesWithoutScenario(demoRules), []);
});
