/*
 * Service worker de la app de cámara.
 *
 * REGLA CENTRAL: cachea el shell y NADA MÁS.
 *
 * Un service worker que cachee una orden de captura o una subida de imagen es una fuente de
 * bugs silenciosos: serviría una orden vieja como si fuera nueva, o daría por subida una
 * imagen que nunca salió del teléfono. Como esos fallos no rompen nada visiblemente, se
 * descubren tarde y mal.
 *
 * Por eso la exclusión es explícita y por path, y NUNCA se interceptan peticiones que no
 * sean GET.
 */

const CACHE = 'yerbanalytics-camara-v1';

/** Lo único cacheable: el esqueleto de la app. */
const SHELL = ['/', '/index.html', '/manifest.json', '/icono-192.png', '/icono-512.png'];

/** Nada bajo estos prefijos se cachea ni se sirve desde caché. */
const NUNCA_CACHEAR = ['/api/'];

self.addEventListener('install', (evento) => {
  evento.waitUntil(
    caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (evento) => {
  evento.waitUntil(
    caches
      .keys()
      .then((claves) => Promise.all(claves.filter((c) => c !== CACHE).map((c) => caches.delete(c))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (evento) => {
  const req = evento.request;

  // Las escrituras (subida de imagen, acuse, heartbeat, enrolamiento) no se tocan.
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // Otro origen: el backend puede estar en otro host. No es nuestro shell.
  if (url.origin !== self.location.origin) return;

  // API y stream de órdenes: siempre a la red, sin caché ni fallback.
  if (NUNCA_CACHEAR.some((prefijo) => url.pathname.startsWith(prefijo))) return;

  // El stream SSE tampoco, por si alguna vez se sirve desde el mismo origen.
  if (req.headers.get('accept') === 'text/event-stream') return;

  evento.respondWith(
    caches.match(req).then((cacheada) => {
      if (cacheada) return cacheada;
      return fetch(req)
        .then((res) => {
          // Sólo se guardan respuestas propias y exitosas del shell.
          if (res.ok && res.type === 'basic') {
            const copia = res.clone();
            caches.open(CACHE).then((cache) => cache.put(req, copia));
          }
          return res;
        })
        .catch(() => caches.match('/index.html'));
    }),
  );
});
