import { afterEach, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  answerReadNow,
  pinnedMetrics,
  isActive,
  isSilenced,
  setActive,
  setDependencies,
  setPinnedMetrics,
  setSilenced,
  silencedZones,
} from './emission.ts';
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
  silencedZones().forEach((zone) => setSilenced(zone, false));
  Object.keys(pinnedMetrics()).forEach((zone) => setPinnedMetrics(zone, null));
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

test('a silenced zone does not answer LEER_AHORA, the others still do', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setSilenced('MZ-2', true);
  await answerReadNow('MZ-2');
  await answerReadNow('MZ-1');
  assert.deepEqual(
    published.map((p) => p.zoneId),
    ['MZ-1'],
  );
});

test('a silenced zone answers again once it is released', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setSilenced('MZ-2', true);
  setSilenced('MZ-2', false);
  await answerReadNow('MZ-2');
  assert.equal(published.length, 1);
});

test('a silenced zone is also left out of the boot round', async () => {
  setSilenced('MZ-2', true);
  setActive(true);
  await settle();
  assert.deepEqual(
    published.map((p) => p.zoneId),
    ['MZ-1', 'MZ-3'],
  );
});

test('silencing is idempotent and reported', () => {
  assert.equal(isSilenced('MZ-1'), false);
  setSilenced('MZ-1', true);
  setSilenced('MZ-1', true);
  assert.equal(isSilenced('MZ-1'), true);
  assert.deepEqual(silencedZones(), ['MZ-1']);
  setSilenced('MZ-1', false);
  setSilenced('MZ-1', false);
  assert.deepEqual(silencedZones(), []);
});

/* ------------------------------------------------------------------ pinned readings (scenarios) */

test('a pinned metric rides on every answer to LEER_AHORA, however many times the backend asks again', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-2', { humSus: 112 });
  for (let i = 0; i < 5; i++) await answerReadNow('MZ-2');
  assert.equal(published.length, 5);
  for (const p of published) assert.equal(p.payload.metrics.humSus, 112);
});

test('the rest of the reading stays a normal random one', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-2', { humSus: 112 });
  await answerReadNow('MZ-2');
  const m = published[0].payload.metrics;
  assert.ok(m.temp !== undefined && m.temp >= 17 && m.temp <= 28, 'temp is still random');
  assert.ok(m.phSuelo !== undefined && m.phSuelo >= 4.8 && m.phSuelo <= 6.2, 'pH is still random');
});

test('only the pinned zone is affected', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-2', { humSus: 112 });
  await answerReadNow('MZ-1');
  assert.ok((published[0].payload.metrics.humSus ?? 0) <= 63, 'MZ-1 reads a normal humidity');
});

test('the boot round carries the pinned metric too', async () => {
  setPinnedMetrics('MZ-3', { humSus: 130 });
  setActive(true);
  await settle();
  const mz3 = published.find((p) => p.zoneId === 'MZ-3');
  assert.equal(mz3?.payload.metrics.humSus, 130);
});

test('a pinned metric is given in UI units: a conductivity in dS/m travels in µS/cm', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-1', { ce: 12 });
  await answerReadNow('MZ-1');
  assert.equal(published[0].payload.metrics.ce, 12000);
});

test('releasing the zone brings back normal readings', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-2', { humSus: 112 });
  setPinnedMetrics('MZ-2', null);
  await answerReadNow('MZ-2');
  assert.ok((published[0].payload.metrics.humSus ?? 0) <= 63);
  assert.deepEqual(pinnedMetrics(), {});
});

test('an empty set of metrics is the same as releasing it', () => {
  setPinnedMetrics('MZ-2', { humSus: 112 });
  setPinnedMetrics('MZ-2', {});
  assert.deepEqual(pinnedMetrics(), {});
});

test('pinned metrics are reported by zone, in UI units, and replacing them drops the previous ones', () => {
  setPinnedMetrics('MZ-2', { humSus: 112, temp: 90 });
  setPinnedMetrics('MZ-2', { phSuelo: 11 });
  setPinnedMetrics('MZ-1', { ce: 12 });
  assert.deepEqual(pinnedMetrics(), { 'MZ-2': { phSuelo: 11 }, 'MZ-1': { ce: 12 } });
});

test('a pinned metric does not make a silenced zone answer', async () => {
  setActive(true);
  await settle();
  published.length = 0;
  setPinnedMetrics('MZ-2', { humSus: 112 });
  setSilenced('MZ-2', true);
  await answerReadNow('MZ-2');
  assert.equal(published.length, 0);
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
