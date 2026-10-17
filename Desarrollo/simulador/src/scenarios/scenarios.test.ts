import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildThresholds, calculateConditions, DEFAULT_THRESHOLDS } from '../ruleConditions';
import type { ThresholdMap } from './types';
import { SCENARIOS, rulesWithoutScenario } from './index';
import type { DemoRule } from '../types';

const thresholds = buildThresholds([]);

/* ------------------------------------------------------------------
   Caracterización: la implementación anterior de calculateConditions (un switch fijo por
   regla) como oráculo. Migrar a escenarios no debe cambiar qué se publica para ninguna
   combinación que no sea un conflicto.
   ------------------------------------------------------------------ */
type LegacyKey = 'R01' | 'R02' | 'R04' | 'R03' | 'SHADING' | 'SUPPLY';

function legacy(selected: LegacyKey[], t: ThresholdMap) {
  const wantsLow = selected.includes('R02');
  const wantsHigh = selected.includes('R04');
  const telemetria: Record<string, number> = {
    humSus: 55, humAmb: 72, temp: 23, uv: 50, tempSuelo: 20, ce: 1.4, phSuelo: 5.5, n: 150, p: 45, k: 180,
  };
  let clima: Record<string, number> | null = null;
  let diagnostico: { estado: string; sev: string; confidence: string; conf: number } | null = null;
  let needsCooldownReset = false;
  if (selected.includes('R02') && !wantsHigh) {
    telemetria.humSus = Math.max(5, t.umbralCritico - 7);
    needsCooldownReset = true;
  } else if (selected.includes('R04') && !wantsLow) {
    telemetria.humSus = Math.min(98, t.saturacionBloqueo + 5);
  } else if (selected.includes('R01') || selected.includes('R03')) {
    telemetria.humSus = Math.round(((t.umbralCritico + t.umbralHumedad) / 2) * 10) / 10;
    needsCooldownReset = true;
  }
  if (selected.includes('R03')) {
    clima = {
      tempC: 18, humedadRelPct: 88, probLluviaPct: Math.min(100, t.lluviaProbabilidad + 10),
      precipMm: t.lluviaMm + 3, uvIndex: 2,
    };
  }
  if (selected.includes('SHADING')) {
    const uv = t.uvUmbral + 1;
    clima = clima ? { ...clima, uvIndex: uv } : { tempC: 32, humedadRelPct: 45, probLluviaPct: 5, precipMm: 0, uvIndex: uv };
  }
  if (selected.includes('SUPPLY')) {
    telemetria.temp = 40;
    diagnostico = { estado: 'Clorosis', sev: 'Alta', confidence: 'Alta', conf: Math.min(100, t.confianzaMinima + 5) };
  }
  return { telemetria, clima, diagnostico, needsCooldownReset };
}

const ALL: LegacyKey[] = ['R01', 'R02', 'R04', 'R03', 'SHADING', 'SUPPLY'];

function subsets<T>(items: T[]): T[][] {
  return items.reduce<T[][]>((acc, item) => acc.concat(acc.map((s) => [...s, item])), [[]]);
}

test('the migrated scenarios produce what the fixed switch produced, for every non-conflicting combination', () => {
  for (const selected of subsets(ALL)) {
    if (selected.includes('R02') && selected.includes('R04')) continue;
    const expected = legacy(selected, thresholds);
    const actual = calculateConditions(selected, thresholds);
    const label = `[${selected.join(', ')}]`;
    assert.deepEqual(actual.telemetria, expected.telemetria, `telemetry ${label}`);
    assert.deepEqual(actual.clima, expected.clima, `weather ${label}`);
    assert.deepEqual(actual.diagnostico, expected.diagnostico, `diagnosis ${label}`);
    assert.deepEqual(actual.conflictos, [], `conflicts ${label}`);
    // Only difference: R-01/R-03 with R-04 also zero the cooldowns now (harmless; see the scenarios).
    if (!(selected.includes('R04') && (selected.includes('R01') || selected.includes('R03')))) {
      assert.equal(actual.needsCooldownReset, expected.needsCooldownReset, `cooldowns ${label}`);
    }
  }
});

test('R-01 or R-03 together with R-04 also reset the cooldowns', () => {
  assert.equal(calculateConditions(['R01', 'R04'], thresholds).needsCooldownReset, true);
});

/* ------------------------------------------------------------------ registro */

test('the registry keeps the order and keys of the original catalog', () => {
  assert.deepEqual(
    SCENARIOS.map((s) => s.key),
    ['R01', 'R02', 'R04', 'R03', 'SHADING', 'SUPPLY'],
  );
});

test('every scenario is described and names the backend rules it exercises', () => {
  const keys = new Set<string>();
  for (const s of SCENARIOS) {
    assert.ok(s.label.length > 0, `${s.key} label`);
    assert.ok(s.description.length > 0, `${s.key} description`);
    assert.ok(s.branch.length > 0, `${s.key} branch`);
    assert.ok(s.backendRules.length > 0, `${s.key} backendRules`);
    assert.ok(!keys.has(s.key), `${s.key} is duplicated`);
    keys.add(s.key);
  }
});

test('only the diagnosis-driven scenario asks for a prior diagnosis', () => {
  assert.deepEqual(SCENARIOS.filter((s) => s.needsDiagnosis).map((s) => s.key), ['SUPPLY']);
});

/* ------------------------------------------------------------------ conflictos */

test('R-02 and R-04 are reported once, whatever the selection order, and nothing is applied', () => {
  for (const selected of [['R02', 'R04'], ['R04', 'R02']] as const) {
    const result = calculateConditions([...selected], thresholds);
    assert.equal(result.conflictos.length, 1);
    assert.match(result.conflictos[0], /R-02/);
    assert.match(result.conflictos[0], /R-04/);
    assert.equal(result.telemetria.humSus, 55);
    assert.equal(result.clima, null);
  }
});

test('compatible rules report no conflict', () => {
  assert.deepEqual(calculateConditions(['R02', 'R03'], thresholds).conflictos, []);
});

/* ------------------------------------------------------------------ resumen */

test('the preview does not mention the humidity that another selected rule overrode', () => {
  const resumen = calculateConditions(['R01', 'R02'], thresholds).resumen.join('\n');
  assert.match(resumen, /umbral crítico/);
  assert.doesNotMatch(resumen, /entre \d+(\.\d+)?% y/);
});

/* ------------------------------------------------------------------ valores de la v2 */

test('the confidence default is the v2 value (70), not the old 85', () => {
  assert.equal(DEFAULT_THRESHOLDS['diagnostico.confianza-minima'], 70);
  assert.equal(buildThresholds([]).confianzaMinima, 70);
});

/* ------------------------------------------------------------------ reglas sin escenario */

function demoRule(name: string): DemoRule {
  return {
    name, label: name, branch: 'RIEGO', priority: 1, enabled: true,
    cooldownKeys: [], bypassKeys: [], cooldownMinutes: {}, bypassed: [],
  };
}

test('backend rules with no scenario are the ones no scenario lists as exercised', () => {
  const rules = [demoRule('ShadingRule'), demoRule('ZoneShading'), demoRule('DeficitCriticoRule'), demoRule('ManualLockRule')];
  assert.deepEqual(
    rulesWithoutScenario(rules).map((r) => r.name),
    ['ZoneShading', 'ManualLockRule'],
  );
});

test('with every backend rule covered nothing is left without a scenario', () => {
  const covered = SCENARIOS.flatMap((s) => s.backendRules).map(demoRule);
  assert.deepEqual(rulesWithoutScenario(covered), []);
});
