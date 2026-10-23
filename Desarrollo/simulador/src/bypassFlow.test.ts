import assert from 'node:assert/strict';
import { test } from 'node:test';
import { applyBypassRules, withBypasses, type BypassDeps } from './bypassFlow';
import type { DemoRule, DemoRuleUpdate } from './types';

function rule(name: string, bypassKeys: string[], bypassed: string[] = []): DemoRule {
  return {
    name, label: name, branch: 'INSUMO', priority: 22, enabled: true,
    cooldownKeys: [], bypassKeys, cooldownMinutes: {}, bypassed,
  };
}

function deps(rules: DemoRule[]) {
  const updates: Array<[string, DemoRuleUpdate]> = [];
  const d: BypassDeps = {
    getRules: async () => rules,
    updateRule: async (name, update) => {
      updates.push([name, update]);
    },
  };
  return { d, updates };
}

test('withBypasses adds only the keys that are missing, keeping the ones already active', () => {
  assert.deepEqual(withBypasses(rule('R', ['a', 'b'], ['a']), ['a', 'b']), ['a', 'b']);
  assert.deepEqual(withBypasses(rule('R', ['a', 'b'], ['a']), ['b']), ['a', 'b']);
});

test('withBypasses has nothing to do when every key is already active', () => {
  assert.equal(withBypasses(rule('R', ['a'], ['a']), ['a']), null);
});

test('nothing is asked and nothing is read when no scenario wants a bypass', async () => {
  const { d, updates } = deps([]);
  let read = false;
  d.getRules = async () => {
    read = true;
    return [];
  };

  assert.deepEqual(await applyBypassRules({}, d), []);
  assert.equal(read, false);
  assert.deepEqual(updates, []);
});

test('the bypass is turned on and the ones the operator already had stay', async () => {
  const { d, updates } = deps([rule('LowEcRule', ['samples', 'other'], ['other'])]);

  const lines = await applyBypassRules({ LowEcRule: ['samples'] }, d);

  assert.deepEqual(updates, [['LowEcRule', { bypassed: ['other', 'samples'] }]]);
  assert.match(lines.join('\n'), /✓ Bypass samples de LowEcRule activado/);
});

test('a bypass that was already active is not written again', async () => {
  const { d, updates } = deps([rule('LowEcRule', ['samples'], ['samples'])]);

  const lines = await applyBypassRules({ LowEcRule: ['samples'] }, d);

  assert.deepEqual(updates, []);
  assert.match(lines.join('\n'), /ya estaba activo/);
});

test('a rule the backend does not list is reported, not fatal', async () => {
  const { d, updates } = deps([rule('Other', ['x'])]);

  const lines = await applyBypassRules({ LowEcRule: ['samples'] }, d);

  assert.deepEqual(updates, []);
  assert.match(lines[0], /⚠ LowEcRule no figura/);
});

test('a key the rule does not declare is reported and nothing is written', async () => {
  const { d, updates } = deps([rule('LowEcRule', ['other'])]);

  const lines = await applyBypassRules({ LowEcRule: ['samples'] }, d);

  assert.deepEqual(updates, []);
  assert.match(lines[0], /no declara el bypass samples/);
});

test('several rules are handled one by one', async () => {
  const { d, updates } = deps([rule('A', ['x']), rule('B', ['y'])]);

  await applyBypassRules({ A: ['x'], B: ['y'] }, d);

  assert.deepEqual(updates, [['A', { bypassed: ['x'] }], ['B', { bypassed: ['y'] }]]);
});
