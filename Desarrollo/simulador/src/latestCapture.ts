/* ============================================================
   Diagnóstico simulado sobre la última foto del sector.

   Pedir una orden de captura nueva compite con el celular real: si está conectado, el backend le entrega la orden a él
   y la subida del simulador se rechaza («entregada a otro dispositivo»). Un diagnóstico sólo exige que exista una
   captura (y una captura puede tener varios diagnósticos; el vigente del sector es el más reciente), así que se cuelga
   de la última foto del sector. Sólo si el sector no tiene ninguna se recurre al flujo de la orden.

   Módulo sin fetch: la búsqueda y el alta se inyectan en `LatestCaptureDeps`.
   ============================================================ */
import { captureIdOf, type DiagnosisCardRef } from './captureId';
import type { DiagnosisRequest } from './captureFlow';
import type { NewDiagnosis } from './types';

export interface OrderRef {
  sectorId: string;
  capturaId?: string | null;
  creadaEn?: number | null;
}

export interface CardRef extends DiagnosisCardRef {
  sectorId: string;
  creadoEn?: number | null;
}

/** Última captura del sector: la orden más nueva con captura; si ninguna, la tarjeta de diagnóstico más nueva con foto. */
export function pickLatestCaptureId(orders: OrderRef[], cards: CardRef[], sectorId: string): string | null {
  const order = orders
    .filter((o) => o.sectorId === sectorId && o.capturaId)
    .sort((a, b) => (b.creadaEn ?? 0) - (a.creadaEn ?? 0))[0];
  if (order?.capturaId) return order.capturaId;
  const card = cards
    .filter((c) => c.sectorId === sectorId && c.imagenUrl)
    .sort((a, b) => (b.creadoEn ?? 0) - (a.creadoEn ?? 0))[0];
  const id = captureIdOf(card);
  return id && id.startsWith('CAP-') ? id : null;
}

export interface LatestCaptureDeps {
  findLatestCaptureId(sectorId: string): Promise<string | null>;
  createDiagnosis(input: NewDiagnosis): Promise<{ id: string }>;
  registerWithNewCapture(request: DiagnosisRequest): Promise<{ id: string }>;
  log(line: string): void;
}

export async function registerOnLatestCapture(
  request: DiagnosisRequest,
  deps: LatestCaptureDeps,
): Promise<{ id: string }> {
  const capturaId = await deps.findLatestCaptureId(request.sectorId);
  if (!capturaId) {
    deps.log(
      `ℹ ${request.sectorId} no tiene fotos: se pide una captura nueva para colgar el diagnóstico simulado (necesita la cámara del simulador libre)`,
    );
    return deps.registerWithNewCapture(request);
  }
  const created = await deps.createDiagnosis({ ...request, capturaId });
  const severity = request.sev ? `, severidad ${request.sev}` : '';
  deps.log(
    `✓ Diagnóstico simulado (${request.estado}${severity}) registrado sobre la última foto de ${request.sectorId} (${capturaId})`,
  );
  return created;
}
