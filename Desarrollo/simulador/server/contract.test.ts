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

test('the rail topics and vocabulary mirror contrato.h', async () => {
  const c = await import('./contract.ts');
  assert.equal(c.RAIL_COMMAND_TOPIC, 'nursery/rail/command');
  assert.equal(c.RAIL_EVENT_TOPIC, 'nursery/rail/event');
  assert.equal(c.RAIL_ACTUATOR, 'rail');
  assert.deepEqual([...c.RAIL_ACTIONS], ['IR_A', 'HOME']);
  assert.deepEqual([...c.RAIL_STATUSES], ['ACEPTADO', 'LLEGO', 'ERROR']);
  assert.deepEqual(
    [...c.RAIL_ERROR_CODES],
    ['COMANDO_INVALIDO', 'HOME_NO_ENCONTRADO', 'FIN_DE_CARRERA', 'REEMPLAZADO'],
  );
});

test('command and ack topics mirror contrato.h', async () => {
  const c = await import('./contract.ts');
  assert.equal(c.ackTopic('MZ-1', 'MZ-1-002'), 'nursery/zone/MZ-1/sector/MZ-1-002/ack');
  assert.deepEqual(c.parseCommandTopic('nursery/zone/MZ-1/sector/MZ-1-002/command'), {
    zonaId: 'MZ-1',
    sectorId: 'MZ-1-002',
  });
  assert.equal(c.parseCommandTopic('nursery/zone/MZ-1/telemetry'), null);
  assert.equal(c.parseCommandTopic('nursery/rail/command'), null);
});

test('a valve command is acked when the irrigation ends', async () => {
  const { simulatedAck } = await import('./contract.ts');
  const r = simulatedAck({ commandId: 'c1', actuador: 'valve', accion: 'ON', parametros: { durationSec: 600 } });
  assert.equal(r.delayMs, 600_000);
  assert.deepEqual(r.ack, { commandId: 'c1', status: 'SUCCESS', detalle: { tipo: 'ok', durationSec: 600 } });

  const trimmed = simulatedAck({ commandId: 'c2', actuador: 'valve', accion: 'ON', parametros: { durationSec: 5000 } });
  assert.equal(trimmed.delayMs, 1_200_000);
  assert.equal(trimmed.ack.detalle.tipo, 'limite_excedido');

  const invalid = simulatedAck({ commandId: 'c3', actuador: 'valve', accion: 'ON', parametros: {} });
  assert.equal(invalid.delayMs, 0);
  assert.equal(invalid.ack.status, 'ERROR');
  assert.equal(invalid.ack.detalle.tipo, 'duracion_invalida');
});

test('a pump ON without parameters runs the firmware default', async () => {
  const { simulatedAck, INPUT_DEFAULT_SEC } = await import('./contract.ts');
  const r = simulatedAck({ commandId: 'p1', actuador: 'pump', accion: 'ON', parametros: {} });
  assert.equal(r.delayMs, INPUT_DEFAULT_SEC * 1000);
  assert.equal(r.ack.status, 'SUCCESS');
  assert.equal(r.ack.detalle.durationSec, INPUT_DEFAULT_SEC);
});

test('a shade SET is acked with the reached opening (two states)', async () => {
  const { simulatedAck } = await import('./contract.ts');
  assert.equal(simulatedAck({ commandId: 's1', actuador: 'shade', accion: 'SET', parametros: { targetPct: 70 } }).ack.detalle.targetPct, 100);
  assert.equal(simulatedAck({ commandId: 's2', actuador: 'shade', accion: 'SET', parametros: { targetPct: 20 } }).ack.detalle.targetPct, 0);
  assert.equal(simulatedAck({ commandId: 's3', actuador: 'shade', accion: 'SET', parametros: {} }).ack.detalle.tipo, 'parametros_invalidos');
});

test('OFF is acked at once and unknown actuators are rejected', async () => {
  const { simulatedAck } = await import('./contract.ts');
  const off = simulatedAck({ commandId: 'o1', actuador: 'valve', accion: 'off' });
  assert.equal(off.delayMs, 0);
  assert.equal(off.ack.status, 'SUCCESS');
  const unknown = simulatedAck({ commandId: 'u1', actuador: 'fan', accion: 'ON' });
  assert.equal(unknown.ack.status, 'ERROR');
  assert.equal(unknown.ack.detalle.tipo, 'comando_invalido');
});

test('a zone command topic is parsed to its zone; sector and other topics are not', async () => {
  const c = await import('./contract.ts');
  assert.deepEqual(c.parseZoneCommandTopic('nursery/zone/MZ-3/command'), { zonaId: 'MZ-3' });
  assert.equal(c.parseZoneCommandTopic('nursery/zone/MZ-1/sector/MZ-1-002/command'), null);
  assert.equal(c.parseZoneCommandTopic('nursery/zone/MZ-1/telemetry'), null);
  assert.equal(c.parseZoneCommandTopic('nursery/rail/command'), null);
  assert.equal(c.parseZoneCommandTopic('nursery/zone//command'), null);
  assert.equal(c.parseZoneCommandTopic(c.zoneCommandTopic('MZ-2')) !== null, true);
});

test('only a well-formed LEER_AHORA payload is a read-now request', async () => {
  const { isReadNow } = await import('./contract.ts');
  assert.equal(isReadNow({ commandId: 'x', accion: 'LEER_AHORA', parametros: {} }), true);
  assert.equal(isReadNow({ accion: 'LEER_AHORA' }), true);
  assert.equal(isReadNow({ accion: 'OTRA' }), false);
  assert.equal(isReadNow({ accion: 'leer_ahora' }), false);
  assert.equal(isReadNow({}), false);
  assert.equal(isReadNow(null), false);
  assert.equal(isReadNow('LEER_AHORA'), false);
  assert.equal(isReadNow(42), false);
  assert.equal(isReadNow([]), false);
});

test('a macro-zone id maps to its index; anything else is not a macro-zone', async () => {
  const { macroZoneIndex } = await import('./contract.ts');
  assert.equal(macroZoneIndex('MZ-1'), 1);
  assert.equal(macroZoneIndex('MZ-12'), 12);
  assert.equal(macroZoneIndex('MZ-0'), null);
  assert.equal(macroZoneIndex('MZ-'), null);
  assert.equal(macroZoneIndex('MZ-1x'), null);
  assert.equal(macroZoneIndex('Z-1'), null);
});
