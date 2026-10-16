import { describe, expect, it } from 'vitest';
import type { DiagnosisCriteria } from '@/types/domain';
import {
  bandErrors,
  bandSummary,
  countCriteriaChanges,
  criteriaErrors,
  criticalityError,
  DEFAULT_DIAGNOSIS_CRITERIA,
  defaultDiagnosisCriteria,
  moveCriticality,
} from './diagnosisCriteria';

const conBanda = (state: string, patch: object): DiagnosisCriteria => {
  const c = defaultDiagnosisCriteria();
  c.severityBands = c.severityBands.map((b) => (b.state === state ? { ...b, ...patch } : b));
  return c;
};

describe('criterio de diagnóstico · valores por defecto', () => {
  it('son los que siembra el backend (design D12)', () => {
    expect(DEFAULT_DIAGNOSIS_CRITERIA).toEqual({
      severityBands: [
        { state: 'Clorosis', minimum: 5, mediumFrom: 10, highFrom: 20 },
        { state: 'Estrés solar', minimum: 5, mediumFrom: 10, highFrom: 20 },
        { state: 'Plaga foliar', minimum: 2, mediumFrom: 5, highFrom: 10 },
        { state: 'Daño fúngico', minimum: 2, mediumFrom: 4, highFrom: 8 },
      ],
      criticality: ['Daño fúngico', 'Plaga foliar', 'Clorosis', 'Estrés solar'],
    });
    expect(criteriaErrors(DEFAULT_DIAGNOSIS_CRITERIA)).toEqual([]);
  });

  it('cada restablecimiento es una copia nueva', () => {
    const a = defaultDiagnosisCriteria();
    a.severityBands[0].minimum = 99;
    expect(DEFAULT_DIAGNOSIS_CRITERIA.severityBands[0].minimum).toBe(5);
  });
});

describe('criterio de diagnóstico · validación (las mismas reglas que el backend)', () => {
  it('rechaza bandas no crecientes y marca el campo culpable', () => {
    // Escenario de la spec: Clorosis con mínimo 10 y media 5
    const c = conBanda('Clorosis', { minimum: 10, mediumFrom: 5 });
    expect(bandErrors(c.severityBands[0]).map((e) => e.field)).toEqual(['minimum']);
    expect(criteriaErrors(c)[0]).toMatch(/^Clorosis · /);

    expect(bandErrors({ state: 'X', minimum: 2, mediumFrom: 4, highFrom: 4 }).map((e) => e.field)).toEqual([
      'mediumFrom',
    ]);
    // Igual también es inválido: tiene que ser estrictamente menor
    expect(bandErrors({ state: 'X', minimum: 4, mediumFrom: 4, highFrom: 8 })[0].field).toBe('minimum');
  });

  it('exige enteros desde 1 (un campo vacío llega como NaN)', () => {
    expect(bandErrors({ state: 'X', minimum: 0, mediumFrom: 4, highFrom: 8 })[0]).toMatchObject({
      field: 'minimum',
      message: expect.stringMatching(/al menos 1/),
    });
    expect(bandErrors({ state: 'X', minimum: 2, mediumFrom: 4.5, highFrom: 8 })[0]).toMatchObject({
      field: 'mediumFrom',
      message: expect.stringMatching(/entero/),
    });
    expect(bandErrors({ state: 'X', minimum: 2, mediumFrom: 4, highFrom: Number.NaN })[0].field).toBe('highFrom');
  });

  it('exige exactamente las cuatro clases anómalas', () => {
    const falta = defaultDiagnosisCriteria();
    falta.severityBands = falta.severityBands.slice(1);
    expect(criteriaErrors(falta).join(' ')).toMatch(/exactamente/);

    const sobra = defaultDiagnosisCriteria();
    sobra.severityBands.push({ state: 'Sano', minimum: 1, mediumFrom: 2, highFrom: 3 });
    expect(criteriaErrors(sobra).join(' ')).toMatch(/exactamente/);
  });

  it('la criticidad tiene que ser una permutación de los cuatro estados', () => {
    expect(criticalityError(['Daño fúngico', 'Plaga foliar', 'Clorosis', 'Estrés solar'])).toBeNull();
    expect(criticalityError(['Clorosis', 'Clorosis', 'Plaga foliar', 'Estrés solar'])).not.toBeNull();
    expect(criticalityError(['Clorosis', 'Plaga foliar', 'Estrés solar'])).not.toBeNull();
    expect(criticalityError(['Clorosis', 'Plaga foliar', 'Estrés solar', 'Sano'])).not.toBeNull();
  });
});

describe('criterio de diagnóstico · edición', () => {
  it('mueve un estado una posición y no sale de la lista', () => {
    const c = DEFAULT_DIAGNOSIS_CRITERIA.criticality;
    expect(moveCriticality(c, 'Clorosis', -1)).toEqual(['Daño fúngico', 'Clorosis', 'Plaga foliar', 'Estrés solar']);
    expect(moveCriticality(c, 'Daño fúngico', -1)).toBe(c);
    expect(moveCriticality(c, 'Estrés solar', 1)).toBe(c);
  });

  it('cuenta un cambio por campo de banda y uno por reordenar', () => {
    const saved = defaultDiagnosisCriteria();
    expect(countCriteriaChanges(saved, defaultDiagnosisCriteria())).toBe(0);

    const draft = conBanda('Daño fúngico', { mediumFrom: 3, highFrom: 9 });
    expect(countCriteriaChanges(saved, draft)).toBe(2);

    draft.criticality = moveCriticality(draft.criticality, 'Clorosis', -1);
    expect(countCriteriaChanges(saved, draft)).toBe(3);
  });

  it('resume una banda en palabras', () => {
    expect(bandSummary({ state: 'Daño fúngico', minimum: 2, mediumFrom: 4, highFrom: 8 })).toBe(
      'Baja 2–3 · Media 4–7 · Alta 8 o más',
    );
    expect(bandSummary({ state: 'X', minimum: 2, mediumFrom: 3, highFrom: 4 })).toBe('Baja 2 · Media 3 · Alta 4 o más');
  });
});
