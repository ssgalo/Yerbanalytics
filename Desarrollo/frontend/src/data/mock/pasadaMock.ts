/* ============================================================
   Simulación de la pasada del riel para la demo sin backend.

   `simularPasada` es una función PURA del tiempo: dado cuándo empezó la pasada y qué hora es,
   devuelve el estado completo que mandaría el backend. Así el mock "avanza solo" sin timers
   propios (el polling del hook la vuelve a pedir) y se puede probar en cualquier instante.

   Duraciones: mover 4 s · foto 3 s · mover 4 s · foto 3 s · home 6 s (20 s en total). Los
   diagnósticos llegan 5 s después de terminar (el servicio real tarda ~1 min).
   ============================================================ */
import type { DiagnosticoPaso, EstadoPaso, Pasada, PasoPasada, TipoPaso } from '@/types/domain';
import { capturasDemo } from './capturasDemo';

interface Plantilla {
  tipo: TipoPaso;
  posicion: number;
  sectorId: string | null;
  duracionMs: number;
  /** Sólo en CAPTURAR: diagnóstico que dará la IA de demostración. */
  foto?: { estado: string; conf: number; sev: string };
}

const PLAN: Plantilla[] = [
  { tipo: 'MOVER', posicion: 1, sectorId: null, duracionMs: 4000 },
  {
    tipo: 'CAPTURAR',
    posicion: 1,
    sectorId: 'MZ-1-001',
    duracionMs: 3000,
    foto: { estado: 'Clorosis', conf: 87, sev: 'Media' },
  },
  { tipo: 'MOVER', posicion: 2, sectorId: null, duracionMs: 4000 },
  {
    tipo: 'CAPTURAR',
    posicion: 2,
    sectorId: 'MZ-1-002',
    duracionMs: 3000,
    foto: { estado: 'Estrés solar', conf: 91, sev: 'Alta' },
  },
  { tipo: 'HOME', posicion: 0, sectorId: null, duracionMs: 6000 },
];

/** Cuánto después de terminar la pasada aparecen los diagnósticos. */
const DEMORA_DIAGNOSTICO_MS = 5000;
const DURACION_HOME_MS = PLAN[4].duracionMs;

export function simularPasada(
  inicioMs: number,
  ahoraMs: number,
  canceladaEnMs: number | null,
): Pasada {
  // Una cancelación posterior al final de la pasada no cambia nada.
  const finNatural = inicioMs + PLAN.reduce((t, p) => t + p.duracionMs, 0);
  const cancelo = canceladaEnMs !== null && canceladaEnMs >= inicioMs && canceladaEnMs < finNatural;
  const cancelEn = cancelo ? (canceladaEnMs as number) : null;

  // Ventana [ini, fin) de cada paso. Con cancelación, HOME arranca en ese momento (salvo que ya
  // estuviera en curso) y los pasos que no habían terminado se omiten.
  let cursor = inicioMs;
  const ventanas = PLAN.map((p) => {
    const ini = cursor;
    cursor += p.duracionMs;
    return { ini, fin: cursor };
  });
  const homeNatural = ventanas[4];
  const homeEnCurso = cancelEn !== null && cancelEn >= homeNatural.ini;
  if (cancelEn !== null && !homeEnCurso) {
    ventanas[4] = { ini: cancelEn, fin: cancelEn + DURACION_HOME_MS };
  }
  const finalizadaEn = ventanas[4].fin;
  const terminada = ahoraMs >= finalizadaEn;
  const diagnosticosListos = terminada && ahoraMs >= finalizadaEn + DEMORA_DIAGNOSTICO_MS;

  const pasos: PasoPasada[] = PLAN.map((plan, i) => {
    const n = i + 1;
    const { ini, fin } = ventanas[i];
    const esHome = plan.tipo === 'HOME';
    const omitido = cancelEn !== null && !esHome && fin > cancelEn;

    let estado: EstadoPaso;
    if (omitido) estado = 'OMITIDO';
    else if (ahoraMs >= fin) estado = 'OK';
    else if (ahoraMs >= ini) estado = 'EN_CURSO';
    else estado = 'PENDIENTE';

    const esCaptura = plan.tipo === 'CAPTURAR';
    const recibida = esCaptura && estado === 'OK';
    const foto = plan.foto;

    let diagnostico: DiagnosticoPaso | null = null;
    if (recibida && foto && diagnosticosListos) {
      diagnostico = { ...foto, creadoEn: finalizadaEn + DEMORA_DIAGNOSTICO_MS };
    }

    return {
      n,
      tipo: plan.tipo,
      posicion: plan.posicion,
      sectorId: plan.sectorId,
      estado,
      codigoError: null,
      detalle: omitido ? 'Cancelada por el operador' : null,
      commandId: !esCaptura && (estado === 'EN_CURSO' || estado === 'OK') ? `demo-cmd-${n}` : null,
      ordenId: esCaptura && (estado === 'EN_CURSO' || estado === 'OK') ? `demo-orden-${n}` : null,
      estadoOrden: esCaptura ? (estado === 'EN_CURSO' ? 'ENTREGADA' : recibida ? 'RECIBIDA' : null) : null,
      capturaId: recibida ? `CAP-DEMO-${n}` : null,
      imagenUrl: recibida && foto ? (capturasDemo[foto.estado]?.[0] ?? null) : null,
      diagnostico,
      iniciadoEn: estado === 'EN_CURSO' || estado === 'OK' ? ini : null,
      terminadoEn: estado === 'OK' ? fin : null,
    };
  });

  return {
    id: `demo-pasada-${inicioMs}`,
    estado: terminada ? (cancelEn !== null ? 'CANCELADA' : 'COMPLETADA') : 'EN_CURSO',
    iniciadaEn: inicioMs,
    finalizadaEn: terminada ? finalizadaEn : null,
    cancelacionSolicitada: cancelEn !== null && ahoraMs >= cancelEn,
    error: null,
    pasos,
  };
}
