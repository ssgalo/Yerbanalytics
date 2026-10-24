import assert from 'node:assert/strict';
import { test } from 'node:test';
import { pickLatestCaptureId, registerOnLatestCapture, type LatestCaptureDeps } from './latestCapture';

const order = (sectorId: string, capturaId: string | null, creadaEn: number) => ({ sectorId, capturaId, creadaEn });

test('takes the newest order of the sector that already has a capture', () => {
  const id = pickLatestCaptureId(
    [order('S1', 'CAP-1', 10), order('S1', 'CAP-3', 30), order('S2', 'CAP-9', 99), order('S1', null, 50)],
    [],
    'S1',
  );
  assert.equal(id, 'CAP-3');
});

test('falls back to the newest diagnosis card with a photo when no order has a capture', () => {
  const id = pickLatestCaptureId(
    [order('S1', null, 10)],
    [
      { id: 'DX-1', sectorId: 'S1', creadoEn: 5, imagenUrl: '/api/capturas/CAP-000010/imagen?v=1' },
      { id: 'DX-2', sectorId: 'S1', creadoEn: 8, imagenUrl: '/api/capturas/CAP-000011/imagen?v=2' },
      { id: 'DX-3', sectorId: 'S2', creadoEn: 9, imagenUrl: '/api/capturas/CAP-000012/imagen' },
      { id: 'DX-4', sectorId: 'S1', creadoEn: 20, imagenUrl: null },
    ],
    'S1',
  );
  assert.equal(id, 'CAP-000011');
});

test('returns null when the sector has no capture at all', () => {
  assert.equal(pickLatestCaptureId([order('S2', 'CAP-1', 1)], [{ id: 'DX-1', sectorId: 'S1', imagenUrl: '/otra.jpg' }], 'S1'), null);
});

const request = { sectorId: 'MZ-1-001', zonaId: 'MZ-1', estado: 'Clorosis', sev: 'Media', confidence: 'Alta', conf: 80 };

function deps(latest: string | null) {
  const calls: string[] = [];
  const d: LatestCaptureDeps = {
    findLatestCaptureId: async () => latest,
    createDiagnosis: async (input) => {
      calls.push(`create:${input.capturaId}`);
      return { id: 'DX-1' };
    },
    registerWithNewCapture: async () => {
      calls.push('new-capture');
      return { id: 'DX-2' };
    },
    log: (line) => calls.push(`log:${line}`),
  };
  return { d, calls };
}

test('hangs the simulated diagnosis on the latest capture, without emitting an order', async () => {
  const { d, calls } = deps('CAP-000056');
  const created = await registerOnLatestCapture(request, d);
  assert.equal(created.id, 'DX-1');
  assert.deepEqual(calls, [
    'create:CAP-000056',
    'log:✓ Diagnóstico simulado (Clorosis, severidad Media) registrado sobre la última foto de MZ-1-001 (CAP-000056)',
  ]);
});

test('without any capture it falls back to the order flow and says so', async () => {
  const { d, calls } = deps(null);
  const created = await registerOnLatestCapture(request, d);
  assert.equal(created.id, 'DX-2');
  assert.deepEqual(calls, [
    'log:ℹ MZ-1-001 no tiene fotos: se pide una captura nueva para colgar el diagnóstico simulado (necesita la cámara del simulador libre)',
    'new-capture',
  ]);
});
