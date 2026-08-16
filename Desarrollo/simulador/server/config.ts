/* ============================================================
   Simulator configuration, read from the environment.

   Everything here belongs to the simulator: its own port, which backend it talks to and
   which broker it publishes to. The system has no counterpart for any of it — it does not
   know the simulator exists.
   ============================================================ */
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

/** Simulator project root (one level above `server/`). */
export const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/**
 * Loads `.env` into `process.env` without external dependencies. The format is deliberately
 * minimal: `KEY=value`, with `#` for comments. Variables already present in the environment
 * win, so a single value can be overridden without editing the file.
 */
function loadEnv(): void {
  let raw: string;
  try {
    raw = readFileSync(resolve(ROOT, '.env'), 'utf8');
  } catch {
    return; // Without `.env` the defaults below apply.
  }
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const split = trimmed.indexOf('=');
    if (split < 0) continue;
    const key = trimmed.slice(0, split).trim();
    const value = trimmed.slice(split + 1).trim();
    if (key && process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

loadEnv();

function str(key: string, fallback: string): string {
  const v = process.env[key];
  return v === undefined || v.trim() === '' ? fallback : v.trim();
}

function int(key: string, fallback: number): number {
  const n = Number(str(key, String(fallback)));
  return Number.isFinite(n) && n > 0 ? n : fallback;
}

export const config = {
  port: int('SIMULATOR_PORT', 5180),
  /** Nursery backend. No trailing slash, so routes can be concatenated safely. */
  backendUrl: str('BACKEND_URL', 'http://localhost:8000').replace(/\/+$/, ''),
  mqttUrl: str('MQTT_URL', 'mqtt://localhost:1883'),
  mqttClientId: str('MQTT_CLIENT_ID', 'yerbanalytics-simulator'),
  autoEmissionIntervalMs: int('AUTO_EMISSION_INTERVAL_MS', 10_000),
  /** Serve the `dist/` build instead of mounting Vite in middleware mode. */
  production: process.argv.includes('--prod'),
  /** Simulator state file. */
  dataFile: resolve(ROOT, 'data', 'simulator.json'),
};
