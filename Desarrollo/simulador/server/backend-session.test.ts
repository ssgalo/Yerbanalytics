import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createBackendSession, readSessionCookie } from './backend-session.ts';

const BACKEND = 'http://backend.test';

interface Call {
  path: string;
  method: string;
  cookie: string | null;
  body: unknown;
}

/**
 * Minimal fake backend: issues `s1`, `s2`… on each sign-in and accepts only the latest one.
 * `expire()` invalidates the current session, as inactivity or a revocation would.
 */
function fakeBackend(options: { acceptLogin?: boolean; loginDelayMs?: number } = {}) {
  const calls: Call[] = [];
  let issued = 0;
  let valid: string | null = null;

  const fetch = (async (input: string | URL | Request, init: RequestInit = {}) => {
    const url = new URL(String(input));
    const headers = new Headers(init.headers);
    calls.push({
      path: url.pathname,
      method: init.method ?? 'GET',
      cookie: headers.get('cookie'),
      body: init.body,
    });

    if (url.pathname === '/api/auth/login') {
      if (options.loginDelayMs) await new Promise((r) => setTimeout(r, options.loginDelayMs));
      if (options.acceptLogin === false) {
        return Response.json({ error: 'Credenciales incorrectas' }, { status: 401 });
      }
      valid = `s${++issued}`;
      return Response.json(
        { username: 'sim', debeCambiarClave: false },
        { headers: { 'Set-Cookie': `YERBA_SESION=${valid}; Path=/api; HttpOnly; SameSite=Strict` } },
      );
    }
    if (valid && headers.get('cookie') === `YERBA_SESION=${valid}`) {
      return Response.json({ ok: true });
    }
    return Response.json({ error: 'Sin sesión', motivo: 'SIN_SESION' }, { status: 401 });
  }) as typeof globalThis.fetch;

  return {
    fetch,
    calls,
    logins: () => calls.filter((c) => c.path === '/api/auth/login').length,
    expire: () => {
      valid = null;
    },
  };
}

const quiet = () => undefined;

test('reads the session cookie from Set-Cookie', () => {
  const headers = new Headers();
  headers.append('Set-Cookie', 'OTRA=x; Path=/');
  headers.append('Set-Cookie', 'YERBA_SESION=abc123; Path=/api; HttpOnly');
  assert.equal(readSessionCookie(headers), 'abc123');
  assert.equal(readSessionCookie(new Headers()), null);
});

test('signs in lazily and sends the cookie with the credentials from the environment', async () => {
  const backend = fakeBackend();
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'clave-segura',
    fetch: backend.fetch,
    log: quiet,
  });
  assert.equal(session.state(), 'idle');

  const r = await session.request('/api/topologia');
  assert.equal(r.status, 200);
  assert.equal(session.state(), 'active');
  assert.deepEqual(JSON.parse(String(backend.calls[0].body)), {
    username: 'sim',
    clave: 'clave-segura',
  });
  assert.equal(backend.calls[1].cookie, 'YERBA_SESION=s1');

  await session.request('/api/topologia');
  assert.equal(backend.logins(), 1, 'a live session is reused, not renewed');
});

test('on a 401 it signs in again and retries exactly once, resending the same body', async () => {
  const backend = fakeBackend();
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'x',
    fetch: backend.fetch,
    log: quiet,
  });
  await session.request('/api/topologia');
  backend.expire();

  const body = new TextEncoder().encode('{"sectorId":"S-001"}');
  const r = await session.request('/api/capturas/ordenes', { method: 'POST', body });
  assert.equal(r.status, 200);
  assert.equal(backend.logins(), 2);
  const posts = backend.calls.filter((c) => c.path === '/api/capturas/ordenes');
  assert.equal(posts.length, 2);
  assert.equal(posts[1].cookie, 'YERBA_SESION=s2');
  assert.equal(posts[1].body, body);
});

test('a second 401 after signing in again goes back to the caller, with no more retries', async () => {
  const backend = fakeBackend();
  // Every request is rejected even with a fresh session (e.g. revoked between login and use).
  const alwaysRejects = (async (input: string | URL | Request, init?: RequestInit) => {
    const r = await backend.fetch(input, init);
    if (new URL(String(input)).pathname === '/api/auth/login') return r;
    return Response.json({ error: 'Sesión revocada', motivo: 'SESION_REVOCADA' }, { status: 401 });
  }) as typeof globalThis.fetch;
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'x',
    fetch: alwaysRejects,
    log: quiet,
  });

  const r = await session.request('/api/topologia');
  assert.equal(r.status, 401);
  assert.equal(backend.calls.filter((c) => c.path === '/api/topologia').length, 2);
  assert.equal(backend.logins(), 2, 'the lazy sign-in plus the single re-authentication');
});

test('concurrent requests collapse into a single sign-in', async () => {
  const backend = fakeBackend({ loginDelayMs: 20 });
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'x',
    fetch: backend.fetch,
    log: quiet,
  });

  const first = await Promise.all(Array.from({ length: 5 }, () => session.request('/api/nursery')));
  assert.ok(first.every((r) => r.status === 200));
  assert.equal(backend.logins(), 1);

  backend.expire();
  const second = await Promise.all(Array.from({ length: 5 }, () => session.request('/api/nursery')));
  assert.ok(second.every((r) => r.status === 200));
  assert.equal(backend.logins(), 2, 'five 401s at once open one new session, not five');
});

test('without credentials it never signs in, and requests go out without a cookie', async () => {
  const backend = fakeBackend();
  const session = createBackendSession({ backendUrl: BACKEND, fetch: backend.fetch, log: quiet });
  assert.equal(session.hasCredentials(), false);
  assert.equal(session.state(), 'no-credentials');

  const r = await session.request('/api/topologia');
  assert.equal(r.status, 401);
  assert.equal(backend.logins(), 0);
  assert.equal(backend.calls.length, 1);
  assert.equal(backend.calls[0].cookie, null);
});

test('rejected credentials: state "rejected" and no retry until the cooldown passes', async () => {
  const backend = fakeBackend({ acceptLogin: false });
  let clock = 0;
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'mala',
    fetch: backend.fetch,
    now: () => clock,
    log: quiet,
  });

  const r = await session.request('/api/topologia');
  assert.equal(r.status, 401);
  assert.equal(session.state(), 'rejected');
  assert.equal(backend.logins(), 1);

  await session.request('/api/topologia');
  assert.equal(backend.logins(), 1, 'the status probe does not hammer the login');

  clock += 60_000;
  await session.request('/api/topologia');
  assert.equal(backend.logins(), 2);
});

test('the browser cookie never reaches the backend: only the simulator session', async () => {
  const backend = fakeBackend();
  const session = createBackendSession({
    backendUrl: BACKEND,
    username: 'sim',
    password: 'x',
    fetch: backend.fetch,
    log: quiet,
  });
  await session.request('/api/topologia', { headers: { Cookie: 'YERBA_SESION=ajena' } });
  assert.equal(backend.calls[1].cookie, 'YERBA_SESION=s1');
});
