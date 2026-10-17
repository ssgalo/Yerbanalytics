import type { RuleScenario } from './types';

/** How far above the physical limit the stuck sensor reads: 100 % + 12 = the 112 % of the v2 example. */
const EXCESS = 12;

/** Scenarios that need the soil humidity to read something sensible, or a node that answers. */
const NEED_A_SENSIBLE_HUMIDITY = ['R01', 'R02', 'R03', 'R04', 'LOCK', 'NODE_OFFLINE'];

/**
 * S-03, invalid reading: the soil humidity sensor of the target zone gets stuck above the physical limit, so the
 * pulse's answer and every reread carry the impossible value and the backend ends up discarding that metric.
 *
 * The fault is not published as a telemetry reading of its own (`faultySensor`, not `telemetry`): the whole
 * reread-and-discard flow has to happen inside the pulse the engine decides on. The impossible value comes from the
 * catalog limit (`seguridad.rango-hum-max`), never from a loose number, so it stays impossible if the limit is edited.
 */
export const invalidReading: RuleScenario = {
  key: 'INVALID_READING',
  label: 'Lectura inválida (S-03)',
  description:
    'El sensor de humedad de sustrato queda trabado fuera del rango físico: se piden las relecturas y, si todas fallan, se descarta esa métrica con una alerta',
  branch: 'GLOBAL',
  backendRules: ['ReadingValidationGuard'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 40,
  conflicts: NEED_A_SENSIBLE_HUMIDITY.map((other) => ({
    with: other,
    reason: () =>
      'Lectura inválida deja la humedad de sustrato trabada fuera de rango (y R-01…R-04, el bloqueo y el nodo mudo la necesitan o cambian quién contesta). Ejecutalo por separado.',
  })),
  apply: ({ thresholds }) => {
    const stuck = thresholds.humedadMaxima + EXCESS;
    return {
      faultySensor: {
        humSus: {
          value: stuck,
          note: `humSus del sensor queda trabado en ${stuck}% (el máximo físico es ${thresholds.humedadMaxima}%): cada lectura sale así`,
        },
      },
      notes: [
        `el pulso con motor recibe el valor imposible y el backend pide hasta ${thresholds.relecturasInvalidas} relecturas; todas llegan igual`,
        'la métrica se descarta en el ciclo: R-01…R-06 no actúan (sin dato) y queda un WARNING «Lectura inválida en Humedad de sustrato»',
        'si vuelve a fallar en el ciclo de lectura siguiente la alerta sube a CRITICAL',
        'la emisión automática tiene que estar activa: con la emisión apagada nadie contesta la lectura ni las relecturas',
        'al terminar, repará el sensor en «Sensores con falla»',
      ],
    };
  },
};
