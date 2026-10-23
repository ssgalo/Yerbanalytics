import { conflictsWithAZoneThatCannotApply, conflictsWithEarlierDiagnoses, diagnosisPlan } from './shared';
import type { RuleScenario } from './types';

/** Substrate humidity (%) of a plain chlorosis: well under the 80 % that points to root asphyxia. */
export const NORMAL_SUBSTRATE_HUMIDITY = 60;

/** Substrate conductivity (dS/m) of a plain chlorosis: well under the 2,0 that points to salinity. */
export const NORMAL_EC = 0.8;

/** Substrate pH of a plain chlorosis: well under the 6,5 that points to iron chlorosis. */
export const NORMAL_PH = 5.5;

/** How far over the `clorosis.ph-ferrica` threshold the zone reads: with the factory 6,5 it is the 6,8 of the v2 example. */
const PH_MARGIN = 0.3;

/**
 * F-C2, nutritional chlorosis: a "Clorosis" diagnosis over normal substrate, conductivity and pH, so no cause rules the
 * fertilizer out and the pulse asks for it. The three readings travel in the answer to the pulse (`pinnedReadings`),
 * because that is what the engine decides on and the automatic emission would otherwise put random ones. The tanks of the
 * zone get stock first (`ensureTankStock`); whether the sector is dosed in the same pulse is up to the application
 * preconditions: combine it with `APPLICATION_PRECONDITIONS` and the `window` bypass.
 */
export const chlorosisNutritional: RuleScenario = {
  key: 'CHLOROSIS_NUTRITIONAL',
  label: 'Clorosis nutricional (F-C2)',
  description:
    'Diagnóstico Clorosis con sustrato, CE y pH normales: ninguna causa descarta el fertilizante y el pulso lo pide para el sector, con un WARNING',
  branch: 'INSUMO',
  backendRules: ['ChlorosisNutritionalRule'],
  needsCooldownReset: true,
  needsDiagnosis: true,
  applyOrder: 70,
  conflicts: [...conflictsWithEarlierDiagnoses('CHLOROSIS_NUTRITIONAL'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = diagnosisPlan('Clorosis', 'Media', thresholds.confianzaMinima);
    return {
      diagnosis,
      pinnedReadings: {
        humSus: { value: NORMAL_SUBSTRATE_HUMIDITY, note: `humSus = ${NORMAL_SUBSTRATE_HUMIDITY} % en cada lectura de la zona (sin asfixia radicular)` },
        ce: { value: NORMAL_EC, note: `ce = ${NORMAL_EC} dS/m en cada lectura de la zona (sin salinidad)` },
        phSuelo: { value: NORMAL_PH, note: `phSuelo = ${NORMAL_PH} en cada lectura de la zona (sin clorosis férrica)` },
      },
      ensureTankStock: true,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'los tanques de la zona quedan con stock; si no hay uno registrado, se registra',
        'el sector se aplica en su próxima ventana apta: combinalo con «Precondiciones de aplicación» y activá el bypass «window» para verlo aplicado en el mismo pulso',
        'la emisión automática tiene que estar activa: las lecturas viajan en la respuesta al pulso; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};

/**
 * F-C1, chlorosis with a non-nutritional cause: the same diagnosis over a high pH (6,8 with the factory threshold), which
 * points to iron chlorosis. The rule cuts the supplies branch, so the fertilizer is not asked for, and warns with the
 * probable cause. Nothing is applied, so the tanks are left alone.
 */
export const chlorosisNonNutritional: RuleScenario = {
  key: 'CHLOROSIS_NON_NUTRITIONAL',
  label: 'Clorosis con causa no nutricional (F-C1)',
  description:
    'Diagnóstico Clorosis con el pH alto (6,8): probable clorosis férrica, no se aplica fertilizante y queda un WARNING con la causa',
  branch: 'INSUMO',
  backendRules: ['ChlorosisNonNutritionalRule'],
  needsCooldownReset: false,
  needsDiagnosis: true,
  applyOrder: 71,
  conflicts: [...conflictsWithEarlierDiagnoses('CHLOROSIS_NON_NUTRITIONAL'), ...conflictsWithAZoneThatCannotApply()],
  apply: ({ thresholds }) => {
    const diagnosis = diagnosisPlan('Clorosis', 'Media', thresholds.confianzaMinima);
    const ph = Math.round((thresholds.phFerrica + PH_MARGIN) * 10) / 10;
    return {
      diagnosis,
      pinnedReadings: {
        phSuelo: { value: ph, note: `phSuelo = ${ph} en cada lectura de la zona (pH alto: sobre el umbral de ${thresholds.phFerrica})` },
        humSus: { value: NORMAL_SUBSTRATE_HUMIDITY, note: `humSus = ${NORMAL_SUBSTRATE_HUMIDITY} % en cada lectura de la zona (que sea el pH la única causa)` },
        ce: { value: NORMAL_EC, note: `ce = ${NORMAL_EC} dS/m en cada lectura de la zona (que sea el pH la única causa)` },
      },
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${diagnosis.conf}% (mínimo ${thresholds.confianzaMinima}%)`,
        'F-C1 corta la rama de insumos del sector: F-C2 queda sin alcanzar y no se pide fertilizante',
        'la emisión automática tiene que estar activa: las lecturas viajan en la respuesta al pulso; al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
