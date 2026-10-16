import assert from 'node:assert/strict';
import { test } from 'node:test';
import { buildThresholds, calculateConditions } from './ruleConditions';

const thresholds = buildThresholds([]);

test('SupplyRule plans a prior diagnosis above the minimum confidence', () => {
  const result = calculateConditions(['SUPPLY'], thresholds);
  assert.ok(result.diagnostico);
  assert.ok((result.diagnostico.conf ?? 0) >= thresholds.confianzaMinima);
  assert.ok((result.diagnostico.conf ?? 101) <= 100);
  assert.equal(result.diagnostico.sev, 'Alta');
});

test('the planned confidence follows the backend threshold and is capped at 100', () => {
  const strict = buildThresholds([
    { clave: 'diagnostico.confianza-minima', etiqueta: '', valor: '98', fabrica: '85', unidad: '%' },
  ]);
  assert.equal(calculateConditions(['SUPPLY'], strict).diagnostico?.conf, 100);
});

test('rules that do not decide with the diagnosis plan none', () => {
  assert.equal(calculateConditions(['R01', 'SHADING'], thresholds).diagnostico, null);
});

test('SupplyRule forces a critical air temperature without touching substrate humidity', () => {
  const alone = calculateConditions(['SUPPLY'], thresholds);
  assert.equal(alone.telemetria.temp, 40);
  const withIrrigation = calculateConditions(['R01', 'SUPPLY'], thresholds);
  assert.equal(withIrrigation.telemetria.temp, 40);
  assert.ok(withIrrigation.telemetria.humSus > thresholds.umbralCritico);
});
