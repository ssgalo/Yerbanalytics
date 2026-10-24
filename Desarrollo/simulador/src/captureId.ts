/* Qué captura originó una tarjeta de diagnóstico del snapshot del vivero.
   El snapshot trae el id del diagnóstico (`DX-…`) y la URL de la foto, pero `capturaId` suele venir vacío: la captura
   se lee de la URL (`/api/capturas/CAP-000058/imagen`). Sin captura reconocible queda el id del diagnóstico. */

export interface DiagnosisCardRef {
  id: string;
  capturaId?: string | null;
  imagenUrl?: string | null;
}

const CAPTURE_IN_URL = /\/capturas\/(CAP-[\w-]+)\//;

export function captureIdOf(card: DiagnosisCardRef | undefined): string | null {
  if (!card) return null;
  if (card.capturaId) return card.capturaId;
  const match = card.imagenUrl ? CAPTURE_IN_URL.exec(card.imagenUrl) : null;
  return match ? match[1] : card.id;
}
