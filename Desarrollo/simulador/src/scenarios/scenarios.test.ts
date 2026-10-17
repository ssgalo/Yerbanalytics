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
    [
      'R01', 'R02', 'R04', 'R03', 'SHADING', 'SUPPLY', 'NODE_OFFLINE', 'LOCK', 'INVALID_READING', 'INCOMPLETE_HARDWARE',
      'FAULT_NO_FLOW', 'FAULT_OVERFLOW', 'FAULT_SHADE_LIMIT', 'REPAIR',
    ],
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

/* ------------------------------------------------------------------ nodo sin respuesta (S-02) */

test('the node-offline scenario silences the zone and asks for the three pulses that declare it down', () => {
  const result = calculateConditions(['NODE_OFFLINE'], thresholds);
  assert.equal(result.silenceZone, true);
  assert.equal(result.pulses, 3);
  assert.equal(result.needsCooldownReset, false);
  assert.equal(result.diagnostico, null);
  assert.equal(result.clima, null);
  assert.match(result.resumen.join(' | '), /deja de responder/);
});

test('without it nothing is silenced and a single pulse is asked for', () => {
  const result = calculateConditions(['R01'], thresholds);
  assert.equal(result.silenceZone, false);
  assert.equal(result.pulses, 1);
});

test('a silent node cannot make any other rule fire: every other scenario conflicts with it', () => {
  for (const other of SCENARIOS.filter((s) => s.key !== 'NODE_OFFLINE')) {
    for (const selected of [[other.key, 'NODE_OFFLINE'], ['NODE_OFFLINE', other.key]]) {
      const result = calculateConditions(selected, thresholds);
      assert.equal(result.conflictos.length, 1, `${selected.join('+')}`);
      assert.equal(result.silenceZone, false, 'with a conflict nothing is applied');
    }
  }
});

test('the node-offline scenario exercises the escalation piece of the backend', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'NODE_OFFLINE');
  assert.deepEqual(scenario?.backendRules, ['EscalamientoNodo']);
});

/* ------------------------------------------------------------------ bloqueo manual (S-01) */

test('the manual-lock scenario asks for a lock on the target zone and publishes dry soil', () => {
  const result = calculateConditions(['LOCK'], thresholds);
  assert.deepEqual(result.manualLock, { reason: 'Bloqueo de prueba desde el simulador' });
  // Dry enough for R-01 to irrigate if nothing stopped it: the lock is the only reason it does not.
  assert.ok(result.telemetria.humSus < thresholds.umbralHumedad);
  assert.ok(result.telemetria.humSus > thresholds.umbralCritico);
  assert.equal(result.needsCooldownReset, true);
  assert.equal(result.silenceZone, false);
  assert.equal(result.pulses, 1);
  assert.match(result.resumen.join(' | '), /bloqueo/i);
});

test('without it no lock is requested', () => {
  assert.equal(calculateConditions(['R01'], thresholds).manualLock, null);
});

test('the lock scenario exercises ManualLockRule and nothing else', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'LOCK');
  assert.deepEqual(scenario?.backendRules, ['ManualLockRule']);
  assert.equal(scenario?.needsDiagnosis, false);
});

test('a lock cannot be shown next to saturated soil or a silent node: the cause would be ambiguous', () => {
  for (const other of ['R04', 'NODE_OFFLINE']) {
    for (const selected of [[other, 'LOCK'], ['LOCK', other]]) {
      const result = calculateConditions(selected, thresholds);
      assert.equal(result.conflictos.length, 1, selected.join('+'));
      assert.equal(result.manualLock, null, 'with a conflict nothing is applied');
    }
  }
});

test('the lock scenario coexists with the dry-soil rules', () => {
  for (const other of ['R01', 'R02']) {
    assert.equal(calculateConditions([other, 'LOCK'], thresholds).conflictos.length, 0, other);
  }
});

/* ------------------------------------------------------------------ lectura inválida (S-03) */

test('the invalid-reading scenario sticks the soil humidity above the physical limit, without publishing it', () => {
  const result = calculateConditions(['INVALID_READING'], thresholds);
  // 112 % with the factory limit of 100 %: the value of the v2 example.
  assert.deepEqual(result.pinnedMetrics, { humSus: 112 });
  assert.ok(result.pinnedMetrics.humSus > thresholds.humedadMaxima);
  // The healthy reading goes out as always; the impossible value only rides on the answers to LEER_AHORA of the pulse, so the
  // whole reread-and-discard flow happens inside the pulse the engine decides on.
  assert.equal(result.telemetria.humSus, 55);
  assert.equal(result.pulses, 1);
  assert.equal(result.needsCooldownReset, false);
  assert.equal(result.silenceZone, false);
  assert.equal(result.manualLock, null);
  assert.equal(result.clima, null);
  assert.equal(result.diagnostico, null);
  assert.match(result.resumen.join(' | '), /112/);
  assert.match(result.resumen.join(' | '), /emisión automática/);
});

test('the impossible value follows the catalog limit instead of a loose number', () => {
  const strict = { ...thresholds, humedadMaxima: 90 };
  assert.equal(calculateConditions(['INVALID_READING'], strict).pinnedMetrics.humSus, 102);
});

test('without it nothing is pinned', () => {
  assert.deepEqual(calculateConditions(['R01'], thresholds).pinnedMetrics, {});
});

test('the physical limit and the rereads come from the backend catalog, with the v2 values as fallback', () => {
  assert.equal(DEFAULT_THRESHOLDS['seguridad.rango-hum-max'], 100);
  assert.equal(DEFAULT_THRESHOLDS['seguridad.relecturas-invalidas'], 2);
  assert.equal(buildThresholds([]).humedadMaxima, 100);
  assert.equal(buildThresholds([]).relecturasInvalidas, 2);
});

test('the invalid-reading scenario exercises ReadingValidationGuard and needs no prior diagnosis', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'INVALID_READING');
  assert.deepEqual(scenario?.backendRules, ['ReadingValidationGuard']);
  assert.equal(scenario?.needsDiagnosis, false);
  assert.equal(scenario?.branch, 'GLOBAL');
});

test('a pinned impossible humidity cannot be mixed with anything that needs the soil humidity or an answering node', () => {
  for (const other of ['R01', 'R02', 'R03', 'R04', 'LOCK', 'NODE_OFFLINE']) {
    for (const selected of [[other, 'INVALID_READING'], ['INVALID_READING', other]]) {
      const result = calculateConditions(selected, thresholds);
      assert.equal(result.conflictos.length, 1, selected.join('+'));
      assert.deepEqual(result.pinnedMetrics, {}, 'with a conflict nothing is applied');
    }
  }
});

test('a pinned impossible humidity coexists with the shading and the diagnosis-driven rules', () => {
  for (const other of ['SHADING', 'SUPPLY']) {
    assert.equal(calculateConditions([other, 'INVALID_READING'], thresholds).conflictos.length, 0, other);
  }
});

/* ------------------------------------------------------------------ hardware incompleto (S-04) */

test('the incomplete-hardware scenario pins the dry soil of R-01 and asks for a sector without its valve', () => {
  const result = calculateConditions(['INCOMPLETE_HARDWARE'], thresholds);
  assert.equal(result.needsSectorWithoutValve, true);
  // Dry enough for R-01 to irrigate if the hardware were complete: the missing valve is the only reason it does not.
  const humSus = result.pinnedMetrics.humSus;
  assert.ok(humSus < thresholds.umbralHumedad);
  assert.ok(humSus > thresholds.umbralCritico);
  assert.equal(result.needsCooldownReset, true);
  assert.equal(result.silenceZone, false);
  assert.equal(result.manualLock, null);
  assert.equal(result.pulses, 1);
  assert.match(result.resumen.join(' | '), /electroválvula/);
  assert.match(result.resumen.join(' | '), /emisión automática/);
});

test('the dry humidity follows the catalog thresholds', () => {
  const result = calculateConditions(['INCOMPLETE_HARDWARE'], { ...thresholds, umbralCritico: 30, umbralHumedad: 50 });
  assert.equal(result.pinnedMetrics.humSus, 40);
});

test('without it no sector without a valve is needed', () => {
  assert.equal(calculateConditions(['R01'], thresholds).needsSectorWithoutValve, false);
});

test('the incomplete-hardware scenario exercises IncompleteHardwareRule and needs no prior diagnosis', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'INCOMPLETE_HARDWARE');
  assert.deepEqual(scenario?.backendRules, ['IncompleteHardwareRule']);
  assert.equal(scenario?.needsDiagnosis, false);
  assert.equal(scenario?.branch, 'GLOBAL');
});

test('the cause of the abort would be ambiguous next to saturated soil, a lock, a silent node or an impossible humidity', () => {
  for (const other of ['R02', 'R04', 'LOCK', 'NODE_OFFLINE', 'INVALID_READING']) {
    for (const selected of [[other, 'INCOMPLETE_HARDWARE'], ['INCOMPLETE_HARDWARE', other]]) {
      const result = calculateConditions(selected, thresholds);
      assert.equal(result.conflictos.length, 1, selected.join('+'));
      assert.deepEqual(result.pinnedMetrics, {}, 'with a conflict nothing is applied');
      assert.equal(result.needsSectorWithoutValve, false, 'with a conflict nothing is applied');
    }
  }
});

test('the incomplete-hardware scenario coexists with the rules that want the same dry soil, the shading and the diagnosis', () => {
  for (const other of ['R01', 'R03', 'SHADING', 'SUPPLY']) {
    assert.equal(calculateConditions([other, 'INCOMPLETE_HARDWARE'], thresholds).conflictos.length, 0, other);
  }
});

/* ------------------------------------------------------------------ fallas del nodo (S-05) */

test('no-flow: the node reports SIN_CAUDAL on a sector with its valve, over soil that asks for irrigation', () => {
  const result = calculateConditions(['FAULT_NO_FLOW'], thresholds);
  assert.deepEqual(result.nodeErrors, [{ tipo: 'SIN_CAUDAL', valor: 0 }]);
  assert.equal(result.needsSectorWithValve, true);
  assert.equal(result.needsFaultyDevice, false);
  // Dry enough for R-01 to irrigate if the fault were not marked: the fault is the only reason it does not.
  const humSus = result.pinnedMetrics.humSus;
  assert.ok(humSus < thresholds.umbralHumedad);
  assert.ok(humSus > thresholds.umbralCritico);
  assert.equal(result.needsCooldownReset, true);
  assert.equal(result.repair, null);
  assert.match(result.resumen.join(' | '), /SIN_CAUDAL/);
  assert.match(result.resumen.join(' | '), /riego manual/);
});

test('overflow: CAUDAL_EXCESIVO over wet soil, so the alert has no manual-irrigation request', () => {
  const result = calculateConditions(['FAULT_OVERFLOW'], thresholds);
  assert.equal(result.nodeErrors.length, 1);
  assert.equal(result.nodeErrors[0].tipo, 'CAUDAL_EXCESIVO');
  assert.equal(typeof result.nodeErrors[0].valor, 'number');
  assert.ok((result.nodeErrors[0].valor as number) > 0);
  assert.equal(result.needsSectorWithValve, true);
  // Above the irrigation threshold and below saturation: nothing is due and nothing blocks it.
  const humSus = result.pinnedMetrics.humSus;
  assert.ok(humSus > thresholds.umbralHumedad);
  assert.ok(humSus < thresholds.saturacionBloqueo);
  assert.match(result.resumen.join(' | '), /CAUDAL_EXCESIVO/);
});

test('the soil humidity of the flow faults follows the catalog thresholds', () => {
  const custom = { ...thresholds, umbralCritico: 30, umbralHumedad: 50, saturacionBloqueo: 70 };
  assert.equal(calculateConditions(['FAULT_NO_FLOW'], custom).pinnedMetrics.humSus, 40);
  assert.equal(calculateConditions(['FAULT_OVERFLOW'], custom).pinnedMetrics.humSus, 60);
});

test('shade limit: the node reports the closed-position limit switch, with no sector and no soil pinned', () => {
  const result = calculateConditions(['FAULT_SHADE_LIMIT'], thresholds);
  assert.deepEqual(result.nodeErrors, [{ tipo: 'MEDIASOMBRA_FIN_DE_CARRERA', valor: 'CERRADA' }]);
  assert.equal(result.needsSectorWithValve, false, 'the mesh belongs to the macro-zone, not to a sector');
  assert.deepEqual(result.pinnedMetrics, {});
  assert.match(result.resumen.join(' | '), /MEDIASOMBRA_FIN_DE_CARRERA/);
});

test('repair: registers the repair of the zone faulty devices and leaves the soil asking for irrigation again', () => {
  const result = calculateConditions(['REPAIR'], thresholds);
  assert.deepEqual(result.repair, { detail: 'Reparación de prueba desde el simulador' });
  assert.equal(result.needsFaultyDevice, true);
  assert.deepEqual(result.nodeErrors, []);
  assert.ok(result.pinnedMetrics.humSus < thresholds.umbralHumedad);
  assert.equal(result.needsCooldownReset, true);
  assert.match(result.resumen.join(' | '), /vuelve a regar/);
});

test('without a fault scenario nothing is reported, repaired or looked up in the registry', () => {
  const result = calculateConditions(['R01'], thresholds);
  assert.deepEqual(result.nodeErrors, []);
  assert.equal(result.repair, null);
  assert.equal(result.needsSectorWithValve, false);
  assert.equal(result.needsFaultyDevice, false);
});

test('each fault scenario exercises its rule and the service that marks the fault, and needs no diagnosis', () => {
  const byKey = (key: string) => SCENARIOS.find((s) => s.key === key)!;
  assert.deepEqual(byKey('FAULT_NO_FLOW').backendRules, ['HydraulicFaultRule', 'HardwareFaultService']);
  assert.deepEqual(byKey('FAULT_OVERFLOW').backendRules, ['HydraulicFaultRule', 'HardwareFaultService']);
  assert.deepEqual(byKey('FAULT_SHADE_LIMIT').backendRules, ['ShadeFaultRule', 'HardwareFaultService']);
  assert.deepEqual(byKey('REPAIR').backendRules, ['HardwareFaultService']);
  for (const key of ['FAULT_NO_FLOW', 'FAULT_OVERFLOW', 'FAULT_SHADE_LIMIT', 'REPAIR']) {
    assert.equal(byKey(key).needsDiagnosis, false, key);
  }
  assert.equal(byKey('FAULT_SHADE_LIMIT').branch, 'MEDIASOMBRA');
});

test('the cause would be ambiguous next to a lock, a silent node, an impossible humidity or the hardware scenario', () => {
  for (const fault of ['FAULT_NO_FLOW', 'FAULT_OVERFLOW', 'REPAIR']) {
    for (const other of ['R02', 'R04', 'LOCK', 'NODE_OFFLINE', 'INVALID_READING', 'INCOMPLETE_HARDWARE']) {
      for (const selected of [[other, fault], [fault, other]]) {
        const result = calculateConditions(selected, thresholds);
        assert.equal(result.conflictos.length, 1, selected.join('+'));
        assert.deepEqual(result.nodeErrors, [], 'with a conflict nothing is applied');
        assert.equal(result.repair, null, 'with a conflict nothing is applied');
      }
    }
  }
});

test('the shade fault is only ambiguous next to a lock, a silent node or the hardware scenario', () => {
  for (const other of ['LOCK', 'NODE_OFFLINE', 'INCOMPLETE_HARDWARE']) {
    for (const selected of [[other, 'FAULT_SHADE_LIMIT'], ['FAULT_SHADE_LIMIT', other]]) {
      assert.equal(calculateConditions(selected, thresholds).conflictos.length, 1, selected.join('+'));
    }
  }
  for (const other of ['R01', 'R02', 'R04', 'R03', 'SHADING', 'SUPPLY', 'INVALID_READING']) {
    assert.equal(calculateConditions([other, 'FAULT_SHADE_LIMIT'], thresholds).conflictos.length, 0, other);
  }
});

test('two faults on the same valve, or a repair next to a fault, say nothing clear: they conflict', () => {
  for (const [a, b] of [
    ['FAULT_NO_FLOW', 'FAULT_OVERFLOW'],
    ['FAULT_NO_FLOW', 'REPAIR'],
    ['FAULT_OVERFLOW', 'REPAIR'],
    ['FAULT_SHADE_LIMIT', 'REPAIR'],
  ]) {
    for (const selected of [[a, b], [b, a]]) {
      assert.equal(calculateConditions(selected, thresholds).conflictos.length, 1, selected.join('+'));
    }
  }
});

test('a hydraulic fault coexists with the rules that want the same dry soil, and with the mesh fault', () => {
  for (const other of ['R01', 'R03', 'SHADING', 'SUPPLY']) {
    assert.equal(calculateConditions([other, 'FAULT_NO_FLOW'], thresholds).conflictos.length, 0, other);
  }
  const both = calculateConditions(['FAULT_NO_FLOW', 'FAULT_SHADE_LIMIT'], thresholds);
  assert.equal(both.conflictos.length, 0);
  assert.deepEqual(both.nodeErrors.map((e) => e.tipo), ['SIN_CAUDAL', 'MEDIASOMBRA_FIN_DE_CARRERA'], 'both errors go out, in applyOrder');
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
    ['ZoneShading'],
  );
});

test('with every backend rule covered nothing is left without a scenario', () => {
  const covered = SCENARIOS.flatMap((s) => s.backendRules).map(demoRule);
  assert.deepEqual(rulesWithoutScenario(covered), []);
});
