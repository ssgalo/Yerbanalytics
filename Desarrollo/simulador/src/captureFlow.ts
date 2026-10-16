/* ============================================================
   Capture flow driven by the simulator itself: order → photo → diagnosis.

   It plays both roles the real system splits between the capture device and the inference
   service, going through the same public endpoints they use (no simulator-only surface):
     1. enrolls once as a camera device and keeps its access token;
     2. emits a capture order for the sector;
     3. uploads a synthetic photo as that device;
     4. waits for the capture to be archived;
     5. registers the diagnosis against it.

   Every diagnosis is born from an image (`captura_id` is NOT NULL), so there is no shortcut
   past steps 2-4.
   ============================================================ */
import {
  createDiagnosis,
  emitCaptureOrder,
  enrollDevice,
  generatePairingCode,
  getCaptureOrder,
  getDeviceToken,
  uploadImage,
} from './api';
import type { NewDiagnosis, RegisteredDiagnosis } from './types';

const DEFAULT_RAIL_POSITION = 1200;
const CAPTURE_POLL_MS = 500;
const CAPTURE_TIMEOUT_MS = 15_000;

/** What the caller decides; the capture is resolved here. */
export type DiagnosisRequest = Omit<NewDiagnosis, 'capturaId'> & { sectorId: string };

let deviceToken: string | null = null;

/** Enrolls the simulated device on first use and reuses its token afterwards. */
export async function getDeviceAccessToken(): Promise<string> {
  if (!deviceToken) {
    const { codigo } = await generatePairingCode();
    const { refreshToken } = await enrollDevice(codigo);
    deviceToken = (await getDeviceToken(refreshToken)).accessToken;
  }
  return deviceToken;
}

/** A small solid-green JPEG: the backend needs an image, not a meaningful one. */
function syntheticPhoto(): Promise<File> {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  if (!ctx) return Promise.reject(new Error('No se pudo generar la imagen sintética.'));
  ctx.fillStyle = '#4c7a3c';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) =>
        blob
          ? resolve(new File([blob], 'simulated.jpg', { type: 'image/jpeg' }))
          : reject(new Error('No se pudo generar la imagen sintética.')),
      'image/jpeg',
    );
  });
}

/** Polls the order until the backend reports the archived capture's id. */
async function waitForCapture(ordenId: string): Promise<string> {
  const deadline = Date.now() + CAPTURE_TIMEOUT_MS;
  while (Date.now() < deadline) {
    const order = await getCaptureOrder(ordenId);
    if (order.capturaId) return order.capturaId;
    if (order.estado === 'ERROR') {
      throw new Error(order.motivoFallo ?? 'La orden de captura agotó sus reintentos.');
    }
    await new Promise((resolve) => setTimeout(resolve, CAPTURE_POLL_MS));
  }
  throw new Error('La captura no se archivó a tiempo; el diagnóstico no se registró.');
}

/** Runs the whole flow and returns the persisted diagnosis. */
export async function registerDiagnosisWithCapture(
  request: DiagnosisRequest,
): Promise<RegisteredDiagnosis> {
  const token = await getDeviceAccessToken();
  const order = await emitCaptureOrder(request.sectorId, DEFAULT_RAIL_POSITION);
  await uploadImage(order.ordenId, await syntheticPhoto(), token);
  const capturaId = await waitForCapture(order.ordenId);
  return createDiagnosis({ ...request, capturaId });
}
