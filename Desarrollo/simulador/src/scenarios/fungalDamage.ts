import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses, diagnosisPlan } from './shared';
import type { DiagnosisPlan, RuleScenario } from './types';

/** The diagnosis a biotic scenario registers: the state, a mild severity (so the rule needs another reason to act) and a valid confidence. */
export function mildDiagnosis(estado: string, minConfidence: number): DiagnosisPlan {
  return diagnosisPlan(estado, 'Baja', minConfidence);
}

/**
 * F-F1, fungal damage that persists: a mild "Daño fúngico" diagnosis on a dry ambient would only wait for more captures, so
 * the scenario turns on the `persistence` bypass of `FungalDamageRule` (the diagnosis counts as seen in the consecutive
 * captures the catalog asks for) and the next pulse asks for the fungicide.
 *
 * Whether it is applied in the same pulse is up to the application preconditions: combine it with
 * `APPLICATION_PRECONDITIONS` and the `window` bypass to see it applied right away. The tanks of the zone get stock first
 * (`ensureTankStock`).
 */
export const fungalDamage: RuleScenario = {
  key: 'FUNGAL_DAMAGE',
  label: 'Daño fúngico persistente (F-F1)',
  description:
    'Diagnóstico Daño fúngico leve que se repite en capturas seguidas: el pulso pide fitosanitario para el sector y deja un WARNING',
  branch: 'INSUMO',
  backendRules: ['FungalDamageRule'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 60,
  conflicts: [...conflictsWithEarlierDiagnoses('FUNGAL_DAMAGE'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = mildDiagnosis('Daño fúngico', thresholds.confianzaMinima);
    return {
      diagnosis,
      bypassRules: { FungalDamageRule: ['persistence'] },
      ensureTankStock: true,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'el bypass «persistence» de FungalDamageRule se activa antes del pulso: el diagnóstico cuenta como visto en las capturas seguidas',
        'los tanques de la zona quedan con stock; si no hay uno registrado, se registra',
        'el sector se aplica en su próxima ventana apta: combinalo con «Precondiciones de aplicación» y activá el bypass «window» para verlo aplicado en el mismo pulso',
      ],
    };
  },
};
