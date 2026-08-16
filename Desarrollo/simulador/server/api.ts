/* ============================================================
   Simulator-internal API. Only its own UI consumes it.

   None of this is system surface: neither the backend nor the dashboard knows about it. What
   IS system surface — topology, camera, diagnoses — does not go through here but through the
   proxy (`/backend/**`), because those are public endpoints the simulator consumes like any
   other client.

   Error messages are surfaced verbatim by the UI, so they are written in Spanish.
   ============================================================ */
import { Router, type RequestHandler } from 'express';
import { config } from './config.ts';
import {
  METRIC_KEYS,
  toContractUnits,
  type MetricKey,
  type TelemetryPayload,
} from './contract.ts';
import * as store from './store.ts';
import { InvalidInput } from './store.ts';
import * as emission from './emission.ts';
import { isConnected, publish } from './mqtt.ts';

export const api = Router();

/**
 * Wraps an async handler so its rejections reach the error middleware. Express 4 does not do
 * it on its own: without this, a `throw` inside an `async` leaves the request hanging and
 * surfaces as an unhandled rejection.
 */
function asyncRoute(handler: RequestHandler): RequestHandler {
  return (req, res, next) => {
    void Promise.resolve(handler(req, res, next)).catch(next);
  };
}

/** Optional number: `null`/empty means absent; a non-numeric string is an error. */
function optionalNumber(value: unknown, field: string): number | undefined {
  if (value === undefined || value === null || value === '') return undefined;
  const n = Number(value);
  if (!Number.isFinite(n)) throw new InvalidInput(`El valor de ${field} no es numérico.`);
  return n;
}

// ------------------------------------------------------------------
// Simulator status and that of its two external dependencies
// ------------------------------------------------------------------

api.get(
  '/status',
  asyncRoute(async (_req, res) => {
    // The backend is probed on every call: the UI needs to notice if it went down or came back.
    let backendUp = false;
    try {
      const r = await fetch(`${config.backendUrl}/api/topologia`);
      backendUp = r.ok;
    } catch {
      backendUp = false;
    }

    res.json({
      backend: { url: config.backendUrl, up: backendUp },
      broker: { url: config.mqttUrl, connected: isConnected() },
      emission: { active: emission.isActive(), intervalMs: emission.intervalMs() },
    });
  }),
);

// ------------------------------------------------------------------
// Simulated sensors
// ------------------------------------------------------------------

api.get('/sensors', (_req, res) => {
  res.json(store.listSensors());
});

api.post('/sensors', (req, res) => {
  const { serial, zonaId } = (req.body ?? {}) as { serial?: unknown; zonaId?: unknown };
  res.status(201).json(store.createSensor(serial, zonaId));
});

api.delete('/sensors/:serial', (req, res) => {
  store.deleteSensor(req.params.serial);
  res.status(204).end();
});

// ------------------------------------------------------------------
// Telemetry publishing
// ------------------------------------------------------------------

api.post(
  '/telemetry',
  asyncRoute(async (req, res) => {
    const body = (req.body ?? {}) as {
      serial?: unknown;
      zonaId?: unknown;
      battery?: unknown;
      signal?: unknown;
      timestamp?: unknown;
      metrics?: Record<string, unknown>;
    };

    const serial = typeof body.serial === 'string' ? body.serial.trim() : '';
    const zonaId = typeof body.zonaId === 'string' ? body.zonaId.trim() : '';
    if (!serial) throw new InvalidInput('El serial/MAC del sensor es obligatorio.');
    if (!zonaId) throw new InvalidInput('La macro-zona del sensor es obligatoria.');

    // Partial readings: every metric is optional, but at least one must be present. Absent
    // ones keep their last value in the nursery, so sending a single metric is the norm.
    const inUiUnits: Partial<Record<MetricKey, number>> = {};
    for (const key of METRIC_KEYS) {
      const value = optionalNumber(body.metrics?.[key], key);
      if (value !== undefined) inUiUnits[key] = value;
    }
    const metrics = toContractUnits(inUiUnits);
    if (Object.keys(metrics).length === 0) {
      throw new InvalidInput(`Enviá al menos una métrica (${METRIC_KEYS.join(', ')}).`);
    }

    const timestamp = optionalNumber(body.timestamp, 'timestamp') ?? Date.now();
    const payload: TelemetryPayload = {
      mac: serial,
      battery: optionalNumber(body.battery, 'batería') ?? null,
      signal: optionalNumber(body.signal, 'señal') ?? null,
      timestamp,
      metrics,
    };

    await publish(zonaId, payload);
    res.status(202).json({ zonaId, serial, timestamp, metrics: Object.keys(metrics) });
  }),
);

// ------------------------------------------------------------------
// Automatic emission
// ------------------------------------------------------------------

api.put('/emission', (req, res) => {
  const { active } = (req.body ?? {}) as { active?: unknown };
  if (typeof active !== 'boolean') {
    throw new InvalidInput('Indicá `active` como booleano.');
  }
  emission.setActive(active);
  res.json({ active: emission.isActive(), intervalMs: emission.intervalMs() });
});
