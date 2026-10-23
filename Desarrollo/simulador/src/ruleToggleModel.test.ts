import test from 'node:test';
import assert from 'node:assert/strict';
import { operationalLegend, nextEnabled } from './ruleToggleModel';
import type { DemoRule } from './types';

const base: DemoRule = {
  name: 'SupplyRule', label: 'Dosificación', branch: 'INSUMO', priority: 20,
  enabled: true, operationalActive: true, effectiveEnabled: true,
  cooldownKeys: [], bypassKeys: [], cooldownMinutes: {}, bypassed: [],
};

test('una regla apagada en el vivero muestra la leyenda "Apagada en el vivero"', () => {
  assert.equal(operationalLegend({ ...base, operationalActive: false, effectiveEnabled: false }), 'Apagada en el vivero');
});

test('una regla activa en el vivero no lleva leyenda', () => {
  assert.equal(operationalLegend(base), null);
});

test('el interruptor muestra el estado efectivo y alterna a partir de él', () => {
  const offInVivero = { ...base, operationalActive: false, effectiveEnabled: false };
  assert.equal(nextEnabled(offInVivero), true);
  assert.equal(nextEnabled(base), false);
});
