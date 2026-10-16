/* ============================================================
   Criterio agronómico del diagnóstico: valores por defecto, validación y conteo de cambios.

   La validación es la misma que aplica el backend en `PUT /api/configuracion/diagnostico`
   (design D5): bandas enteras con 1 ≤ mínimo < media < alta, exactamente las cuatro clases
   anómalas del modelo, y una criticidad que sea una permutación de esas cuatro. La vista la usa
   para marcar los campos y bloquear el guardado; el mock, para rechazar igual que el backend.
   ============================================================ */
import type { DiagnosisCriteria, SeverityBand } from '@/types/domain';

/** Estados anómalos que emite el modelo, en el orden fijo en que el backend devuelve las bandas. */
export const MODEL_ANOMALOUS_STATES = ['Clorosis', 'Estrés solar', 'Plaga foliar', 'Daño fúngico'] as const;

/** Los mismos valores que siembra el backend en una base vacía (design D12). */
export const DEFAULT_DIAGNOSIS_CRITERIA: DiagnosisCriteria = {
  severityBands: [
    { state: 'Clorosis', minimum: 5, mediumFrom: 10, highFrom: 20 },
    { state: 'Estrés solar', minimum: 5, mediumFrom: 10, highFrom: 20 },
    { state: 'Plaga foliar', minimum: 2, mediumFrom: 5, highFrom: 10 },
    { state: 'Daño fúngico', minimum: 2, mediumFrom: 4, highFrom: 8 },
  ],
  criticality: ['Daño fúngico', 'Plaga foliar', 'Clorosis', 'Estrés solar'],
};

/** Copia nueva de los valores por defecto (el "restablecer" de la vista). */
export function defaultDiagnosisCriteria(): DiagnosisCriteria {
  return structuredClone(DEFAULT_DIAGNOSIS_CRITERIA);
}

export type BandField = 'minimum' | 'mediumFrom' | 'highFrom';

export const BAND_FIELDS: BandField[] = ['minimum', 'mediumFrom', 'highFrom'];

export const BAND_FIELD_LABEL: Record<BandField, string> = {
  minimum: 'Mínimo',
  mediumFrom: 'Media desde',
  highFrom: 'Alta desde',
};

/** Error de una banda: el campo que hay que marcar y por qué. */
export interface BandError {
  field: BandField;
  message: string;
}

const isPositiveInt = (v: number) => Number.isInteger(v) && v >= 1;

/** Errores de una banda; vacío si es válida. A lo sumo uno por campo. */
export function bandErrors(band: SeverityBand): BandError[] {
  const errors: BandError[] = [];
  for (const field of BAND_FIELDS) {
    const v = band[field];
    if (!Number.isFinite(v) || !Number.isInteger(v)) {
      errors.push({ field, message: `${BAND_FIELD_LABEL[field]}: tiene que ser un número entero de hojas.` });
    } else if (!isPositiveInt(v)) {
      errors.push({ field, message: `${BAND_FIELD_LABEL[field]}: tiene que ser al menos 1 hoja.` });
    }
  }
  if (errors.length > 0) return errors;
  if (band.minimum >= band.mediumFrom) {
    errors.push({ field: 'minimum', message: 'El mínimo tiene que ser menor que "media desde".' });
  }
  if (band.mediumFrom >= band.highFrom) {
    errors.push({ field: 'mediumFrom', message: '"Media desde" tiene que ser menor que "alta desde".' });
  }
  return errors;
}

/** Problema del orden de criticidad; `null` si es una permutación de los cuatro estados. */
export function criticalityError(criticality: string[]): string | null {
  const expected = new Set<string>(MODEL_ANOMALOUS_STATES);
  const got = new Set(criticality);
  if (
    criticality.length !== expected.size ||
    got.size !== criticality.length ||
    [...got].some((s) => !expected.has(s))
  ) {
    return 'El orden de criticidad tiene que incluir cada estado anómalo una sola vez.';
  }
  return null;
}

/** Todos los problemas del criterio, con mensajes aptos para mostrar; vacío si es válido. */
export function criteriaErrors(criteria: DiagnosisCriteria): string[] {
  const errors: string[] = [];
  const states = criteria.severityBands.map((b) => b.state);
  const expected: readonly string[] = MODEL_ANOMALOUS_STATES;
  if (states.length !== expected.length || expected.some((s) => !states.includes(s))) {
    errors.push(`Las bandas tienen que cubrir exactamente estos estados: ${expected.join(', ')}.`);
  }
  for (const band of criteria.severityBands) {
    for (const e of bandErrors(band)) errors.push(`${band.state} · ${e.message}`);
  }
  const crit = criticalityError(criteria.criticality);
  if (crit) errors.push(crit);
  return errors;
}

/** Cada campo de banda distinto cuenta como un cambio; reordenar la criticidad, como uno. */
export function countCriteriaChanges(saved: DiagnosisCriteria, draft: DiagnosisCriteria): number {
  let n = 0;
  for (const band of draft.severityBands) {
    const before = saved.severityBands.find((b) => b.state === band.state);
    n += before ? BAND_FIELDS.filter((f) => !Object.is(before[f], band[f])).length : 1;
  }
  const sameOrder =
    saved.criticality.length === draft.criticality.length &&
    saved.criticality.every((s, i) => s === draft.criticality[i]);
  return n + (sameOrder ? 0 : 1);
}

/** Mueve un estado una posición (−1 = más crítico, +1 = menos); fuera de rango no cambia nada. */
export function moveCriticality(criticality: string[], state: string, delta: -1 | 1): string[] {
  const i = criticality.indexOf(state);
  const j = i + delta;
  if (i < 0 || j < 0 || j >= criticality.length) return criticality;
  const next = [...criticality];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

/** Cómo se lee una banda válida: "Baja 5–9 · Media 10–19 · Alta 20 o más". */
export function bandSummary(band: SeverityBand): string {
  const range = (from: number, to: number) => (from === to ? `${from}` : `${from}–${to}`);
  return `Baja ${range(band.minimum, band.mediumFrom - 1)} · Media ${range(band.mediumFrom, band.highFrom - 1)} · Alta ${band.highFrom} o más`;
}
