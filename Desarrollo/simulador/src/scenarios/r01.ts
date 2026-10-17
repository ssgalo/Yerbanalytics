import { humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

export const r01: RuleScenario = {
  key: 'R01',
  label: 'Riego por déficit hídrico (R-01)',
  description: 'Humedad entre el umbral crítico y el umbral de riego',
  branch: 'RIEGO',
  backendRules: ['RiegoPorDeficitRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 10,
  apply: (ctx) => ({ telemetry: { humSus: humidityDueForIrrigation(ctx) } }),
};
