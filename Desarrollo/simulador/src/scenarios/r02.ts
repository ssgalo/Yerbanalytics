import type { RuleScenario } from './types';

export const r02: RuleScenario = {
  key: 'R02',
  label: 'Déficit hídrico crítico (R-02)',
  description: 'Humedad bajo el umbral crítico — riego urgente',
  branch: 'RIEGO',
  backendRules: ['DeficitCriticoRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 30,
  conflicts: [
    {
      with: 'R04',
      reason: ({ thresholds }) =>
        `R-02 requiere humSus < ${thresholds.umbralCritico}% y R-04 requiere humSus ≥ ${thresholds.saturacionBloqueo}%. Son excluyentes; ejecutalas por separado.`,
    },
  ],
  apply: ({ thresholds }) => {
    const h = Math.max(5, thresholds.umbralCritico - 7);
    return {
      telemetry: {
        humSus: { value: h, note: `humSus = ${h}% (< umbral crítico ${thresholds.umbralCritico}%)` },
      },
    };
  },
};
