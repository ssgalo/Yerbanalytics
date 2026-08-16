/* ============================================================
   Proxy to the nursery backend.

   The simulator UI never calls the backend directly: it hits `/backend/**` on this same
   server and the request is forwarded from here. That way the backend receives server-to-
   server calls, with no CORS involved, and does NOT need to declare the simulator's origin
   among its allowed ones. That line in the backend configuration was the last trace of the
   simulator in the system.

   Side effect worth remembering: the backend returns a capture's image as a relative path
   (`/api/capturas/{id}/imagen`), so the UI has to prefix it with `/backend` or the browser
   looks for it on the page's own origin. That is handled in `src/api.ts`.
   ============================================================ */
import type { Request, Response } from 'express';
import { config } from './config.ts';

/* Headers not forwarded: `fetch` recomputes them, or they belong to the browser connection.

   `origin` and `referer` matter more than they look. The browser attaches `Origin` to every
   POST — even a same-origin one, as this is — and forwarding it would turn a server-to-server
   call into a CORS request in the backend's eyes, rejected with a 403 because the simulator's
   origin is not (and must not be) among the allowed ones. Stripping them here is what keeps
   the promise of the proxy: the backend sees a plain server call and stays unaware of who
   made it. */
const SKIPPED_HEADERS = new Set([
  'host',
  'connection',
  'content-length',
  'accept-encoding',
  'origin',
  'referer',
]);

/** Response headers worth passing back upstream. */
const FORWARDED_HEADERS = ['content-type', 'etag', 'cache-control', 'content-disposition'];

/** Forwards the request to the backend and returns its response as-is. */
export async function proxyBackend(req: Request, res: Response): Promise<void> {
  const target = `${config.backendUrl}${req.url}`;

  const headers: Record<string, string> = {};
  for (const [key, value] of Object.entries(req.headers)) {
    if (typeof value === 'string' && !SKIPPED_HEADERS.has(key.toLowerCase())) {
      headers[key] = value;
    }
  }

  const hasBody = req.method !== 'GET' && req.method !== 'HEAD';
  // `Uint8Array` rather than `Buffer`: the global `fetch` types reject Node's Buffer.
  const body =
    Buffer.isBuffer(req.body) && req.body.length > 0 ? new Uint8Array(req.body) : undefined;

  try {
    const response = await fetch(target, {
      method: req.method,
      headers,
      body: hasBody ? body : undefined,
    });

    for (const name of FORWARDED_HEADERS) {
      const value = response.headers.get(name);
      if (value) res.setHeader(name, value);
    }
    res.status(response.status);
    res.send(Buffer.from(await response.arrayBuffer()));
  } catch (e) {
    // A backend that is down is not a simulator error: it is reported as such so the UI can
    // tell it apart from a rejection by the backend. User-facing message, hence Spanish.
    res.status(502).json({
      error:
        `No se pudo alcanzar el backend en ${config.backendUrl} ` +
        `(${e instanceof Error ? e.message : String(e)}). Verificá que esté levantado.`,
    });
  }
}

/** One-off backend call from the simulator server. `null` when it is unavailable. */
export async function fetchFromBackend<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${config.backendUrl}${path}`);
    if (!response.ok) return null;
    return (await response.json()) as T;
  } catch {
    return null;
  }
}
