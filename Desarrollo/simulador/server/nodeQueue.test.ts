import { test } from 'node:test';
import assert from 'node:assert/strict';
import { NODE_QUEUE_CAPACITY, SimulatedNodes } from './nodeQueue.ts';
import type { ActuatorAck, ActuatorCommand } from './contract.ts';

/* A fake clock: timers fire only when the test advances time, in order. */
class FakeClock {
  t = 0;
  private seq = 0;
  private timers: { id: number; at: number; fn: () => void }[] = [];
  now = () => this.t;
  setTimer = (fn: () => void, ms: number) => {
    const id = ++this.seq;
    this.timers.push({ id, at: this.t + ms, fn });
    return id;
  };
  clearTimer = (handle: unknown) => {
    this.timers = this.timers.filter((x) => x.id !== handle);
  };
  advance(ms: number) {
    const end = this.t + ms;
    for (;;) {
      const due = this.timers.filter((x) => x.at <= end).sort((a, b) => a.at - b.at || a.id - b.id)[0];
      if (!due) break;
      this.timers = this.timers.filter((x) => x !== due);
      this.t = due.at;
      due.fn();
    }
    this.t = end;
  }
}

interface Emitted {
  zona: string;
  sector: string;
  ack: ActuatorAck;
}

function setup(maxLoads = 1) {
  const clock = new FakeClock();
  const out: Emitted[] = [];
  const nodes = new SimulatedNodes(
    { now: clock.now, setTimer: clock.setTimer, clearTimer: clock.clearTimer, maxLoads },
    (zona, sector, ack) => out.push({ zona, sector, ack }),
  );
  /** What was emitted since the last call, as `commandId status tipo` strings. */
  const drain = () => out.splice(0).map((e) => `${e.ack.commandId} ${e.ack.status} ${e.ack.detalle.tipo}`);
  return { clock, out, nodes, drain };
}

const valveOn = (id: string, durationSec = 20): ActuatorCommand => ({
  commandId: id, actuador: 'valve', accion: 'ON', parametros: { durationSec },
});
const pumpOn = (id: string, ml = 10): ActuatorCommand => ({
  commandId: id, actuador: 'pump', accion: 'ON', parametros: { ml },
});
const shadeSet = (id: string, targetPct: number, prioridad?: string): ActuatorCommand => ({
  commandId: id, actuador: 'shade', accion: 'SET', parametros: prioridad ? { targetPct, prioridad } : { targetPct },
});
const off = (id: string, actuador: 'valve' | 'pump'): ActuatorCommand => ({ commandId: id, actuador, accion: 'OFF', parametros: {} });

test('one pulse (limit 1): the first load starts, the others queue with position and a wait upper bound', () => {
  const { nodes, drain, out } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', pumpOn('p1', 10));
  nodes.receive('MZ-1', 'S1', shadeSet('m1', 0));

  assert.deepEqual(drain(), ['v1 EN_CURSO iniciado', 'p1 EN_CURSO en_cola', 'm1 EN_CURSO en_cola']);
  // details: re-send to read them (fresh node)
  const second = setup();
  second.nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  second.nodes.receive('MZ-1', 'S2', pumpOn('p1', 10));
  second.nodes.receive('MZ-1', 'S1', shadeSet('m1', 0));
  assert.deepEqual(second.out[0].ack.detalle, { tipo: 'iniciado', durationSec: 20 });
  assert.deepEqual(second.out[1].ack.detalle, { tipo: 'en_cola', posicion: 1, esperaMaxSec: 20 });
  assert.deepEqual(second.out[2].ack.detalle, { tipo: 'en_cola', posicion: 2, esperaMaxSec: 30 });
  assert.equal(out.length, 0);
});

test('loads run one at a time, in arrival order, each with its final ack', () => {
  const { nodes, drain, clock, out } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', pumpOn('p1', 10));
  nodes.receive('MZ-1', 'S1', shadeSet('m1', 0));
  drain();

  clock.advance(19_999);
  assert.deepEqual(drain(), []);
  clock.advance(1);
  assert.deepEqual(drain(), ['v1 SUCCESS ok', 'p1 EN_CURSO iniciado']);
  assert.deepEqual(out.length, 0);

  clock.advance(10_000);
  assert.deepEqual(drain(), ['p1 SUCCESS ok', 'm1 EN_CURSO iniciado']);
  clock.advance(3_000);
  assert.deepEqual(drain(), ['m1 SUCCESS ok']);
});

test('the iniciado ack carries the effective duration (ml for the pump, targetPct for the shade)', () => {
  const { nodes, out, clock } = setup();
  nodes.receive('MZ-1', 'S1', pumpOn('p1', 10));
  nodes.receive('MZ-1', 'S1', shadeSet('m1', 100));
  assert.deepEqual(out[0].ack.detalle, { tipo: 'iniciado', durationSec: 10, ml: 10 });
  clock.advance(10_000);
  assert.deepEqual(out.at(-1)!.ack.detalle, { tipo: 'iniciado', targetPct: 100 });
});

test('with SIM_NODE_MAX_LOADS=2 two loads run together and the third waits', () => {
  const { nodes, drain, clock } = setup(2);
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', valveOn('v2', 30));
  nodes.receive('MZ-1', 'S1', pumpOn('p1', 5));
  assert.deepEqual(drain(), ['v1 EN_CURSO iniciado', 'v2 EN_CURSO iniciado', 'p1 EN_CURSO en_cola']);
  clock.advance(20_000);
  assert.deepEqual(drain(), ['v1 SUCCESS ok', 'p1 EN_CURSO iniciado']);
});

test('OFF discards the queued load of that sector and actuator, and does not touch the load that is running', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', valveOn('v2', 20));
  drain();

  nodes.receive('MZ-1', 'S2', off('o1', 'valve'));
  assert.deepEqual(drain(), ['v2 ERROR cancelado', 'o1 SUCCESS ok']);

  clock.advance(20_000);
  assert.deepEqual(drain(), ['v1 SUCCESS ok']);   // S1 kept irrigating; S2 never started
});

test('OFF cuts the load that is running (cancelado, no SUCCESS later) and frees its place for the queue', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', valveOn('v2', 20));
  drain();

  nodes.receive('MZ-1', 'S1', off('o1', 'valve'));
  assert.deepEqual(drain(), ['v1 ERROR cancelado', 'o1 SUCCESS ok', 'v2 EN_CURSO iniciado']);

  clock.advance(100_000);
  assert.deepEqual(drain(), ['v2 SUCCESS ok']);
});

test('OFF of the pump leaves the valve of the same sector alone', () => {
  const { nodes, drain, clock } = setup(2);
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S1', pumpOn('p1', 10));
  drain();
  nodes.receive('MZ-1', 'S1', off('o1', 'pump'));
  assert.deepEqual(drain(), ['p1 ERROR cancelado', 'o1 SUCCESS ok']);
  clock.advance(20_000);
  assert.deepEqual(drain(), ['v1 SUCCESS ok']);
});

test('a new command for what is running replaces it and starts at once, in the same place', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('A', 20));
  nodes.receive('MZ-1', 'S2', valveOn('Q', 20));
  drain();

  nodes.receive('MZ-1', 'S1', valveOn('B', 30));
  assert.deepEqual(drain(), ['A ERROR reemplazado', 'B EN_CURSO iniciado']);

  clock.advance(30_000);
  assert.deepEqual(drain(), ['B SUCCESS ok', 'Q EN_CURSO iniciado']);   // A never reports SUCCESS
});

test('a new command for what is queued takes its place in the line', () => {
  const { nodes, drain, out } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('A', 20));
  nodes.receive('MZ-1', 'S2', pumpOn('X', 5));
  nodes.receive('MZ-1', 'S2', valveOn('B', 20));
  drain();
  out.length = 0;

  nodes.receive('MZ-1', 'S2', valveOn('B2', 25));
  assert.deepEqual(drain(), ['B ERROR reemplazado', 'B2 EN_CURSO en_cola']);
});

test('a shade SET with prioridad INMEDIATA starts now even with the node full and replaces the queued SET', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S1', shadeSet('m100', 100));
  drain();

  nodes.receive('MZ-1', 'S1', shadeSet('m0', 0, 'INMEDIATA'));
  assert.deepEqual(drain(), ['m100 ERROR reemplazado', 'm0 EN_CURSO iniciado']);

  clock.advance(3_000);
  assert.deepEqual(drain(), ['m0 SUCCESS ok']);
  clock.advance(17_000);
  assert.deepEqual(drain(), ['v1 SUCCESS ok']);
});

test('a plain shade SET 0 (engine, sequences) waits its turn: only the priority skips the queue', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  drain();
  nodes.receive('MZ-1', 'S1', shadeSet('m0', 0));
  assert.deepEqual(drain(), ['m0 EN_CURSO en_cola']);
});

test('prioridad is ignored on valve ON and pump ON', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  drain();
  nodes.receive('MZ-1', 'S2', { commandId: 'v2', actuador: 'valve', accion: 'ON', parametros: { durationSec: 20, prioridad: 'INMEDIATA' } });
  assert.deepEqual(drain(), ['v2 EN_CURSO en_cola']);
});

test('an unknown prioridad value is treated as absent', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  drain();
  nodes.receive('MZ-1', 'S1', shadeSet('m0', 0, 'URGENTE'));
  assert.deepEqual(drain(), ['m0 EN_CURSO en_cola']);
});

test('a full queue answers ERROR cola_llena at once and does not run the command', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S0', valveOn('run', 5));
  for (let i = 0; i < NODE_QUEUE_CAPACITY; i++) nodes.receive('MZ-1', `Q${i}`, valveOn(`q${i}`, 5));
  drain();

  nodes.receive('MZ-1', 'EXTRA', valveOn('extra', 5));
  assert.deepEqual(drain(), ['extra ERROR cola_llena']);
  clock.advance(5_000 * (NODE_QUEUE_CAPACITY + 2));
  assert.ok(!drain().some((l) => l.startsWith('extra ')), 'the rejected command never runs');
});

test('invalid commands and OFF answer at once and do not take a place', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  drain();
  nodes.receive('MZ-1', 'S2', { commandId: 'bad', actuador: 'valve', accion: 'ON', parametros: {} });
  nodes.receive('MZ-1', 'S2', { commandId: 'nope', actuador: 'laser', accion: 'ON' });
  nodes.receive('MZ-1', 'S2', off('o', 'valve'));
  assert.deepEqual(drain(), ['bad ERROR duracion_invalida', 'nope ERROR comando_invalido', 'o SUCCESS ok']);
});

test('each macro-zone is its own node: a load in another zone does not wait', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('a', 20));
  nodes.receive('MZ-2', 'S9', valveOn('b', 20));
  assert.deepEqual(drain(), ['a EN_CURSO iniciado', 'b EN_CURSO iniciado']);
});

test('the acks come out on the zone and sector of their own command', () => {
  const { nodes, out, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('a', 5));
  nodes.receive('MZ-1', 'S2', valveOn('b', 5));
  clock.advance(5_000);
  assert.deepEqual(out.map((e) => [e.zona, e.sector, e.ack.commandId, e.ack.detalle.tipo]), [
    ['MZ-1', 'S1', 'a', 'iniciado'],
    ['MZ-1', 'S2', 'b', 'en_cola'],
    ['MZ-1', 'S1', 'a', 'ok'],
    ['MZ-1', 'S2', 'b', 'iniciado'],
  ]);
});

test('a redelivered commandId that is queued or running is ignored (QoS 1), like the firmware', () => {
  const { nodes, drain } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 20));
  nodes.receive('MZ-1', 'S2', valveOn('v2', 20));
  drain();

  nodes.receive('MZ-1', 'S1', valveOn('v1', 20)); // running
  nodes.receive('MZ-1', 'S2', valveOn('v2', 20)); // queued
  assert.deepEqual(drain(), []);
});

test('a redelivered commandId that already finished gets its final ack again, and does not run twice', () => {
  const { nodes, drain, clock } = setup();
  nodes.receive('MZ-1', 'S1', valveOn('v1', 5));
  clock.advance(5_000);
  drain();

  nodes.receive('MZ-1', 'S1', valveOn('v1', 5));
  assert.deepEqual(drain(), ['v1 SUCCESS ok']);
  clock.advance(10_000);
  assert.deepEqual(drain(), []);
});
