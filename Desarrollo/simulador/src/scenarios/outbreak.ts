import { mildDiagnosis } from './fungalDamage';
import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses } from './shared';
import type { RuleScenario } from './types';

/** Sectors that get the diagnosis: the three that form an outbreak with the factory `biotico.sectores-foco`. */
export const OUTBREAK_SECTORS = 3;

/**
 * F-04, outbreak: the same mild fungal diagnosis is registered on three adjacent sectors of the target zone, so the pulse
 * finds an outbreak and asks for the fungicide on every sector next to them, healthy ones included, plus a CRITICAL alert.
 *
 * The diagnosis is mild on purpose: `FungalDamageRule` waits for the three sectors (nothing else justifies a treatment), so
 * the only rule that acts is the outbreak. Which neighbors can be treated depends on the hardware: a sector with no pump
 * registered stays out, and the alert says so.
 *
 * The zone's `biotico.sectores-foco` can be raised above three: then it takes more sectors than the scenario registers.
 */
export const outbreak: RuleScenario = {
  key: 'OUTBREAK',
  label: 'Foco biótico (F-04)',
  description:
    'Registra el mismo diagnóstico de daño fúngico en tres sectores de la zona: el pulso pide fitosanitario para sus lindantes y deja un CRITICAL',
  branch: 'INSUMO',
  backendRules: ['OutbreakRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 63,
  conflicts: [...conflictsWithEarlierDiagnoses('OUTBREAK'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = mildDiagnosis('Daño fúngico', thresholds.confianzaMinima);
    return {
      outbreak: { diagnosis, sectors: OUTBREAK_SECTORS },
      ensureTankStock: true,
      notes: [
        `diagnóstico IA previo en ${OUTBREAK_SECTORS} sectores contiguos de la zona: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}%`,
        'los lindantes se calculan con la disposición por fila de la topología; un sector sin bomba registrada queda fuera',
        'los tanques de la zona quedan con stock; si no hay uno registrado, se registra',
        'cada lindante se aplica en su próxima ventana apta: combinalo con «Precondiciones de aplicación» y activá el bypass «window»',
      ],
    };
  },
};
