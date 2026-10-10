/* ============================================================
   Simulación de las secuencias de actuadores para la demo sin backend.

   Igual que `simularPasada`, `simularSecuencia` es una función PURA del tiempo: dado cuándo
   empezó y qué hora es, devuelve el estado completo que mandaría el backend. El mock "avanza
   solo" sin timers (el polling del hook la vuelve a pedir) y se prueba en cualquier instante.

   Duraciones: ABRIR/CERRAR 1 s · DESPLEGAR/ENROLLAR 4 s · ESPERAR = parámetro ·
   PEDIR 0,5 s · ESPERAR_TELEMETRIA 2 s · MOSTRAR instantáneo.

   Cancelar, como en el backend: RIEGO y MEDIASOMBRA omiten lo que falta y corren igual el paso
   seguro (cerrar la válvula / enrollar la mediasombra); LECTURA no tiene nada que dejar seguro
   y queda cancelada al instante.
   ============================================================ */
import type {
  EstadoPaso,
  ParametrosSecuencia,
  PasoSecuencia,
  Secuencia,
  TipoPasoSecuencia,
  TipoSecuencia,
} from '@/types/domain';

/** Destino fijo de la demo, igual que la topología 1x2 del stand. */
const ZONA_DEMO = 'MZ-1';
const SECTOR_DEMO = 'MZ-1-001';

const DEFAULT_DURACION_SEG = 10;
const DEFAULT_ESPERA_SEG = 10;

/** Lectura de demostración, por si nadie pasa la de la zona del mock. */
export const LECTURA_DEMO: Record<string, number | null> = {
  humSus: 41,
  humAmb: 63,
  temp: 22.5,
  tempSuelo: 19.8,
  uv: 78,
  ce: 1.2,
  phSuelo: 6.1,
  n: 24,
  p: 18,
  k: 150,
};

interface Plantilla {
  tipo: TipoPasoSecuencia;
  duracionMs: number;
}

function plan(tipo: TipoSecuencia, p: ParametrosSecuencia): Plantilla[] {
  switch (tipo) {
    case 'RIEGO':
      return [
        { tipo: 'ABRIR', duracionMs: 1000 },
        { tipo: 'ESPERAR', duracionMs: (p.duracionSeg ?? DEFAULT_DURACION_SEG) * 1000 },
        { tipo: 'CERRAR', duracionMs: 1000 },
      ];
    case 'MEDIASOMBRA':
      return [
        { tipo: 'DESPLEGAR', duracionMs: 4000 },
        { tipo: 'ESPERAR', duracionMs: (p.esperaSeg ?? DEFAULT_ESPERA_SEG) * 1000 },
        { tipo: 'ENROLLAR', duracionMs: 4000 },
      ];
    case 'LECTURA':
      return [
        { tipo: 'PEDIR', duracionMs: 500 },
        { tipo: 'ESPERAR_TELEMETRIA', duracionMs: 2000 },
        { tipo: 'MOSTRAR', duracionMs: 0 },
      ];
  }
}

const PASO_SEGURO = 2; // índice: CERRAR / ENROLLAR

export function simularSecuencia(
  tipo: TipoSecuencia,
  parametros: ParametrosSecuencia,
  inicioMs: number,
  ahoraMs: number,
  canceladaEnMs: number | null,
  lecturaDemo: Record<string, number | null> = LECTURA_DEMO,
): Secuencia {
  const plantillas = plan(tipo, parametros);
  const esLectura = tipo === 'LECTURA';

  let cursor = inicioMs;
  const ventanas = plantillas.map((p) => {
    const ini = cursor;
    cursor += p.duracionMs;
    return { ini, fin: cursor };
  });
  const finNatural = cursor;
  // Una cancelación posterior al final natural no cambia nada.
  const cancelEn =
    canceladaEnMs !== null &&
    canceladaEnMs >= inicioMs &&
    canceladaEnMs <= ahoraMs &&
    canceladaEnMs < finNatural
      ? canceladaEnMs
      : null;

  // Pasos que no llegaron a terminar cuando se canceló.
  const omitido = (i: number) => {
    if (cancelEn === null) return false;
    if (esLectura) return ventanas[i].fin > cancelEn;
    // El paso seguro corre siempre: si ya estaba en curso no se toca; si no, arranca ahora.
    return i !== PASO_SEGURO && ventanas[i].fin > cancelEn;
  };
  if (cancelEn !== null && !esLectura && cancelEn < ventanas[PASO_SEGURO].ini) {
    const dur = plantillas[PASO_SEGURO].duracionMs;
    ventanas[PASO_SEGURO] = { ini: cancelEn, fin: cancelEn + dur };
  }

  const finalizadaEn = cancelEn !== null && esLectura ? cancelEn : ventanas[PASO_SEGURO].fin;
  const terminada = ahoraMs >= finalizadaEn;
  const cancelada = cancelEn !== null;

  const pasos: PasoSecuencia[] = plantillas.map((pl, i) => {
    const n = i + 1;
    const { ini, fin } = ventanas[i];
    const omite = omitido(i);

    let estado: EstadoPaso;
    if (omite) estado = 'OMITIDO';
    else if (ahoraMs >= fin) estado = 'OK';
    else if (ahoraMs >= ini) estado = 'EN_CURSO';
    else estado = 'PENDIENTE';

    const arrancado = estado === 'EN_CURSO' || estado === 'OK';
    const esComando =
      pl.tipo !== 'ESPERAR' && pl.tipo !== 'ESPERAR_TELEMETRIA' && pl.tipo !== 'MOSTRAR';
    // Un paso omitido que ya había arrancado conserva su inicio (como el backend).
    const habiaArrancado = omite && ini < (cancelEn ?? 0);

    return {
      n,
      tipo: pl.tipo,
      estado,
      codigoError: null,
      detalle: omite ? 'Cancelada por el operador' : null,
      commandId: esComando && arrancado ? `demo-cmd-${n}` : null,
      esperaHasta: pl.tipo === 'ESPERAR' && (arrancado || habiaArrancado) ? fin : null,
      iniciadoEn: arrancado || habiaArrancado ? ini : null,
      terminadoEn: estado === 'OK' ? fin : null,
    };
  });

  const lecturaRecibidaEn = ventanas[1].fin;
  const hayLectura = esLectura && !cancelada && ahoraMs >= lecturaRecibidaEn;

  return {
    id: `demo-secuencia-${tipo.toLowerCase()}-${inicioMs}`,
    tipo,
    estado: terminada ? (cancelada ? 'CANCELADA' : 'COMPLETADA') : 'EN_CURSO',
    zonaId: ZONA_DEMO,
    sectorId: esLectura ? null : SECTOR_DEMO,
    parametros: {
      duracionSeg: tipo === 'RIEGO' ? (parametros.duracionSeg ?? DEFAULT_DURACION_SEG) : null,
      esperaSeg: tipo === 'MEDIASOMBRA' ? (parametros.esperaSeg ?? DEFAULT_ESPERA_SEG) : null,
    },
    iniciadaEn: inicioMs,
    finalizadaEn: terminada ? finalizadaEn : null,
    cancelacionSolicitada: cancelada,
    error: null,
    lectura: hayLectura ? { recibidaEn: lecturaRecibidaEn, metricas: { ...lecturaDemo } } : null,
    pasos,
  };
}
