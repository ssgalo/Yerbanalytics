import { mildDiagnosis } from './fungalDamage';
import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses } from './shared';
import type { RuleScenario } from './types';

/**
 * Hot, dry air: 38 °C with 25 % of ambient humidity is a vapor pressure deficit near 5 kPa, above the 3 kPa that is the
 * highest an operator can set for `biotico.dpv-calor-seco`. Whatever the catalog says, the scenario clears it.
 */
export const HOT_DRY_AIR_TEMP = 38;
export const HOT_DRY_AIR_HUMIDITY = 25;

/**
 * F-P1, foliar pest: a mild "Plaga foliar" diagnosis that only deserves a treatment because the air has been hot and dry
 * (the mites' weather). The zone answers the pulse with 38 °C and 25 % of ambient humidity, so the deficit reaches the
 * threshold in the readings history, and the next pulse asks for the fungicide.
 *
 * The heat is the point of the scenario, but an application is deferred over 30 °C (P-D3): the `temperature` bypass of
 * `ApplicationPreconditions` lets the order through.
 */
export const foliarPest: RuleScenario = {
  key: 'FOLIAR_PEST',
  label: 'Plaga foliar (F-P1)',
  description:
    'Diagnóstico Plaga foliar leve con calor y aire seco (38 °C, 25 % de humedad): el pulso pide fitosanitario para el sector y deja un WARNING',
  branch: 'INSUMO',
  backendRules: ['FoliarPestRule'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 61,
  conflicts: [...conflictsWithEarlierDiagnoses('FOLIAR_PEST'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = mildDiagnosis('Plaga foliar', thresholds.confianzaMinima);
    return {
      diagnosis,
      pinnedReadings: {
        temp: { value: HOT_DRY_AIR_TEMP, note: `temp = ${HOT_DRY_AIR_TEMP} °C en cada lectura de la zona (calor)` },
        humAmb: {
          value: HOT_DRY_AIR_HUMIDITY,
          note: `humAmb = ${HOT_DRY_AIR_HUMIDITY} % en cada lectura de la zona (aire seco: DPV ≥ 2 kPa)`,
        },
      },
      bypassRules: { ApplicationPreconditions: ['temperature'] },
      ensureTankStock: true,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'el bypass «temperature» de ApplicationPreconditions se activa antes del pulso: con más de 30 °C una aplicación se difiere',
        'la emisión automática tiene que estar activa: el calor y el aire seco viajan en la respuesta al pulso',
        'al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
