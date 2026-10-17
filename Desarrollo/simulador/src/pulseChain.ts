/* ============================================================
   Chain of sensing pulses: asks the backend for N pulses, one after the other.

   The backend allows a single pulse at a time (a second request while one runs gets a 409), and a
   pulse lasts as long as its zones take to answer or give up. So each request waits for the
   previous pulse to end by asking again until it is accepted. Pure: the HTTP call and the wait are
   injected, so the logic is testable without a backend or real time.
   ============================================================ */

/** Milliseconds between two attempts while the previous pulse is still running. */
const DEFAULT_WAIT_MS = 5_000;

/** Attempts before giving up: 120 x 5 s = 10 minutes, well above the longest response window of a pulse. */
const DEFAULT_MAX_WAITS = 120;

export interface PulseChainDeps {
  /** Asks the backend for one pulse with the rule engine. Rejects with the backend message. */
  trigger: () => Promise<void>;
  sleep: (ms: number) => Promise<void>;
  waitMs?: number;
  maxWaits?: number;
  /** Called with the number of pulses accepted so far, as each one starts. */
  onPulse?: (accepted: number) => void;
}

/** `true` if the error says another pulse is still running (the backend answers 409 "en curso"). */
export function isPulseInProgress(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error);
  return message.includes('409') || message.toLowerCase().includes('en curso');
}

/**
 * Requests `count` pulses in sequence and returns how many were accepted. A pulse in course is
 * waited out; any other failure is thrown as is.
 */
export async function runPulses(count: number, deps: PulseChainDeps): Promise<number> {
  const waitMs = deps.waitMs ?? DEFAULT_WAIT_MS;
  const maxWaits = deps.maxWaits ?? DEFAULT_MAX_WAITS;
  let accepted = 0;
  while (accepted < count) {
    let waits = 0;
    for (;;) {
      try {
        await deps.trigger();
        break;
      } catch (e) {
        if (!isPulseInProgress(e)) throw e;
        if (++waits > maxWaits) {
          throw new Error('El pulso anterior no terminó a tiempo: no se pudo pedir el siguiente.');
        }
        await deps.sleep(waitMs);
      }
    }
    accepted++;
    deps.onPulse?.(accepted);
  }
  return accepted;
}
