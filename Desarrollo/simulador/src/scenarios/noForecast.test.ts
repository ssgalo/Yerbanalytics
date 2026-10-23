import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildThresholds, calculateConditions } from '../ruleConditions';
import { SCENARIOS } from './index';

const thresholds = buildThresholds([]);

test('NO_FORECAST: pone el modo sin pronóstico y un clima de lluvia fuerte que, con pronóstico, pospondría el riego', () => {
  const r = calculateConditions(['NO_FORECAST'], thresholds);

  assert.equal(r.offlineForecast, true);
  assert.ok((r.clima?.probLluviaPct ?? 0) >= thresholds.lluviaProbabilidad);
  assert.ok((r.clima?.precipMm ?? 0) >= thresholds.lluviaMm);
  assert.equal(r.needsCooldownReset, false);
});

test('NO_FORECAST se combina con R-01: el riego sale aunque el clima simulado anuncie lluvia', () => {
  const r = calculateConditions(['R01', 'NO_FORECAST'], thresholds);

  assert.deepEqual(r.conflictos, []);
  assert.equal(r.offlineForecast, true);
  assert.equal(r.telemetria.humSus, 40, 'R-01 sigue publicando su humedad');
  assert.ok(r.resumen.some((line) => line.includes('sin pronóstico')));
});

test('NO_FORECAST se combina con las reglas que usan el pronóstico: R-03, la mediasombra y las aplicaciones', () => {
  for (const other of ['R03', 'SHADING', 'APPLICATION_PRECONDITIONS']) {
    const r = calculateConditions([other, 'NO_FORECAST'], thresholds);

    assert.deepEqual(r.conflictos, [], other);
    assert.equal(r.offlineForecast, true, other);
  }
});

test('sin el modificador no se activa el modo sin pronóstico', () => {
  assert.equal(calculateConditions(['R01'], thresholds).offlineForecast, false);
});

test('NO_FORECAST ejercita R-03 y las precondiciones de aplicación', () => {
  const scenario = SCENARIOS.find((s) => s.key === 'NO_FORECAST')!;

  assert.deepEqual(scenario.backendRules, ['PosponerPorLluviaRule', 'ApplicationPreconditions']);
});
