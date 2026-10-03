/* ============================================================
   Lectura humana de una traza: cómo se escribe "recibido vs. umbral" y qué pasó de un vistazo.
   Funciones puras; el DAG y el panel sólo las muestran.
   ============================================================ */
import { unidadSegunValor } from '@/lib/plural';
import type {
  Comparacion,
  DagSchema,
  OperadorComparacion,
  OrigenEvaluacion,
  ResultadoComparacion,
  TrazaEvaluacion,
} from '@/types/domain';

const SIMBOLOS: Record<OperadorComparacion, string> = { LT: '<', LE: '≤', GT: '>', GE: '≥', EQ: '=' };

export const simboloOperador = (op: OperadorComparacion): string => SIMBOLOS[op];

export const etiquetaOrigen = (o: OrigenEvaluacion): string => (o === 'TELEMETRIA' ? 'Telemetría' : 'Barrido');

/** Un valor de la traza como texto: números redondeados, booleanos en español, texto tal cual. */
function valorTexto(v: Comparacion['recibido'], unidad: string): string {
  if (v === null) return 'sin dato';
  if (typeof v === 'boolean') return v ? 'sí' : 'no';
  if (typeof v === 'string') return v;
  const n = String(Number(v.toFixed(2)));
  return unidad ? `${n} ${unidadSegunValor(unidad, Number(n))}` : n;
}

const MARCAS: Record<ResultadoComparacion, string> = { CUMPLE: '✓', NO_CUMPLE: '✗', SIN_DATO: '?' };

export interface ComparacionFormateada {
  recibido: string;
  operador: string;
  umbral: string;
  marca: string;
  resultado: ResultadoComparacion;
}

/** "38 %  <  42 %  ✓": las tres partes por separado para poder dar estilo a cada una. */
export function formatoComparacion(c: Comparacion): ComparacionFormateada {
  return {
    recibido: valorTexto(c.recibido, c.unidad),
    operador: simboloOperador(c.operador),
    umbral: valorTexto(c.umbral, c.unidad),
    marca: MARCAS[c.resultado],
    resultado: c.resultado,
  };
}

export type TipoLineaResumen = 'accion' | 'bloqueo' | 'pospuso' | 'omitida' | 'noAlcanzada' | 'error' | 'nada';

export interface LineaResumen {
  tipo: TipoLineaResumen;
  texto: string;
  ruleId?: string;
}

const QUE_HACE: Record<string, string> = {
  ACTIVAR_VALVULA: 'abrió la electroválvula de riego',
  ACTIVAR_BOMBA: 'dosificó insumo con la bomba peristáltica',
  MOVER_MEDIASOMBRA: 'movió la mediasombra',
};

const QUE_CORTA: Record<string, string> = {
  ABORT_RIEGO: 'la rama de riego',
  ABORT_INSUMO: 'la rama de insumos',
  ABORT_ALL: 'todo el motor',
};

/** Qué pasó en la evaluación, de un vistazo: qué accionó, qué se pospuso, qué se bloqueó y por quién. */
export function resumenTraza(schema: DagSchema, traza: TrazaEvaluacion): LineaResumen[] {
  const nombre = (id: string) => schema.nodes.find((n) => n.id === id)?.label ?? id;
  const lineas: LineaResumen[] = [];

  for (const r of traza.reglas) {
    if (r.estado === 'ERROR') {
      lineas.push({ tipo: 'error', ruleId: r.ruleId, texto: `${nombre(r.ruleId)} falló: ${r.error ?? 'error desconocido'}.` });
    }
    for (const a of r.acciones) {
      if (QUE_HACE[a.tipo]) {
        lineas.push({ tipo: 'accion', ruleId: r.ruleId, texto: `${nombre(r.ruleId)} ${QUE_HACE[a.tipo]}.` });
      } else if (a.tipo === 'POSTPONE_RIEGO') {
        lineas.push({ tipo: 'pospuso', ruleId: r.ruleId, texto: `${nombre(r.ruleId)} pospuso el riego.` });
      } else if (QUE_CORTA[a.tipo]) {
        lineas.push({ tipo: 'bloqueo', ruleId: r.ruleId, texto: `${nombre(r.ruleId)} bloqueó ${QUE_CORTA[a.tipo]}.` });
      }
    }
  }

  // Las omitidas se agrupan por quién cortó: "A, B omitidas por C".
  const porCulpable = new Map<string, string[]>();
  for (const r of traza.reglas.filter((x) => x.estado === 'OMITIDA_RAMA_BLOQUEADA' && x.bloqueadaPor)) {
    porCulpable.set(r.bloqueadaPor!, [...(porCulpable.get(r.bloqueadaPor!) ?? []), nombre(r.ruleId)]);
  }
  for (const [culpable, omitidas] of porCulpable) {
    lineas.push({
      tipo: 'omitida',
      ruleId: culpable,
      texto: `${omitidas.join(', ')} ${omitidas.length === 1 ? 'quedó omitida' : 'quedaron omitidas'} por ${nombre(culpable)}.`,
    });
  }

  const noAlcanzadas = traza.reglas.filter((r) => r.estado === 'NO_ALCANZADA');
  if (noAlcanzadas.length > 0) {
    lineas.push({ tipo: 'noAlcanzada', texto: `${noAlcanzadas.length} reglas no se evaluaron: el motor se detuvo antes.` });
  }

  return lineas.length > 0
    ? lineas
    : [{ tipo: 'nada', texto: 'Ninguna regla actuó ni bloqueó: no hubo nada que hacer.' }];
}
