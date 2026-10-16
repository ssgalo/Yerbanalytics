/* ============================================================
   Simulator HTTP client. Two destinations, with different meanings:

     `/api/**`      Simulator-internal API (sensors, MQTT publishing, automatic emission).
                    Not system surface: it is born and dies with this folder.

     `/backend/**`  The platform's PUBLIC API, through this same server's proxy. None of
                    these endpoints belongs to the simulator: emitting a capture order is
                    what the rail pass planner will do, and registering a diagnosis is what
                    the inference service will do. That is why the round trip rehearsed here
                    is literally the one that will run on its own.

   No Repository pattern like the dashboard's: the simulator has no two sources to choose
   between — it always talks to the real system.

   Error messages come from the other side already written for the operator, so they surface
   in Spanish; fallbacks match.
   ============================================================ */
import type {
  CameraDevice,
  CaptureOrder,
  DemoRule,
  DemoRuleUpdate,
  NewDiagnosis,
  NuevoHardware,
  NurseryTopology,
  PairingCode,
  Pasada,
  PasoPasada,
  ParametroRegla,
  ParametrosSecuencia,
  RegisteredDiagnosis,
  Secuencia,
  SectorRef,
  SimulatedSensor,
  SimulatedWeather,
  SimulatorStatus,
  TelemetryInput,
  TipoSecuencia,
} from './types';

/** Proxy prefix. The UI never learns the backend's real address. */
const BACKEND = '/backend';

/**
 * Throws with the message the other side returned, not a bare status code. Both the backend
 * and the internal API answer with `{ error }`.
 */
async function check(res: Response, action: string): Promise<Response> {
  if (res.ok) return res;
  const body = (await res.json().catch(() => null)) as { error?: string } | null;
  throw new Error(body?.error ?? `Error ${res.status} al ${action}.`);
}

async function json<T>(res: Response, action: string): Promise<T> {
  return (await (await check(res, action)).json()) as T;
}

/**
 * The backend returns a capture's image as a relative path (`/api/capturas/…`) because it
 * does not know its own public URL. Without this prefix the browser would look for it on the
 * simulator's origin and the photo would not appear: a silent 404, with the card falling back
 * to its placeholder as if the diagnosis had no image.
 */
function absolutizeImage<T extends { imagenUrl?: string | null }>(item: T): T {
  if (!item.imagenUrl || /^(https?:|data:|blob:)/.test(item.imagenUrl)) return item;
  return { ...item, imagenUrl: `${BACKEND}${item.imagenUrl}` };
}

/* ----------------------------------------------------------------
   Simulator-internal API
   ---------------------------------------------------------------- */

export async function getStatus(): Promise<SimulatorStatus> {
  return json(await fetch('/api/status'), 'consultar el estado del simulador');
}

export async function getSensors(): Promise<SimulatedSensor[]> {
  return json(await fetch('/api/sensors'), 'listar los sensores simulados');
}

export async function createSensor(sensor: SimulatedSensor): Promise<SimulatedSensor> {
  const res = await fetch('/api/sensors', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sensor),
  });
  return json(res, 'dar de alta el sensor simulado');
}

export async function deleteSensor(serial: string): Promise<void> {
  const res = await fetch(`/api/sensors/${encodeURIComponent(serial)}`, { method: 'DELETE' });
  await check(res, 'eliminar el sensor simulado');
}

export async function sendTelemetry(input: TelemetryInput): Promise<void> {
  const res = await fetch('/api/telemetry', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  await check(res, 'publicar la lectura');
}

export async function setAutoEmission(active: boolean): Promise<void> {
  const res = await fetch('/api/emission', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ active }),
  });
  await check(res, 'cambiar la emisión automática');
}

/* ----------------------------------------------------------------
   The platform's public API (through the proxy)
   ---------------------------------------------------------------- */

export async function getTopology(): Promise<NurseryTopology> {
  return json(await fetch(`${BACKEND}/api/topologia`), 'consultar la topología');
}

export async function generateTopology(
  macroZonas: number,
  sectoresPorMacroZona: number,
): Promise<NurseryTopology> {
  const res = await fetch(`${BACKEND}/api/topologia`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ macroZonas, sectoresPorMacroZona, regenerar: true }),
  });
  return json(res, 'regenerar la topología');
}

/** Sectors of the current topology, to choose where to capture. */
export async function getSectors(): Promise<SectorRef[]> {
  const data = await json<{ sectors?: SectorRef[] }>(
    await fetch(`${BACKEND}/api/nursery?t=${Date.now()}`),
    'consultar los sectores del vivero',
  );
  return (data.sectors ?? []).map((s) => ({ id: s.id, zonaName: s.zonaName }));
}

export async function getCameraDevices(): Promise<CameraDevice[]> {
  return json(
    await fetch(`${BACKEND}/api/camara/dispositivos?t=${Date.now()}`),
    'consultar los dispositivos de cámara',
  );
}

export async function generatePairingCode(): Promise<PairingCode> {
  const res = await fetch(`${BACKEND}/api/camara/vinculacion`, { method: 'POST' });
  return json(res, 'generar el código de vinculación');
}

/** Same endpoint the rail pass planner will use. */
export async function emitCaptureOrder(
  sectorId: string,
  posicionRiel: number,
): Promise<CaptureOrder> {
  const res = await fetch(`${BACKEND}/api/capturas/ordenes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sectorId, posicionRiel }),
  });
  return absolutizeImage(await json<CaptureOrder>(res, 'pedir la captura'));
}

export async function getCaptureOrder(ordenId: string): Promise<CaptureOrder> {
  const res = await fetch(`${BACKEND}/api/capturas/ordenes/${encodeURIComponent(ordenId)}`);
  return absolutizeImage(await json<CaptureOrder>(res, 'consultar la orden de captura'));
}

export async function createDiagnosis(input: NewDiagnosis): Promise<RegisteredDiagnosis> {
  const res = await fetch(`${BACKEND}/api/diagnosticos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  return absolutizeImage(await json<RegisteredDiagnosis>(res, 'registrar el diagnóstico'));
}

/* ----------------------------------------------------------------
   Camera Device API (Simulating the physical device)
   ---------------------------------------------------------------- */

export async function enrollDevice(codigo: string): Promise<{ refreshToken: string; dispositivoId: string }> {
  const res = await fetch(`${BACKEND}/api/camara/v1/enrolar`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      codigo,
      nombre: 'Cámara Simulada Web',
      plataforma: 'web-simulator',
    }),
  });
  return json(res, 'enrolar el dispositivo simulado');
}

export async function getDeviceToken(refreshToken: string): Promise<{ accessToken: string }> {
  const res = await fetch(`${BACKEND}/api/camara/v1/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  return json(res, 'obtener el token de acceso del dispositivo');
}

/* ----------------------------------------------------------------
   Demo mode — rule overrides and simulated weather (through proxy)
   ---------------------------------------------------------------- */

export async function getDemoRules(): Promise<DemoRule[]> {
  return json(await fetch(`${BACKEND}/api/demo/rules`), 'listar reglas del modo demo');
}

export async function updateDemoRule(name: string, update: DemoRuleUpdate): Promise<DemoRule> {
  const res = await fetch(`${BACKEND}/api/demo/rules/${encodeURIComponent(name)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(update),
  });
  return json(res, 'actualizar la regla');
}

export async function zeroDemoCooldowns(): Promise<DemoRule[]> {
  const res = await fetch(`${BACKEND}/api/demo/rules/cooldowns/zero`, { method: 'PUT' });
  return json(res, 'poner todos los cooldowns en 0');
}

export async function getSimulatedWeather(): Promise<SimulatedWeather | null> {
  const res = await fetch(`${BACKEND}/api/demo/weather`);
  if (res.status === 204) return null;
  return json(res, 'consultar el clima simulado');
}

export async function setSimulatedWeather(weather: SimulatedWeather): Promise<SimulatedWeather> {
  const res = await fetch(`${BACKEND}/api/demo/weather`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(weather),
  });
  return json(res, 'configurar el clima simulado');
}

export async function clearSimulatedWeather(): Promise<void> {
  const res = await fetch(`${BACKEND}/api/demo/weather`, { method: 'DELETE' });
  await check(res, 'eliminar el clima simulado');
}

/** Triggers a manual sensing pulse with the rule engine. 409 if one is already in progress. */
export async function triggerSensingPulse(ejecutarMotor: boolean): Promise<void> {
  const res = await fetch(`${BACKEND}/api/pulsos`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ejecutarMotor }),
  });
  await check(res, 'disparar el pulso de sensado');
}

/* ----------------------------------------------------------------
   Pasadas del riel (Demo Expo)
   ---------------------------------------------------------------- */

function absolutizePasada(pasada: Pasada): Pasada {
  return {
    ...pasada,
    pasos: pasada.pasos.map((paso) => absolutizePaso(paso)),
  };
}

function absolutizePaso(paso: PasoPasada): PasoPasada {
  return absolutizeImage(paso) as PasoPasada;
}

export async function getPasadaActual(): Promise<Pasada | null> {
  const res = await fetch(`${BACKEND}/api/pasadas/actual`);
  if (res.status === 204) return null;
  return absolutizePasada(await json<Pasada>(res, 'consultar la pasada actual'));
}

export async function iniciarPasada(): Promise<Pasada> {
  const res = await fetch(`${BACKEND}/api/pasadas`, { method: 'POST' });
  return absolutizePasada(await json<Pasada>(res, 'iniciar la pasada'));
}

export async function cancelarPasada(): Promise<Pasada> {
  const res = await fetch(`${BACKEND}/api/pasadas/actual/cancelar`, { method: 'POST' });
  return absolutizePasada(await json<Pasada>(res, 'cancelar la pasada'));
}

/* ----------------------------------------------------------------
   Secuencias de actuadores (Demo Expo)
   ---------------------------------------------------------------- */

export async function getSecuenciaActual(): Promise<Secuencia | null> {
  const res = await fetch(`${BACKEND}/api/secuencias/actual`);
  if (res.status === 204) return null;
  return json<Secuencia>(res, 'consultar la secuencia actual');
}

export async function iniciarSecuencia(
  tipo: TipoSecuencia,
  parametros?: ParametrosSecuencia,
): Promise<Secuencia> {
  const res = await fetch(`${BACKEND}/api/secuencias`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ tipo, parametros: parametros ?? {} }),
  });
  return json<Secuencia>(res, 'iniciar la secuencia');
}

export async function cancelarSecuencia(): Promise<Secuencia> {
  const res = await fetch(`${BACKEND}/api/secuencias/actual/cancelar`, { method: 'POST' });
  return json<Secuencia>(res, 'cancelar la secuencia');
}

/* ----------------------------------------------------------------
   Rule parameters catalog
   ---------------------------------------------------------------- */

export async function getParametrosCatalogo(): Promise<ParametroRegla[]> {
  const data = await json<{ parametros?: ParametroRegla[] } | ParametroRegla[]>(
    await fetch(`${BACKEND}/api/rules/parametros`),
    'consultar los parámetros del motor',
  );
  return Array.isArray(data) ? data : ((data as { parametros?: ParametroRegla[] }).parametros ?? []);
}

/* ----------------------------------------------------------------
   Hardware registration
   ---------------------------------------------------------------- */

export async function registrarHardware(device: NuevoHardware): Promise<void> {
  const res = await fetch(`${BACKEND}/api/hardware`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(device),
  });
  await check(res, 'registrar el hardware en el backend');
}

export async function uploadImage(ordenId: string, file: File, token: string): Promise<void> {
  const arrayBuffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  const sha256 = hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');

  const formData = new FormData();
  formData.append('imagen', file);
  
  const meta = {
    ancho: 1920,
    alto: 1080,
    sha256,
    capturadaEn: Date.now(),
  };
  formData.append('meta', JSON.stringify(meta));

  const res = await fetch(`${BACKEND}/api/camara/v1/ordenes/${encodeURIComponent(ordenId)}/imagen`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData,
  });
  await check(res, 'subir la imagen capturada');
}

