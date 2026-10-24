import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseNodeAcks, shouldAnswerAsNode } from './nodeAcks.ts';

test('parseNodeAcks: on/true/1 y off/false/0, sin distinguir mayúsculas', () => {
  for (const v of ['on', 'ON', 'true', '1', ' On ']) assert.deepEqual(parseNodeAcks(v), { enabled: true });
  for (const v of ['off', 'OFF', 'false', '0', ' Off ']) assert.deepEqual(parseNodeAcks(v), { enabled: false });
});

test('parseNodeAcks: sin valor es on, sin advertencia', () => {
  assert.deepEqual(parseNodeAcks(undefined), { enabled: true });
  assert.deepEqual(parseNodeAcks(''), { enabled: true });
});

test('parseNodeAcks: un valor desconocido es on y advierte', () => {
  const r = parseNodeAcks('quizas');
  assert.equal(r.enabled, true);
  assert.match(r.warning ?? '', /SIM_NODE_ACKS.*quizas/);
});

test('shouldAnswerAsNode: sólo con ACK encendidos y un sensor simulado en la zona', () => {
  assert.equal(shouldAnswerAsNode({ acksEnabled: true, zoneHasSimulatedSensor: true }), true);
  assert.equal(shouldAnswerAsNode({ acksEnabled: true, zoneHasSimulatedSensor: false }), false);
  assert.equal(shouldAnswerAsNode({ acksEnabled: false, zoneHasSimulatedSensor: true }), false);
  assert.equal(shouldAnswerAsNode({ acksEnabled: false, zoneHasSimulatedSensor: false }), false);
});
