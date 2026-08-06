/* ============================================================
   Cliente del contrato de dispositivo de captura v1.

   Toda la comunicación con la plataforma pasa por acá, y usa EXCLUSIVAMENTE el namespace
   `/api/camara/v1`. Si alguna vez hace falta un endpoint fuera de ese namespace, el problema
   no es de este archivo: es que el contrato quedó corto.

   Fuente de verdad: Desarrollo/contratos/camara/v1/openapi.yaml
   ============================================================ */

/**
 * Base del backend.
 *
 * Por defecto se deduce del host desde el que se sirvió la app: si el iPhone abrió
 * `https://192.168.1.56:5190`, el backend es `https://192.168.1.56:8443`. Backend y app corren
 * en la misma máquina, así que el host siempre coincide.
 *
 * Deducirlo en vez de configurarlo importa para que el repo sea clonable: nadie tiene que
 * escribir su IP en ningún archivo, y cambiar de red no rompe nada. `VITE_API_BASE_URL` queda
 * como escape para los casos en que backend y app no compartan host.
 */
const PUERTO_BACKEND = import.meta.env.VITE_API_PORT || '8443';

function baseBackend(): string {
  const configurada = import.meta.env.VITE_API_BASE_URL;
  if (configurada) return configurada.replace(/\/$/, '');
  // Siempre https: el 8443 es el conector TLS del backend. El 8000 (HTTP) es para el
  // dashboard, y una página https no podría llamarlo igual.
  return `https://${globalThis.location.hostname}:${PUERTO_BACKEND}`;
}

const BASE = baseBackend();
const V1 = `${BASE}/api/camara/v1`;

/** Configuración de captura. La fija el backend, no el cliente. */
export interface ConfigCaptura {
  anchoMax: number;
  altoMax: number;
  calidadJpeg: number;
  warmupMs: number;
  heartbeatSeg: number;
  timeoutOrdenSeg: number;
  maxColaOrdenes: number;
}

/** Orden de captura tal como llega por el canal. */
export interface Orden {
  ordenId: string;
  sectorId: string;
  zonaId: string;
  posicionRiel: number;
  emitidaEn: number;
  venceEn: number;
  intento: number;
}

/** Conjunto cerrado del contrato. Un motivo fuera de acá lo rechaza el backend con 400. */
export type MotivoFallo =
  | 'CAMARA_NO_LISTA'
  | 'EXPORTACION_FALLIDA'
  | 'COLA_LLENA'
  | 'TIMEOUT_LOCAL'
  | 'ENVIO_AGOTADO'
  | 'ERROR_DESCONOCIDO';

export interface MetadataImagen {
  ancho: number;
  alto: number;
  sha256: string;
  capturadaEn: number;
  constraints?: Record<string, unknown>;
}

export interface CapturaCreada {
  capturaId: string;
  ordenId: string;
  imagenUrl: string;
}

export interface Credencial {
  dispositivoId: string;
  refreshToken: string;
}

/** El backend rechazó la credencial: hay que volver a vincular el dispositivo. */
export class CredencialRechazadaError extends Error {}

/** La orden ya tenía imagen. NO es un error: el contrato obliga a tratarlo como éxito. */
export class OrdenYaResueltaError extends Error {
  constructor(readonly captura: CapturaCreada) {
    super('La orden ya había recibido su imagen.');
  }
}

/** La orden no existe o no es de este dispositivo: reintentar no sirve de nada. */
export class OrdenNoEntregableError extends Error {}

export const streamUrl = (token: string) =>
  `${V1}/ordenes/stream?token=${encodeURIComponent(token)}`;

async function leerError(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { error?: string };
    return body?.error ?? `Error ${res.status}`;
  } catch {
    return `Error ${res.status}`;
  }
}

/** No se pudo llegar al backend. Distinto de que el backend haya respondido con un error. */
export class BackendInalcanzableError extends Error {}

/**
 * `fetch` con un mensaje de error que sirva de algo.
 *
 * Un fallo de red o de CORS llega como un `TypeError: Failed to fetch` idéntico en los dos
 * casos, y el navegador sólo cuenta el motivo real en la consola. Para alguien parado frente
 * al riel con el teléfono en la mano eso no alcanza, así que se traduce a las dos causas que
 * lo explican en la práctica.
 */
async function pedir(url: string, init?: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (e) {
    if (e instanceof TypeError) {
      throw new BackendInalcanzableError(
        `No se pudo contactar al backend en ${BASE}. Verificá que:\n` +
          `• esté levantado y accesible desde este dispositivo — si acá dice "localhost", ` +
          `cambiá VITE_API_BASE_URL por la IP de la máquina en la red del vivero;\n` +
          `• el origen de esta app (${location.origin}) esté permitido en ` +
          `"yerbanalytics.cors.origins" del backend.`,
      );
    }
    throw e;
  }
}

// ------------------------------------------------------------------
// Credenciales
// ------------------------------------------------------------------

export async function enrolar(
  codigo: string,
  nombre: string,
  plataforma: string,
): Promise<Credencial> {
  const res = await pedir(`${V1}/enrolar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ codigo, nombre, plataforma }),
  });
  if (res.status === 401) throw new CredencialRechazadaError(await leerError(res));
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as Credencial;
}

export async function obtenerToken(refreshToken: string): Promise<{
  accessToken: string;
  expiraEnSeg: number;
}> {
  const res = await pedir(`${V1}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (res.status === 401) throw new CredencialRechazadaError(await leerError(res));
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as { accessToken: string; expiraEnSeg: number };
}

// ------------------------------------------------------------------
// Operación
// ------------------------------------------------------------------

export async function obtenerConfig(token: string): Promise<ConfigCaptura> {
  const res = await pedir(`${V1}/config`, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 401) throw new CredencialRechazadaError('Token vencido.');
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as ConfigCaptura;
}

export async function heartbeat(
  token: string,
  estado: {
    capturaListo: boolean;
    capturasOk: number;
    capturasError: number;
    pendientesEnvio: number;
    detalle?: string;
  },
): Promise<void> {
  const res = await pedir(`${V1}/heartbeat`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(estado),
  });
  if (res.status === 401) throw new CredencialRechazadaError('Token vencido.');
  if (!res.ok) throw new Error(await leerError(res));
}

// ------------------------------------------------------------------
// Entrega de la captura
// ------------------------------------------------------------------

export async function subirImagen(
  token: string,
  ordenId: string,
  jpeg: Blob,
  meta: MetadataImagen,
): Promise<CapturaCreada> {
  const fd = new FormData();
  fd.append('imagen', jpeg, `${ordenId}.jpg`);
  fd.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }), 'meta.json');

  const res = await pedir(`${V1}/ordenes/${ordenId}/imagen`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: fd,
  });

  // El 409 significa que una subida anterior sí llegó y se perdió la respuesta. El contrato
  // obliga a tratarlo como éxito: si lo contáramos como fallo, un corte de red en el momento
  // justo produciría duplicados o errores fantasma.
  if (res.status === 409) {
    throw new OrdenYaResueltaError((await res.json()) as CapturaCreada);
  }
  if (res.status === 401) throw new CredencialRechazadaError('Token vencido.');
  // 404 (no existe) y 403 (es de otro dispositivo) no mejoran reintentando.
  if (res.status === 404 || res.status === 403) {
    throw new OrdenNoEntregableError(await leerError(res));
  }
  if (!res.ok) throw new Error(await leerError(res));
  return (await res.json()) as CapturaCreada;
}

export async function acusarFallo(
  token: string,
  ordenId: string,
  motivo: MotivoFallo,
  detalle?: string,
): Promise<void> {
  const res = await pedir(`${V1}/ordenes/${ordenId}/fallo`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ motivo, detalle }),
  });
  if (res.status === 401) throw new CredencialRechazadaError('Token vencido.');
  // Un 409 acá significa que la orden ya está resuelta: no hay nada que acusar.
  if (!res.ok && res.status !== 409) throw new Error(await leerError(res));
}
