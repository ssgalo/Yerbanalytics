import type { RuleScenario } from './types';

/** The conductivity (dS/m) never goes below this: a sensor reads a little more than zero even in plain water. */
const MIN_CE = 0.05;

/** How far under the `nutricion.ce-baja` threshold the zone reads. With the factory 0,5 dS/m it is the 0,3 dS/m of the v2. */
const BELOW_THRESHOLD = 0.2;

/** CE (dS/m) the zone reports: clearly under the low-conductivity threshold, which is editable. */
export function lowEcValue(ceBaja: number): number {
  return Math.max(MIN_CE, Math.round((ceBaja - BELOW_THRESHOLD) * 100) / 100);
}

/**
 * N-02, low EC: the substrate conductivity of the target zone reads under the threshold and the zone has not been
 * fertilized for days, so the pulse brings the nutrition plan forward (and leaves its INFO alert).
 *
 * Three things have to hold at the pulse, and none is a loose number:
 *  - The CE travels in the answer to the pulse (`pinnedReadings`), not as a reading of its own: the engine decides on that
 *    answer. A zone that has not gathered six pulse readings yet would not decide, so the `samples` bypass of `LowEcRule`
 *    (`bypassRules`) makes the last reading count as the average.
 *  - "Not fertilized for 5 days" is the `no-fertilized` cooldown of `LowEcRule`, which `needsCooldownReset` takes to zero.
 *  - The plan must not be advanced or running already: an advanced plan does not raise a second alert (one alert per event).
 */
export const lowEc: RuleScenario = {
  key: 'LOW_EC',
  label: 'CE baja (N-02)',
  description:
    'La conductividad del sustrato queda bajo el umbral y la zona no fertilizó en días: el pulso adelanta el plan de nutrición y deja un INFO',
  branch: 'INSUMO',
  backendRules: ['LowEcRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 56,
  conflicts: [
    {
      with: 'NODE_OFFLINE',
      reason: () => 'CE baja viaja en la respuesta al pulso y con el nodo mudo nadie contesta. Ejecutalo por separado.',
    },
  ],
  apply: ({ thresholds }) => {
    const ce = lowEcValue(thresholds.ceBaja);
    return {
      pinnedReadings: {
        ce: {
          value: ce,
          note: `ce = ${ce} dS/m (bajo el umbral de CE baja, ${thresholds.ceBaja} dS/m) en cada lectura de la zona`,
        },
      },
      bypassRules: { LowEcRule: ['samples'] },
      notes: [
        'el bypass «samples» de LowEcRule se activa antes del pulso: la última lectura de CE cuenta como la media de las últimas lecturas',
        'el cooldown «no-fertilized» queda en 0: no hace falta esperar días sin fertilizar',
        'el pulso con motor adelanta el plan (N-01 corre justo después en el mismo pulso) y queda un INFO «CE baja» en el historial de la zona',
        'la emisión automática tiene que estar activa: la CE viaja en la respuesta al pulso',
        'al terminar, soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};
