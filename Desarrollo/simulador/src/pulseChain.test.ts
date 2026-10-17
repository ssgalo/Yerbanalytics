import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isPulseInProgress, runPulses } from './pulseChain';

const noWait = async () => {};

test('requests as many pulses as asked, one after the other', async () => {
  let requested = 0;
  const done = await runPulses(3, { trigger: async () => void requested++, sleep: noWait });
  assert.equal(requested, 3);
  assert.equal(done, 3);
});

test('a pulse still in course (409) is waited out, not counted and not an error', async () => {
  let attempts = 0;
  const slept: number[] = [];
  const done = await runPulses(1, {
    trigger: async () => {
      attempts++;
      if (attempts < 4) throw new Error('No se pudo disparar el pulso de sensado: 409 Ya hay una lectura de sensores en curso');
    },
    sleep: async (ms) => void slept.push(ms),
  });
  assert.equal(attempts, 4);
  assert.equal(done, 1);
  assert.equal(slept.length, 3);
});

test('another failure is not hidden', async () => {
  await assert.rejects(
    runPulses(2, {
      trigger: async () => {
        throw new Error('El backend no responde');
      },
      sleep: noWait,
    }),
    /no responde/,
  );
});

test('gives up with a clear message when the previous pulse never ends', async () => {
  await assert.rejects(
    runPulses(1, {
      trigger: async () => {
        throw new Error('409 en curso');
      },
      sleep: noWait,
      maxWaits: 5,
    }),
    /no terminó/,
  );
});

test('reports each pulse as it is accepted', async () => {
  const seen: number[] = [];
  await runPulses(2, { trigger: async () => {}, sleep: noWait, onPulse: (n) => seen.push(n) });
  assert.deepEqual(seen, [1, 2]);
});

test('recognises the backend answer for a pulse already running', () => {
  assert.equal(isPulseInProgress(new Error('409')), true);
  assert.equal(isPulseInProgress(new Error('Ya hay una lectura de sensores en curso')), true);
  assert.equal(isPulseInProgress(new Error('boom')), false);
});
