/* ============================================================
   Textos de las secuencias de actuadores. Funciones puras, como `pasadaPresentacion`: qué decir
   de cada paso y cómo rotular la lectura de la zona. El `detalle` de un error lo escribe el
   backend; acá sólo se elige cuándo mostrarlo.
   ============================================================ */
import type { LecturaSecuencia, PasoSecuencia, Secuencia } from '@/types/domain';

export function tituloPasoSecuencia(s: PasoSecuencia, q: Secuencia): string {
  switch (s.tipo) {
    case 'ABRIR':
      return `Abrir la válvula de ${q.sectorId ?? '—'}`;
    case 'CERRAR':
      return 'Cerrar la válvula';
    case 'DESPLEGAR':
      return 'Desplegar la mediasombra';
    case 'ENROLLAR':
      return 'Enrollarla';
    case 'ESPERAR':
      return q.tipo === 'RIEGO'
        ? `Regar durante ${q.parametros.duracionSeg ?? '—'} s`
        : `Mantenerla desplegada ${q.parametros.esperaSeg ?? '—'} s`;
    case 'PEDIR':
      return `Pedir lectura a ${q.zonaId}`;
    case 'ESPERAR_TELEMETRIA':
      return 'Esperando la telemetría…';
    case 'MOSTRAR':
      return 'Lectura de la zona';
  }
}

/** Segundos que faltan hasta `esperaHasta`, redondeado para arriba; null si no hay espera. */
export function cuentaRegresivaSeg(esperaHasta: number | null, ahoraMs: number): number | null {
  if (esperaHasta === null) return null;
  return Math.max(0, Math.ceil((esperaHasta - ahoraMs) / 1000));
}

const EN_CURSO: Record<PasoSecuencia['tipo'], string> = {
  ABRIR: 'Abriendo…',
  ESPERAR: 'Esperando…',
  CERRAR: 'Cerrando…',
  DESPLEGAR: 'Desplegando…',
  ENROLLAR: 'Enrollando…',
  PEDIR: 'Pidiendo la lectura…',
  ESPERAR_TELEMETRIA: 'Esperando al nodo…',
  MOSTRAR: 'Preparando la lectura…',
};

const LISTO: Record<PasoSecuencia['tipo'], string> = {
  ABRIR: 'Válvula abierta',
  ESPERAR: 'Listo',
  CERRAR: 'Válvula cerrada',
  DESPLEGAR: 'Desplegada',
  ENROLLAR: 'Enrollada',
  PEDIR: 'Pedido enviado',
  ESPERAR_TELEMETRIA: 'Lectura recibida',
  MOSTRAR: 'Lista',
};

/** La línea de estado de un paso. Los errores y omisiones muestran el detalle del backend. */
export function textoPasoSecuencia(s: PasoSecuencia, ahoraMs: number): string {
  switch (s.estado) {
    case 'PENDIENTE':
      return 'Pendiente';
    case 'ERROR':
      return s.detalle ?? 'Falló';
    case 'OMITIDO':
      return s.detalle ?? 'Omitido';
    case 'EN_CURSO': {
      const faltan = s.tipo === 'ESPERAR' ? cuentaRegresivaSeg(s.esperaHasta, ahoraMs) : null;
      return faltan === null ? EN_CURSO[s.tipo] : `Faltan ${faltan} s`;
    }
    case 'OK':
      return LISTO[s.tipo];
  }
}

/** Duración de un paso en segundos, o null si todavía no arrancó. Un paso en curso cuenta hasta `ahoraMs`. */
export function duracionPasoSecuenciaSeg(s: PasoSecuencia, ahoraMs: number): number | null {
  if (s.iniciadoEn === null) return null;
  const fin = s.terminadoEn ?? (s.estado === 'EN_CURSO' ? ahoraMs : null);
  return fin === null ? null : Math.max(0, Math.round((fin - s.iniciadoEn) / 1000));
}

/* ---------- Lectura de la zona ---------- */

const METRICAS: { key: string; etiqueta: string; unidad: string }[] = [
  { key: 'humSus', etiqueta: 'Humedad de sustrato', unidad: '%' },
  { key: 'humAmb', etiqueta: 'Humedad ambiental', unidad: '%' },
  { key: 'temp', etiqueta: 'Temperatura del aire', unidad: '°C' },
  // `uv` es el % de luz de un LDR, no radiación UV: la unidad va en la etiqueta.
  { key: 'uv', etiqueta: 'Luz (%)', unidad: '' },
  { key: 'tempSuelo', etiqueta: 'Temperatura del sustrato', unidad: '°C' },
  { key: 'ce', etiqueta: 'Conductividad (CE)', unidad: 'dS/m' },
  { key: 'phSuelo', etiqueta: 'pH del sustrato', unidad: '' },
  { key: 'n', etiqueta: 'Nitrógeno (N)', unidad: 'mg/kg' },
  { key: 'p', etiqueta: 'Fósforo (P)', unidad: 'mg/kg' },
  { key: 'k', etiqueta: 'Potasio (K)', unidad: 'mg/kg' },
];

export interface FilaLectura {
  key: string;
  etiqueta: string;
  valor: string;
  unidad: string;
}

/** Hasta 2 decimales y coma decimal, sin depender del locale del entorno. */
const formatear = (v: number) => String(Math.round(v * 100) / 100).replace('.', ',');

/** Las métricas que el nodo sí reportó, en orden de pantalla; las nulas se descartan. */
export function filasLectura(lectura: LecturaSecuencia | null): FilaLectura[] {
  if (!lectura) return [];
  const conocidas = new Set(METRICAS.map((m) => m.key));
  const filas = METRICAS.filter((m) => lectura.metricas[m.key] != null).map((m) => ({
    key: m.key,
    etiqueta: m.etiqueta,
    valor: formatear(lectura.metricas[m.key] as number),
    unidad: m.unidad,
  }));
  const extras = Object.entries(lectura.metricas)
    .filter(([k, v]) => !conocidas.has(k) && v != null)
    .map(([k, v]) => ({ key: k, etiqueta: k, valor: formatear(v as number), unidad: '' }));
  return [...filas, ...extras];
}
