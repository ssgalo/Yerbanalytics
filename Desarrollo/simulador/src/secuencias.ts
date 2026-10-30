/* ============================================================
   Textos de las secuencias directas de Demo Expo (lógica pura).
   Riego y dosis comparten el guion (ABRIR, ESPERAR, CERRAR): sólo cambia el actuador, válvula o bomba de insumo.
   ============================================================ */
import type { PasoSecuencia, SectorRef, Secuencia, TipoPasoSecuencia } from './types';

export const RIEGO = { min: 1, max: 120, defecto: 10 };
export const ESPERA = { min: 0, max: 600, defecto: 10 };
/** Tope del firmware por comando (ContratoNodo.BOMBA_ML_MAX). Por defecto poco: es líquido de verdad sobre plantas. */
export const DOSIS = { min: 1, max: 50, defecto: 5 };

/**
 * El segundo sector de la zona de destino de las secuencias (la de menor número, orden natural: MZ-2 antes que MZ-10, el mismo
 * criterio del backend). El primero es el que riega solo la secuencia sin `sectorId`. `null` si no hay un segundo.
 */
export function secondSectorOfDestination(sectors: SectorRef[]): string | null {
  const natural = (a: string, b: string) => a.localeCompare(b, undefined, { numeric: true });
  const zonaId = [...new Set(sectors.map((s) => s.zonaId))].sort(natural)[0];
  if (zonaId === undefined) return null;
  const ids = sectors.filter((s) => s.zonaId === zonaId).map((s) => s.id).sort(natural);
  return ids[1] ?? null;
}

const EN_CURSO_TEXTO: Record<TipoPasoSecuencia, string> = {
  ABRIR: 'Abriendo…',
  ESPERAR: 'Esperando…',
  CERRAR: 'Cerrando…',
  DESPLEGAR: 'Desplegando…',
  ENROLLAR: 'Enrollando…',
  PEDIR: 'Pidiendo la lectura…',
  ESPERAR_TELEMETRIA: 'Esperando al nodo…',
  MOSTRAR: 'Preparando la lectura…',
};

const LISTO_TEXTO: Record<TipoPasoSecuencia, string> = {
  ABRIR: 'Válvula abierta',
  ESPERAR: 'Listo',
  CERRAR: 'Válvula cerrada',
  DESPLEGAR: 'Desplegada',
  ENROLLAR: 'Enrollada',
  PEDIR: 'Pedido enviado',
  ESPERAR_TELEMETRIA: 'Lectura recibida',
  MOSTRAR: 'Lista',
};

/** Lo que cambia en la dosis: los pasos del actuador nombran la bomba, no la válvula. */
const EN_CURSO_DOSIS: Partial<Record<TipoPasoSecuencia, string>> = {
  ABRIR: 'Prendiendo la bomba…',
  CERRAR: 'Apagando la bomba…',
};
const LISTO_DOSIS: Partial<Record<TipoPasoSecuencia, string>> = {
  ABRIR: 'Bomba encendida',
  CERRAR: 'Bomba apagada',
};

export function etiquetaPaso(paso: PasoSecuencia, sec: Secuencia): string {
  const dosis = sec.tipo === 'DOSIS';
  switch (paso.tipo) {
    case 'ABRIR':
      return dosis ? `Prender la bomba de insumo (${sec.sectorId ?? '—'})` : `Abrir válvula (${sec.sectorId ?? '—'})`;
    case 'CERRAR':
      return dosis ? 'Apagar la bomba' : 'Cerrar válvula';
    case 'DESPLEGAR':
      return sec.tipo === 'MEDIASOMBRA_CERRAR' ? 'Cerrar mediasombra (desplegar)' : 'Desplegar mediasombra';
    case 'ENROLLAR':
      return sec.tipo === 'MEDIASOMBRA_ABRIR' ? 'Abrir mediasombra (enrollar)' : 'Enrollar mediasombra';
    case 'ESPERAR':
      if (dosis) return `Dosificar ${sec.parametros.ml ?? '—'} ml`;
      return sec.tipo === 'RIEGO'
        ? `Regar ${sec.parametros.duracionSeg ?? '—'} s`
        : `Mantener desplegada ${sec.parametros.esperaSeg ?? '—'} s`;
    case 'PEDIR':
      return sec.parametros.ejecutarMotor ? `Pedir lectura a ${sec.zonaId} + motor` : `Pedir lectura a ${sec.zonaId}`;
    case 'ESPERAR_TELEMETRIA':
      return 'Esperando telemetría…';
    case 'MOSTRAR':
      return 'Mostrar lectura';
  }
}

export function textoPaso(paso: PasoSecuencia, sec: Secuencia, ahora: number = Date.now()): string {
  const dosis = sec.tipo === 'DOSIS';
  switch (paso.estado) {
    case 'PENDIENTE':
      return 'Pendiente';
    case 'ERROR':
      return paso.detalle ?? 'Falló';
    case 'OMITIDO':
      return paso.detalle ?? 'Omitido';
    case 'EN_CURSO': {
      if (paso.tipo === 'ESPERAR' && paso.esperaHasta) {
        const faltan = Math.max(0, Math.ceil((paso.esperaHasta - ahora) / 1000));
        return faltan === 0 ? 'Esperando confirmación del nodo…' : `Faltan ${faltan} s`;
      }
      return (dosis && EN_CURSO_DOSIS[paso.tipo]) || EN_CURSO_TEXTO[paso.tipo];
    }
    case 'OK':
      return (dosis && LISTO_DOSIS[paso.tipo]) || LISTO_TEXTO[paso.tipo];
  }
}
