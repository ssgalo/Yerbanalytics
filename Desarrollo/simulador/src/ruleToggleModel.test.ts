import test from 'node:test';
import assert from 'node:assert/strict';
import { stateLegend } from './ruleToggleModel';
import type { DemoRule } from './types';

const base: DemoRule = {
  name: 'SupplyRule', label: 'Dosificación', branch: 'INSUMO', priority: 20,
  enabled: true, cooldownKeys: [], bypassKeys: [], cooldownMinutes: {}, bypassed: [],
};

test('una regla prendida en el vivero lo dice, sólo lectura', () => {
  assert.equal(stateLegend(base), 'Prendida en el vivero');
});

test('una regla apagada en el vivero avisa dónde se cambia', () => {
  assert.equal(
    stateLegend({ ...base, enabled: false }),
    'Apagada en el vivero — se cambia en Motor de reglas',
  );
});
