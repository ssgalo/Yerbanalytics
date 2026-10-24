import assert from 'node:assert/strict';
import { test } from 'node:test';
import { captureIdOf } from './captureId';

test('takes the capture id from the image url of the diagnosis card', () => {
  assert.equal(
    captureIdOf({ id: 'DX-00037', imagenUrl: '/api/capturas/CAP-000058/imagen?v=33e8294f66d2af77' }),
    'CAP-000058',
  );
});

test('prefers an explicit capture id when the card has one', () => {
  assert.equal(
    captureIdOf({ id: 'DX-00037', capturaId: 'CAP-000099', imagenUrl: '/api/capturas/CAP-000058/imagen' }),
    'CAP-000099',
  );
});

test('falls back to the diagnosis id when the url has no capture', () => {
  assert.equal(captureIdOf({ id: 'DX-00037', imagenUrl: '/otra/ruta.jpg' }), 'DX-00037');
  assert.equal(captureIdOf({ id: 'DX-00037', imagenUrl: null }), 'DX-00037');
});

test('no card, no id', () => {
  assert.equal(captureIdOf(undefined), null);
});
