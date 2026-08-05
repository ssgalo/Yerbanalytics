/* ============================================================
   Almacenamiento local del dispositivo, sobre IndexedDB.

   Dos cosas viven acá:
     1. La credencial de renovación, obtenida al vincular. NO está en el código fuente: el
        bundle de una PWA es público, así que nada de larga duración se escribe en él.
     2. Las imágenes que no se pudieron entregar, para reintentarlas al volver la red.

   Se usa IndexedDB y no localStorage porque localStorage sólo guarda texto: un JPEG habría
   que pasarlo a base64, creciendo un 33% y chocando contra el límite de ~5 MB.

   El contrato exige que este almacén exista y esté ACOTADO, no que sea infalible: la
   garantía real de que ninguna orden se pierde es el watchdog del backend.
   ============================================================ */

const DB = 'yerbanalytics-camara';
const VERSION = 1;
const STORE_CRED = 'credencial';
const STORE_COLA = 'cola-envio';

/** Topes de la cola. Safari desaloja el storage sin avisar y una cola sin techo rompe la app. */
export const MAX_PENDIENTES = 50;
export const MAX_BYTES_COLA = 200 * 1024 * 1024;

export interface EnvioPendiente {
  ordenId: string;
  jpeg: Blob;
  meta: {
    ancho: number;
    alto: number;
    sha256: string;
    capturadaEn: number;
    constraints?: Record<string, unknown>;
  };
  bytes: number;
  guardadoEn: number;
  intentos: number;
}

let dbPromise: Promise<IDBDatabase> | null = null;

function abrir(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_CRED)) db.createObjectStore(STORE_CRED);
      if (!db.objectStoreNames.contains(STORE_COLA)) {
        db.createObjectStore(STORE_COLA, { keyPath: 'ordenId' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
  return dbPromise;
}

function tx<T>(store: string, modo: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>) {
  return abrir().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const t = db.transaction(store, modo);
        const req = fn(t.objectStore(store));
        req.onsuccess = () => resolve(req.result);
        req.onerror = () => reject(req.error);
      }),
  );
}

/**
 * Pide al navegador que no desaloje el storage. Es una solicitud, no una garantía: Safari
 * puede decir que no y borrar igual. Por eso la cola está acotada y el backend reintenta.
 */
export async function pedirPersistencia(): Promise<boolean> {
  if (!navigator.storage?.persist) return false;
  try {
    return await navigator.storage.persist();
  } catch {
    return false;
  }
}

// ------------------------------------------------------------------
// Credencial
// ------------------------------------------------------------------

export interface CredencialGuardada {
  dispositivoId: string;
  refreshToken: string;
  nombre: string;
}

export const guardarCredencial = (c: CredencialGuardada) =>
  tx(STORE_CRED, 'readwrite', (s) => s.put(c, 'actual')).then(() => undefined);

export const leerCredencial = () =>
  tx<CredencialGuardada | undefined>(STORE_CRED, 'readonly', (s) => s.get('actual'));

export const borrarCredencial = () =>
  tx(STORE_CRED, 'readwrite', (s) => s.delete('actual')).then(() => undefined);

// ------------------------------------------------------------------
// Cola de envío
// ------------------------------------------------------------------

export const listarPendientes = () =>
  tx<EnvioPendiente[]>(STORE_COLA, 'readonly', (s) => s.getAll() as IDBRequest<EnvioPendiente[]>);

export const quitarPendiente = (ordenId: string) =>
  tx(STORE_COLA, 'readwrite', (s) => s.delete(ordenId)).then(() => undefined);

export const contarPendientes = () =>
  tx<number>(STORE_COLA, 'readonly', (s) => s.count());

/**
 * Encola una imagen que no se pudo entregar. Al llegar al tope descarta las MÁS ANTIGUAS:
 * ante una elección forzada, la captura más reciente del riel vale más que una vieja, y de
 * la vieja el backend ya se va a enterar por vencimiento de su orden.
 *
 * Devuelve cuántas entradas se descartaron, para poder registrarlo en el log — un descarte
 * silencioso haría creer que todo se entregó.
 */
export async function encolarEnvio(envio: Omit<EnvioPendiente, 'guardadoEn'>): Promise<number> {
  const pendientes = await listarPendientes();
  const conNuevo = [...pendientes.filter((p) => p.ordenId !== envio.ordenId), { ...envio, guardadoEn: Date.now() }];
  conNuevo.sort((a, b) => a.guardadoEn - b.guardadoEn);

  let descartadas = 0;
  let bytes = conNuevo.reduce((t, p) => t + p.bytes, 0);
  while (conNuevo.length > MAX_PENDIENTES || bytes > MAX_BYTES_COLA) {
    const fuera = conNuevo.shift();
    if (!fuera) break;
    bytes -= fuera.bytes;
    await quitarPendiente(fuera.ordenId);
    descartadas++;
  }

  await tx(STORE_COLA, 'readwrite', (s) =>
    s.put({ ...envio, guardadoEn: Date.now() } as EnvioPendiente),
  );
  return descartadas;
}

export async function marcarIntento(ordenId: string): Promise<void> {
  const actual = await tx<EnvioPendiente | undefined>(STORE_COLA, 'readonly', (s) => s.get(ordenId));
  if (!actual) return;
  await tx(STORE_COLA, 'readwrite', (s) => s.put({ ...actual, intentos: actual.intentos + 1 }));
}
