import { conflictsWithAZoneThatCannotApply } from './shared';
import type { RuleScenario } from './types';

/** Ambient humidity (%) that, at a mild temperature, leaves the vapor pressure deficit under 0,1 kPa (the lowest `biotico.dpv-saturado` can be set to). */
export const SATURATED_AIR_HUMIDITY = 98;

/** Mild temperature (°C), inside the 18–28 °C the rule asks for whatever the operator edits within its ranges. */
export const MILD_AIR_TEMP = 23;

/** Points over the substrate humidity the rule asks for. */
const SUBSTRATE_MARGIN = 3;

/**
 * F-F2, predisposing condition: saturated air, a mild temperature and wet substrate. The zone answers the pulse with them
 * and the rule warns «Condiciones favorables a enfermedades fúngicas» without applying anything. It needs no diagnosis.
 *
 * The rule asks for two consecutive readings of saturated air; the `readings` bypass of `FungalConditionRule` takes a single
 * one. One alert per event: if the previous reading already met the conditions, the pulse stays silent, so release the zone
 * between runs.
 *
 * The substrate humidity is the point, so the scenarios that want it dry (R-01, R-02) cannot run next to this one.
 */
export const fungalCondition: RuleScenario = {
  key: 'FUNGAL_CONDITION',
  label: 'Condición predisponente al hongo (F-F2)',
  description:
    'Aire saturado, temperatura templada y sustrato húmedo, sin diagnóstico: el pulso deja un WARNING y no aplica nada',
  branch: 'INSUMO',
  backendRules: ['FungalConditionRule'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 62,
  conflicts: [
    ...conflictsWithAZoneThatCannotApply(),
    ...['R01', 'R02'].map((other) => ({
      with: other,
      reason: () => 'Condición predisponente necesita el sustrato húmedo y el riego por déficit lo necesita seco. Ejecutalos por separado.',
    })),
  ],
  apply: ({ thresholds }) => {
    const substrate = Math.min(100, thresholds.humedadPredisponente + SUBSTRATE_MARGIN);
    return {
      pinnedReadings: {
        humAmb: {
          value: SATURATED_AIR_HUMIDITY,
          note: `humAmb = ${SATURATED_AIR_HUMIDITY} % en cada lectura de la zona (aire saturado: DPV < 0,1 kPa)`,
        },
        temp: { value: MILD_AIR_TEMP, note: `temp = ${MILD_AIR_TEMP} °C en cada lectura de la zona (temperatura templada)` },
        humSus: {
          value: substrate,
          note: `humSus = ${substrate} % en cada lectura de la zona (sustrato húmedo, desde ${thresholds.humedadPredisponente} %)`,
        },
      },
      bypassRules: { FungalConditionRule: ['readings'] },
      notes: [
        'el bypass «readings» de FungalConditionRule se activa antes del pulso: una sola lectura de aire saturado alcanza',
        'avisa una sola vez por evento: si la lectura anterior ya cumplía las condiciones el pulso no repite el aviso',
        'no aplica ningún insumo ni necesita diagnóstico',
        'la emisión automática tiene que estar activa: las lecturas viajan en la respuesta al pulso',
        'al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
