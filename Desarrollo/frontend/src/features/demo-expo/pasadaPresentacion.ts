/* ============================================================
   Textos de la pasada del riel. Funciones puras: qué decir de la pasada completa (el titular
   grande, pensado para leerse de lejos) y de cada paso. El `detalle` de un error lo escribe el
   backend; acá sólo se elige cuándo mostrarlo.
   ============================================================ */
import type { EstadoPasada, Pasada, PasoPasada } from '@/types/domain';

export const ETIQUETA_ESTADO: Record<EstadoPasada, string> = {
  EN_CURSO: 'En curso',
  COMPLETADA: 'Completada',
  FALLIDA: 'Falló',
  CANCELADA: 'Cancelada',
};

/** Las capturas que ya tienen foto archivada (las que la IA va a diagnosticar). */
const conFoto = (p: Pasada) => p.pasos.filter((s) => s.tipo === 'CAPTURAR' && s.capturaId !== null);

/** ¿Falta el diagnóstico de alguna foto? */
export function esperaDiagnosticos(p: Pasada): boolean {
  return conFoto(p).some((s) => s.diagnostico === null);
}

/** Sector al que apunta un movimiento: el de la captura que sigue en esa misma posición. */
function sectorDestino(p: Pasada, paso: PasoPasada): string | null {
  return p.pasos.find((s) => s.tipo === 'CAPTURAR' && s.posicion === paso.posicion)?.sectorId ?? null;
}

/** El titular de la pasada: qué está pasando AHORA, en una frase. */
export function titularPasada(p: Pasada): string {
  switch (p.estado) {
    case 'EN_CURSO': {
      if (p.cancelacionSolicitada) return 'Cancelación en curso: el riel vuelve a home…';
      const paso = p.pasos.find((s) => s.estado === 'EN_CURSO');
      if (!paso) return 'Preparando el siguiente paso…';
      if (paso.tipo === 'HOME') return 'El riel vuelve a home…';
      if (paso.tipo === 'MOVER') {
        const sector = sectorDestino(p, paso);
        return sector ? `El riel se está moviendo al sector ${sector}…` : `El riel se está moviendo a la posición ${paso.posicion}…`;
      }
      return paso.estadoOrden === 'PENDIENTE'
        ? `Esperando al celular para sacar la foto del sector ${paso.sectorId}…`
        : `Sacando la foto del sector ${paso.sectorId}…`;
    }
    case 'COMPLETADA': {
      const fotos = conFoto(p);
      if (fotos.length === 0) return 'Pasada completa';
      return esperaDiagnosticos(p)
        ? 'Pasada completa. Esperando el diagnóstico de la IA…'
        : `Pasada completa: la IA ya diagnosticó ${fotos.length === 1 ? 'la foto' : `las ${fotos.length} fotos`}`;
    }
    case 'FALLIDA':
      return 'La pasada falló';
    case 'CANCELADA':
      return 'Pasada cancelada: el riel volvió a home';
  }
}

export function tituloPaso(s: PasoPasada): string {
  switch (s.tipo) {
    case 'MOVER':
      return `Riel → posición ${s.posicion}`;
    case 'CAPTURAR':
      return `Foto del sector ${s.sectorId ?? '—'}`;
    case 'HOME':
      return 'Riel → home';
  }
}

/** La línea de estado de un paso. Los errores y omisiones muestran el detalle del backend. */
export function textoPaso(s: PasoPasada): string {
  switch (s.estado) {
    case 'PENDIENTE':
      return 'Pendiente';
    case 'ERROR':
      return s.detalle ?? 'Falló';
    case 'OMITIDO':
      return s.detalle ?? 'Omitido';
    case 'EN_CURSO':
      if (s.tipo === 'MOVER') return 'Moviendo…';
      if (s.tipo === 'HOME') return 'Volviendo a home…';
      if (s.estadoOrden === 'PENDIENTE') return 'Esperando al celular';
      if (s.estadoOrden === 'ENTREGADA') return 'El celular está sacando la foto';
      return 'Sacando la foto…';
    case 'OK':
      if (s.tipo === 'MOVER') return 'Llegó';
      if (s.tipo === 'HOME') return 'En home';
      return 'Foto recibida';
  }
}

/** Duración de un paso en segundos, o null si todavía no arrancó. Un paso en curso cuenta hasta `ahoraMs`. */
export function duracionPasoSeg(s: PasoPasada, ahoraMs: number): number | null {
  if (s.iniciadoEn === null) return null;
  const fin = s.terminadoEn ?? (s.estado === 'EN_CURSO' ? ahoraMs : null);
  return fin === null ? null : Math.max(0, Math.round((fin - s.iniciadoEn) / 1000));
}
