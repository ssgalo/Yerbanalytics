import type { RuleScenario } from './types';

export const r04: RuleScenario = {
  key: 'R04',
  label: 'Sustrato saturado (R-04)',
  description: 'Humedad sobre el umbral de saturación — bloquea el riego',
  branch: 'RIEGO',
  backendRules: ['SustratoSaturadoRule'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 20,
  apply: ({ thresholds }) => {
    const h = Math.min(98, thresholds.saturacionBloqueo + 5);
    return {
      telemetry: {
        humSus: { value: h, note: `humSus = ${h}% (≥ saturación ${thresholds.saturacionBloqueo}%)` },
      },
    };
  },
};
