/* ============================================================
   The simulator's session with the backend.

   The backend demands a user session on every `/api/**` call. The simulator gets one like any
   other client: it signs in with a Servicio-role account whose credentials live in ITS `.env`
   (`BACKEND_USUARIO` / `BACKEND_CLAVE`), keeps the `YERBA_SESION` cookie in memory and sends it
   back as `Cookie:` on everything it forwards. The backend has no user, role or rule created for
   the simulator: the account is one more service account, created by the Administrator.

   Three behaviours worth knowing:
     - Sign-in is lazy: the first call that needs the backend triggers it.
     - A `401` means the session expired or was revoked (probes are GETs, and GETs do not count
       as activity, so it does expire). It signs in again and retries ONCE; a second `401` goes
       back to the caller as-is.
     - Concurrent sign-ins collapse into one shared promise: ten requests hitting an expired
       session open one new session, not ten.

   Without credentials it never tries to sign in. Requests still go out, without a cookie, so
   the backend rejects them and the status bar says why. MQTT publishing does not depend on
   any of this.
   ============================================================ */
import { config } from './config.ts';

/** Session cookie name, fixed by the backend. */
export const SESSION_COOKIE = 'YERBA_SESION';

/**
 * After the backend rejects the credentials, how long to wait before trying them again. The
 * status probe runs every 5 s; without this pause every probe would be a failed sign-in.
 */
const REJECTED_COOLDOWN_MS = 30_000;

export type SessionState =
  /** `BACKEND_USUARIO` / `BACKEND_CLAVE` missing: it never signs in. */
  | 'no-credentials'
  /** Credentials present, not signed in yet. */
  | 'idle'
  | 'active'
  /** The backend answered `401` to the sign-in. */
  | 'rejected'
  /** Sign-in failed for another reason (backend down, 5xx, response with no cookie). */
  | 'error';

export interface BackendSessionOptions {
  backendUrl: string;
  username?: string;
  password?: string;
  /** Injectable for tests. */
  fetch?: typeof fetch;
  now?: () => number;
  log?: (message: string) => void;
}

export interface BackendSession {
  /**
   * Calls the backend with the session attached. `init.body` must be re-sendable (string or
   * bytes, never a stream): on a `401` the same request goes out a second time.
   */
  request(path: string, init?: RequestInit): Promise<Response>;
  state(): SessionState;
  hasCredentials(): boolean;
}

/** Extracts the session cookie value from a sign-in response, or `null`. */
export function readSessionCookie(headers: Headers): string | null {
  // `getSetCookie` keeps each Set-Cookie apart; `get` joins them with ", " (older runtimes).
  const all =
    typeof headers.getSetCookie === 'function'
      ? headers.getSetCookie()
      : [headers.get('set-cookie') ?? ''];
  for (const line of all) {
    const match = new RegExp(`(?:^|,\\s*)${SESSION_COOKIE}=([^;,]*)`).exec(line);
    if (match && match[1]) return match[1];
  }
  return null;
}

export function createBackendSession(options: BackendSessionOptions): BackendSession {
  const doFetch = options.fetch ?? fetch;
  const now = options.now ?? Date.now;
  const log = options.log ?? ((message: string) => console.warn(`[simulator] ${message}`));
  const { backendUrl, username = '', password = '' } = options;
  const credentials = username !== '' && password !== '';

  let cookie: string | null = null;
  let state: SessionState = credentials ? 'idle' : 'no-credentials';
  let rejectedAt = Number.NEGATIVE_INFINITY;
  /** The sign-in in flight, shared by everyone who needs it. */
  let pending: Promise<string | null> | null = null;

  async function signIn(): Promise<string | null> {
    cookie = null;
    let response: Response;
    try {
      response = await doFetch(`${backendUrl}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, clave: password }),
      });
    } catch (e) {
      state = 'error';
      throw e; // Backend down: the caller turns it into its own 502.
    }

    if (response.status === 401) {
      state = 'rejected';
      rejectedAt = now();
      log(`el backend rechazó las credenciales de "${username}" (BACKEND_USUARIO/BACKEND_CLAVE).`);
      return null;
    }
    const value = response.ok ? readSessionCookie(response.headers) : null;
    if (!value) {
      state = 'error';
      log(`no se pudo iniciar sesión en el backend (HTTP ${response.status}).`);
      return null;
    }

    const profile = (await response.json().catch(() => null)) as {
      debeCambiarClave?: boolean;
    } | null;
    if (profile?.debeCambiarClave) {
      // Accounts are created with a temporary password; until it is changed the backend
      // answers 403 to everything. The simulator does not change it on its own.
      log(
        `la cuenta "${username}" tiene clave temporal: entrá una vez al dashboard con ella, ` +
          'cambiala y poné la nueva en BACKEND_CLAVE.',
      );
    }
    cookie = value;
    state = 'active';
    return value;
  }

  /** Signs in, or joins the sign-in already in flight. `null` when there is no session to use. */
  function login(): Promise<string | null> {
    if (!credentials) return Promise.resolve(null);
    if (pending) return pending;
    if (state === 'rejected' && now() - rejectedAt < REJECTED_COOLDOWN_MS) {
      return Promise.resolve(null);
    }
    pending = signIn().finally(() => {
      pending = null;
    });
    return pending;
  }

  function send(path: string, init: RequestInit, session: string | null): Promise<Response> {
    const headers = new Headers(init.headers);
    if (session) headers.set('Cookie', `${SESSION_COOKIE}=${session}`);
    else headers.delete('Cookie');
    return doFetch(`${backendUrl}${path}`, { ...init, headers });
  }

  async function request(path: string, init: RequestInit = {}): Promise<Response> {
    const used = cookie ?? (await login());
    const first = await send(path, init, used);
    if (first.status !== 401 || !credentials) return first;

    // Expired or revoked. If another request already signed in again in the meantime, its
    // session is reused instead of opening yet another one.
    const fresh = cookie !== null && cookie !== used ? cookie : await login();
    if (!fresh || fresh === used) return first;

    await first.body?.cancel().catch(() => undefined);
    return send(path, init, fresh); // A single retry: whatever comes back is final.
  }

  return {
    request,
    state: () => state,
    hasCredentials: () => credentials,
  };
}

/** The simulator server's session, with the credentials from its `.env`. */
export const backendSession = createBackendSession({
  backendUrl: config.backendUrl,
  username: config.backendUser,
  password: config.backendPassword,
});
