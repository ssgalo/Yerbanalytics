import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses } from './shared';
import type { DiagnosisPlan, RuleScenario } from './types';

/**
 * F-06, fungicide cap: a high-severity fungal diagnosis (reason enough for `FungalDamageRule` to ask for the fungicide) with
 * the `cap` bypass of `ApplicationPreconditions` on, so a sector that already got three fungicides in 30 days receives the
 * fourth one anyway.
 *
 * To see the block, run FUNGAL_DAMAGE three times (with its cooldowns reset) and once more without this scenario: the fourth
 * is rejected with the «tope» reason and leaves a WARNING until someone registers the review of the sector
 * (`POST /api/sectores/{id}/revision-fitosanitario`). The simulator never edits the applications.
 */
export const fungicideCap: RuleScenario = {
  key: 'FUNGICIDE_CAP',
  label: 'Tope de fitosanitarios (F-06)',
  description:
    'Diagnóstico Daño fúngico de severidad alta con el bypass «cap»: un sector que ya recibió tres fitosanitarios en 30 días recibe el cuarto',
  branch: 'INSUMO',
  backendRules: ['FungalDamageRule', 'ApplicationPreconditions'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 64,
  conflicts: [...conflictsWithEarlierDiagnoses('FUNGICIDE_CAP'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis: DiagnosisPlan = {
      estado: 'Daño fúngico',
      sev: 'Alta',
      confidence: 'Alta',
      conf: Math.min(100, thresholds.confianzaMinima + 15),
    };
    return {
      diagnosis,
      bypassRules: { ApplicationPreconditions: ['cap'] },
      ensureTankStock: true,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'el bypass «cap» de ApplicationPreconditions se activa antes del pulso: el tope de fitosanitarios por sector no se aplica',
        'sin este escenario, un sector con tres fitosanitarios en 30 días rechaza el cuarto hasta que alguien registre la revisión',
      ],
    };
  },
};
