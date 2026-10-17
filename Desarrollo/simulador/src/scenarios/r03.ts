import { humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

export const r03: RuleScenario = {
  key: 'R03',
  label: 'Posponer por lluvia inminente (R-03)',
  description: 'Condición de riego + alta probabilidad de lluvia',
  branch: 'RIEGO',
  backendRules: ['PosponerPorLluviaRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 10,
  apply: (ctx) => {
    const { thresholds } = ctx;
    const probLluviaPct = Math.min(100, thresholds.lluviaProbabilidad + 10);
    const precipMm = thresholds.lluviaMm + 3;
    return {
      telemetry: { humSus: humidityDueForIrrigation(ctx) },
      weather: { tempC: 18, humedadRelPct: 88, probLluviaPct, precipMm, uvIndex: 2 },
      notes: [`clima: prob. lluvia = ${probLluviaPct}%, precipMm = ${precipMm} (umbral: ${thresholds.lluviaMm})`],
    };
  },
};
