import type { RuleScenario } from './types';

export const shading: RuleScenario = {
  key: 'SHADING',
  label: 'Mediasombra por pico UV',
  description: 'Índice UV por encima del umbral — despliega la mediasombra',
  branch: 'MEDIASOMBRA',
  backendRules: ['ShadingRule'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 40,
  apply: ({ thresholds }) => {
    const uv = thresholds.uvUmbral + 1;
    return {
      weather: { uvIndex: uv },
      notes: [`clima: uvIndex = ${uv} (umbral: ${thresholds.uvUmbral})`],
    };
  },
};
