import { afterEach, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { answerReadNow, isActive, setActive, setDependencies } from './emission.ts';
import type { TelemetryPayload } from './contract.ts';

let published: Array<{ zoneId: string; payload: TelemetryPayload }>;

/** Lets the fire-and-forget boot round finish (it only awaits resolved promises). */
const settle = async () => {
  for (let i = 0; i < 5; i++) await new Promise((r) => setImmediate(r));
};

beforeEach(() => {
  published = [];
  setDependencies({
    publish: async (zoneId, payload) => {
      published.push({ zoneId, payload });
    },
    fetchTopology: async () => ({ macroZonas: 3 }),
  });
});

afterEach(() => {
  setActive(false);
  setDependencies();
});

test('a LEER_AHORA for a known macro-zone is answered with exactly one reading for that zone', async () => {
  setActive(true);
  await settle();
  published.length = 0; // discard the boot round
  await answerReadNow('MZ-2');
  assert.equal(published.length, 1);
  assert.equal(published[0].zoneId, 'MZ-2');
  assert.equal(published[0].payload.mac, 'A4:CF:12:9A:00:02');
});

test('a LEER_AHORA for an unknown zone is ignored', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  await answerReadNow('MZ-99'); // beyond the topology (3 macro-zones)
  await answerReadNow('ZONA-REAL'); // not a simulated macro-zone
  assert.equal(published.length, 0);
});

test('turning emission on sends a boot round with one reading per macro-zone', async () => {
  setActive(true);
  await settle();
  assert.deepEqual(
    published.map((p) => p.zoneId),
    ['MZ-1', 'MZ-2', 'MZ-3'],
  );
});

test('emission publishes nothing on its own and creates no timers', async () => {
  const realInterval = globalThis.setInterval;
  const realTimeout = globalThis.setTimeout;
  let timers = 0;
  globalThis.setInterval = ((...args: Parameters<typeof setInterval>) => {
    timers++;
    return realInterval(...args);
  }) as typeof setInterval;
  globalThis.setTimeout = ((...args: Parameters<typeof setTimeout>) => {
    timers++;
    return realTimeout(...args);
  }) as typeof setTimeout;
  try {
    setActive(true);
    await settle();
    const afterBoot = published.length;
    await new Promise((r) => realTimeout(r, 50));
    assert.equal(published.length, afterBoot, 'no publish beyond the boot round');
    assert.equal(timers, 0, 'emission registered no timers');
  } finally {
    globalThis.setInterval = realInterval;
    globalThis.setTimeout = realTimeout;
  }
});

test('a LEER_AHORA is ignored while emission is off, and after it is turned off again', async () => {
  assert.equal(isActive(), false);
  await answerReadNow('MZ-1');
  assert.equal(published.length, 0);

  setActive(true);
  await settle();
  setActive(false);
  published.length = 0;
  await answerReadNow('MZ-1');
  assert.equal(published.length, 0);
});

test('an unreachable topology falls back to the six seeded macro-zones', async () => {
  setDependencies({
    publish: async (zoneId, payload) => {
      published.push({ zoneId, payload });
    },
    fetchTopology: async () => null,
  });
  setActive(true);
  await settle();
  assert.equal(published.length, 6);
});
