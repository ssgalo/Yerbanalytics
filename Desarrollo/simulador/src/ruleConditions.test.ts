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
    { regla: 'ApplicationService', clave: 'diagnostico.confianza-minima', etiqueta: '', valor: '98', fabrica: '85', unidad: '%' },
  ]);
  assert.equal(calculateConditions(['SUPPLY'], strict).diagnostico?.conf, 100);
});

test('rules that do not decide with the diagnosis plan none', () => {
  assert.equal(calculateConditions(['R01', 'SHADING'], thresholds).diagnostico, null);
});

test('SupplyRule forces a critical ambient humidity without touching substrate humidity or air temperature', () => {
  const alone = calculateConditions(['SUPPLY'], thresholds);
  assert.equal(alone.telemetria.humAmb, 98);
  // Con calor P-D3 no deja aplicar un insumo: el estado crítico no puede salir de la temperatura.
  assert.ok(alone.telemetria.temp <= 30);
  const withIrrigation = calculateConditions(['R01', 'SUPPLY'], thresholds);
  assert.equal(withIrrigation.telemetria.humAmb, 98);
  assert.ok(withIrrigation.telemetria.humSus > thresholds.umbralCritico);
});

const copy = (regla: string, clave: string, valor: string) => ({ regla, clave, etiqueta: '', valor, fabrica: '', unidad: '' });

test('each threshold comes from the copy of the rule its scenario exercises', () => {
  const t = buildThresholds([
    copy('PosponerPorLluviaRule', 'riego.umbral-humedad', '50'),
    copy('RiegoPorDeficitRule', 'riego.umbral-humedad', '40'),
    copy('PosponerPorLluviaRule', 'riego.umbral-critico', '30'),
    copy('DeficitCriticoRule', 'riego.umbral-critico', '33'),
  ]);
  assert.equal(t.umbralHumedad, 40);
  assert.equal(t.umbralCritico, 33);
});

test('without the copy of its rule a threshold falls back to any copy, then to the factory value', () => {
  const t = buildThresholds([copy('FueraDeVentanaRiegoRule', 'riego.umbral-humedad', '48')]);
  assert.equal(t.umbralHumedad, 48);
  assert.equal(t.umbralCritico, 35);
});

test('the minimum confidence is the strictest of all the copies', () => {
  const t = buildThresholds([
    copy('FungalDamageRule', 'diagnostico.confianza-minima', '60'),
    copy('ApplicationService', 'diagnostico.confianza-minima', '80'),
  ]);
  assert.equal(t.confianzaMinima, 80);
});
