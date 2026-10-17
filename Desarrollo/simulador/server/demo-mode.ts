/* ============================================================
   Is the backend in demo mode?

   Same detection as the UI's demo controls: `GET /api/demo/rules` answers 200 only when
   `yerbanalytics.demo.enabled=true`; in real mode those routes do not exist (404). With demo mode
   the simulator publishes its telemetry on the separate demo topic; without it, on the regular one
   (it still stands in for hardware in setups with no demo mode).

   Cached for a few seconds, because it is asked on every publish. If the backend cannot be asked (down,
   5xx, no session) the last known answer is kept: a blip must not flip the topic and race the ESP32.
   Before the first answer it is "not available".
   ============================================================ */
import { backendSession } from './backend-session.ts';

export interface DemoModeProbeOptions {
  /** HTTP status of the demo probe. May throw when the backend is unreachable. */
  check: () => Promise<number>;
  now?: () => number;
  ttlMs?: number;
}

export interface DemoModeProbe {
  isAvailable(): Promise<boolean>;
}

export function createDemoModeProbe(options: DemoModeProbeOptions): DemoModeProbe {
  const now = options.now ?? Date.now;
  const ttlMs = options.ttlMs ?? 5000;
  let known = false;
  let checkedAt = Number.NEGATIVE_INFINITY;
  let pending: Promise<void> | null = null;

  async function refresh(): Promise<void> {
    try {
      const status = await options.check();
      if (status === 200) known = true;
      else if (status === 404) known = false;
      // Any other status (401, 5xx…): keep the last known answer.
    } catch {
      // Backend unreachable: keep the last known answer.
    }
    checkedAt = now();
  }

  return {
    async isAvailable() {
      if (now() - checkedAt >= ttlMs) {
        pending ??= refresh().finally(() => {
          pending = null;
        });
        await pending;
      }
      return known;
    },
  };
}

/** The simulator server's probe, over its own backend session. */
export const demoMode = createDemoModeProbe({
  check: async () => {
    const response = await backendSession.request('/api/demo/rules');
    await response.body?.cancel().catch(() => undefined);
    return response.status;
  },
});
