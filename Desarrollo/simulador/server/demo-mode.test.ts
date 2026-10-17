import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDemoModeProbe } from './demo-mode.ts';

function probe(statuses: Array<number | 'throw'>, nowRef = { t: 0 }) {
  let calls = 0;
  const p = createDemoModeProbe({
    check: async () => {
      const s = statuses[Math.min(calls++, statuses.length - 1)];
      if (s === 'throw') throw new Error('backend caído');
      return s;
    },
    now: () => nowRef.t,
    ttlMs: 1000,
  });
  return { p, calls: () => calls, nowRef };
}

test('demo mode is available when /api/demo/rules answers 200', async () => {
  const { p } = probe([200]);
  assert.equal(await p.isAvailable(), true);
});

test('demo mode is not available when the backend answers 404 (real mode)', async () => {
  const { p } = probe([404]);
  assert.equal(await p.isAvailable(), false);
});

test('before the first answer it is not available (regular topic)', async () => {
  const { p } = probe(['throw']);
  assert.equal(await p.isAvailable(), false);
});

test('a backend that is down keeps the last known answer', async () => {
  const { p, nowRef } = probe([200, 'throw', 503]);
  assert.equal(await p.isAvailable(), true);
  nowRef.t = 2000;
  assert.equal(await p.isAvailable(), true);
  nowRef.t = 4000;
  assert.equal(await p.isAvailable(), true);
});

test('a later 404 replaces a previous 200', async () => {
  const { p, nowRef } = probe([200, 404]);
  assert.equal(await p.isAvailable(), true);
  nowRef.t = 2000;
  assert.equal(await p.isAvailable(), false);
});

test('the answer is cached for the ttl', async () => {
  const { p, calls, nowRef } = probe([200]);
  await p.isAvailable();
  nowRef.t = 500;
  await p.isAvailable();
  assert.equal(calls(), 1);
  nowRef.t = 1500;
  await p.isAvailable();
  assert.equal(calls(), 2);
});
