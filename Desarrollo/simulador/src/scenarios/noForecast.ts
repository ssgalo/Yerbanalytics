import type { RuleScenario } from './types';

/** Rain probability (%) the simulated weather announces: over any threshold the catalog allows for R-03. */
const HEAVY_RAIN_PROBABILITY = 90;

/** Rain (mm) the simulated weather announces: over any threshold the catalog allows for R-03. */
const HEAVY_RAIN_MM = 20;

/**
 * O-01…O-03, no forecast: a MODIFIER, not a rule. It turns the backend's «sin pronóstico» demo mode on (`PUT
 * /api/demo/weather/offline`) and, so the effect is visible, injects a simulated weather with heavy rain: with a forecast R-03
 * would postpone the irrigation, without it the rules assume it does not rain and R-01 waters. It combines with R-03, the
 * shade and the application preconditions (which read the temperature from the last reading and do not block on rain).
 *
 * Entering and leaving the mode leaves one `INFO` alert each (`ForecastModeMonitor`, within its next check).
 */
export const noForecast: RuleScenario = {
  key: 'NO_FORECAST',
  label: 'Sin pronóstico (O-01…O-03)',
  description:
    'Modificador: el backend decide sin pronóstico aunque el clima simulado anuncie lluvia fuerte; R-01 riega, las aplicaciones no se bloquean por lluvia',
  branch: 'GLOBAL',
  backendRules: ['PosponerPorLluviaRule', 'ApplicationPreconditions'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 90,
  apply: () => ({
    offlineForecast: true,
    weather: { probLluviaPct: HEAVY_RAIN_PROBABILITY, precipMm: HEAVY_RAIN_MM },
    notes: [
      'el backend queda en modo sin pronóstico: las reglas asumen que no llueve (R-03 no pospone) y usan sólo la última lectura de temperatura',
      `el clima simulado anuncia lluvia fuerte (${HEAVY_RAIN_PROBABILITY} %, ${HEAVY_RAIN_MM} mm) para ver que no se usa`,
      'al terminar, desactivalo en «Clima simulado → Sin pronóstico» (el modo queda hasta que lo saques): al volver el pronóstico queda un INFO',
    ],
  }),
};
