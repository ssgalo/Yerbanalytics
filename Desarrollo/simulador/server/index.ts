/* ============================================================
   Simulator entry point: one process, one port.

   It serves three things on the same origin:
     1. the UI       — Vite in middleware mode (dev) or the `dist/` build (prod);
     2. its own API  — `/api/**`, sensors and telemetry publishing;
     3. the proxy    — `/backend/**` to the nursery backend.

   It starts separately from the system, with its own command. Neither the backend nor the
   dashboard launches or needs it: if this process is not running, the nursery simply waits
   for telemetry from real hardware, exactly as in production.
   ============================================================ */
import express, { type NextFunction, type Request, type Response } from 'express';
import { config, ROOT } from './config.ts';
import { api } from './api.ts';
import { InvalidInput } from './store.ts';
import * as emission from './emission.ts';
import { connect, disconnect, PublishFailed } from './mqtt.ts';
import { proxyBackend } from './proxy.ts';

const app = express();

// ------------------------------------------------------------------
// Backend proxy. It goes FIRST and with the raw body: if `express.json()` consumed it, the
// backend would receive an empty one. `express.raw` with type '*/*' also covers uploads.
// ------------------------------------------------------------------
app.use('/backend', express.raw({ type: '*/*', limit: '25mb' }), proxyBackend);

// ------------------------------------------------------------------
// Simulator-internal API
// ------------------------------------------------------------------
app.use('/api', express.json(), api);

/** Internal API errors mapped to HTTP codes. The UI shows `error` verbatim. */
app.use('/api', (err: unknown, _req: Request, res: Response, next: NextFunction) => {
  if (res.headersSent) return next(err);
  if (err instanceof InvalidInput) {
    return res.status(400).json({ error: err.message });
  }
  if (err instanceof PublishFailed) {
    return res.status(502).json({ error: err.message });
  }
  console.error('[simulator] unexpected error:', err);
  return res.status(500).json({ error: err instanceof Error ? err.message : String(err) });
});

// ------------------------------------------------------------------
// UI
// ------------------------------------------------------------------
async function mountUI(): Promise<void> {
  if (config.production) {
    const dist = `${ROOT}/dist`;
    app.use(express.static(dist));
    // SPA: anything not resolved above returns the index.
    app.get('*', (_req, res) => res.sendFile(`${dist}/index.html`));
    return;
  }

  // Vite in middleware mode: same process and same port as the API, with no second server to
  // coordinate and no proxy from one to the other.
  const { createServer } = await import('vite');
  const vite = await createServer({
    root: ROOT,
    appType: 'spa',
    server: { middlewareMode: true },
  });
  app.use(vite.middlewares);
}

async function start(): Promise<void> {
  await mountUI();
  connect();

  const server = app.listen(config.port, () => {
    console.log(`\n  Yerbanalytics simulator`);
    console.log(`  UI        http://localhost:${config.port}`);
    console.log(`  Backend   ${config.backendUrl}  (via /backend)`);
    console.log(`  Broker    ${config.mqttUrl}`);
    console.log(`  Auto emission off — turn it on from the UI\n`);
  });

  const shutdown = async () => {
    emission.stop();
    server.close();
    await disconnect();
    process.exit(0);
  };
  process.on('SIGINT', () => void shutdown());
  process.on('SIGTERM', () => void shutdown());
}

void start();
