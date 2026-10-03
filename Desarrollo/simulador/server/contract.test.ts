import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VALVE_MAX_DURATION_SEC, valveDurationWarning } from './contract.ts';

test('the valve maximum mirrors the contract (1200 s)', () => {
  assert.equal(VALVE_MAX_DURATION_SEC, 1200);
});

test('a valve command within the limit does not warn', () => {
  assert.equal(valveDurationWarning({ actuador: 'valve', parametros: { durationSec: 1200 } }), null);
  assert.equal(valveDurationWarning({ actuador: 'valve', parametros: { durationSec: 600 } }), null);
});

test('a valve command above the limit warns with the value and the maximum', () => {
  const warning = valveDurationWarning({ actuador: 'valve', parametros: { durationSec: 1201 } });
  assert.ok(warning);
  assert.match(warning, /1201/);
  assert.match(warning, /1200/);
});

test('other actuators and malformed commands never warn', () => {
  assert.equal(valveDurationWarning({ actuador: 'pump', parametros: { durationSec: 5000 } }), null);
  assert.equal(valveDurationWarning({ actuador: 'valve' }), null);
  assert.equal(valveDurationWarning({ actuador: 'valve', parametros: { durationSec: '9999' } }), null);
});
