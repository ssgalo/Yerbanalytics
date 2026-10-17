import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseNodeErrorRequest } from './node-errors.ts';
import { InvalidInput } from './store.ts';

test('a flow error needs the zone, the sector and the type; the flow defaults by type', () => {
  assert.deepEqual(parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012', tipo: 'SIN_CAUDAL' }), {
    zonaId: 'MZ-1',
    input: { nodo: 'SIM-MZ-1', sector: 'MZ-1-012', tipo: 'SIN_CAUDAL', valor: 0 },
  });
  assert.equal(
    parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012', tipo: 'CAUDAL_EXCESIVO' }).input.valor,
    180,
  );
});

test('the measured flow and the node serial are taken from the request when they come', () => {
  const parsed = parseNodeErrorRequest({
    zonaId: 'MZ-2',
    serial: 'A4:CF:12:9A:00:02',
    sector: 'MZ-2-005',
    tipo: 'CAUDAL_EXCESIVO',
    valor: '250',
  });

  assert.equal(parsed.input.nodo, 'A4:CF:12:9A:00:02');
  assert.equal(parsed.input.valor, 250);
});

test('the shade error belongs to the macro-zone: the sector is ignored and the limit switch defaults to the closed one', () => {
  const parsed = parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012', tipo: 'MEDIASOMBRA_FIN_DE_CARRERA' });

  assert.deepEqual(parsed.input, { nodo: 'SIM-MZ-1', sector: '', tipo: 'MEDIASOMBRA_FIN_DE_CARRERA', valor: 'CERRADA' });
});

test('the shade error accepts either limit switch and nothing else', () => {
  assert.equal(
    parseNodeErrorRequest({ zonaId: 'MZ-1', tipo: 'MEDIASOMBRA_FIN_DE_CARRERA', valor: 'ABIERTA' }).input.valor,
    'ABIERTA',
  );
  assert.throws(
    () => parseNodeErrorRequest({ zonaId: 'MZ-1', tipo: 'MEDIASOMBRA_FIN_DE_CARRERA', valor: 'MEDIA' }),
    InvalidInput,
  );
});

test('a type the contract does not define is rejected, and says which ones exist', () => {
  assert.throws(
    () => parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012', tipo: 'EXPLOSION' }),
    (e: unknown) => e instanceof InvalidInput && /SIN_CAUDAL/.test(e.message),
  );
  assert.throws(() => parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012' }), InvalidInput);
});

test('the zone is required, and the sector too for a flow error', () => {
  assert.throws(() => parseNodeErrorRequest({ sector: 'MZ-1-012', tipo: 'SIN_CAUDAL' }), /macro-zona/);
  assert.throws(() => parseNodeErrorRequest({ zonaId: 'MZ-1', tipo: 'SIN_CAUDAL' }), /sector/);
  assert.throws(() => parseNodeErrorRequest({ zonaId: '  ', sector: 'MZ-1-012', tipo: 'SIN_CAUDAL' }), InvalidInput);
});

test('a flow that is not a number is rejected', () => {
  assert.throws(
    () => parseNodeErrorRequest({ zonaId: 'MZ-1', sector: 'MZ-1-012', tipo: 'SIN_CAUDAL', valor: 'mucho' }),
    InvalidInput,
  );
});

test('anything that is not an object is rejected', () => {
  assert.throws(() => parseNodeErrorRequest(null), InvalidInput);
  assert.throws(() => parseNodeErrorRequest('SIN_CAUDAL'), InvalidInput);
});
