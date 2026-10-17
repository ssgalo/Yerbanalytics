/* ============================================================
   Request to publish a node error (S-05), validated against the contract.

   Pure: no broker, no Express. The route in `api.ts` parses with this and publishes with `mqtt.ts`, so what a node error
   may look like is decided in one place (`contract.ts` holds the vocabulary, this file the rules for building one).

   Error messages are surfaced verbatim by the UI, so they are written in Spanish.
   ============================================================ */
import {
  NODE_ERROR_TYPES,
  SHADE_LIMIT_SWITCHES,
  type NodeErrorInput,
  type NodeErrorType,
  type ShadeLimitSwitch,
} from './contract.ts';
import { InvalidInput } from './store.ts';

/** Measured flow reported when the request names none, in L/h: nothing passes / far more than a valve lets through. */
const DEFAULT_FLOW_LPH: Record<'SIN_CAUDAL' | 'CAUDAL_EXCESIVO', number> = {
  SIN_CAUDAL: 0,
  CAUDAL_EXCESIVO: 180,
};

export interface ParsedNodeError {
  zonaId: string;
  input: NodeErrorInput;
}

const text = (value: unknown): string => (typeof value === 'string' ? value.trim() : '');

/** The error to publish, or an {@link InvalidInput} explaining what is wrong with the request. */
export function parseNodeErrorRequest(body: unknown): ParsedNodeError {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new InvalidInput('El cuerpo del error del nodo debe ser un objeto JSON.');
  }
  const request = body as Record<string, unknown>;

  const zonaId = text(request.zonaId);
  if (!zonaId) throw new InvalidInput('La macro-zona del nodo es obligatoria.');

  const tipo = text(request.tipo);
  if (!(NODE_ERROR_TYPES as readonly string[]).includes(tipo)) {
    throw new InvalidInput(`El tipo de error debe ser uno de: ${NODE_ERROR_TYPES.join(', ')}.`);
  }
  const type = tipo as NodeErrorType;
  const nodo = text(request.serial) || `SIM-${zonaId}`;

  if (type === 'MEDIASOMBRA_FIN_DE_CARRERA') {
    // The mesh is the macro-zone's: whatever sector came is dropped (`buildNodeError` empties it too).
    const valor = text(request.valor) || 'CERRADA';
    if (!(SHADE_LIMIT_SWITCHES as readonly string[]).includes(valor)) {
      throw new InvalidInput(`El final de carrera de la mediasombra debe ser uno de: ${SHADE_LIMIT_SWITCHES.join(', ')}.`);
    }
    return { zonaId, input: { nodo, sector: '', tipo: type, valor: valor as ShadeLimitSwitch } };
  }

  const sector = text(request.sector);
  if (!sector) throw new InvalidInput('El sector de la electroválvula es obligatorio para un error de caudal.');
  const valor = flow(request.valor, DEFAULT_FLOW_LPH[type]);
  return { zonaId, input: { nodo, sector, tipo: type, valor } };
}

function flow(value: unknown, fallback: number): number {
  if (value === undefined || value === null || value === '') return fallback;
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) throw new InvalidInput('El caudal medido debe ser un número mayor o igual a 0.');
  return n;
}
