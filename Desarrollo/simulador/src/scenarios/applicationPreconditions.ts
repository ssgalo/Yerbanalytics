import type { RuleScenario } from './types';

/** Air temperature (°C) the zone reports: under the 30 °C an application tolerates (P-D3). */
export const APPLICATION_AIR_TEMP = 22;

/** Substrate humidity (%) the zone reports: over the 45 % an application needs (P-D5). */
export const APPLICATION_SUBSTRATE_HUMIDITY = 55;

/**
 * Modifier, not a rule of its own: it makes the conditions an application needs hold so that the rule that asks for one
 * (today `SupplyRule`; later N-*, F-*) is not stopped by the preconditions (P-D3…P-D5) but by what it decides.
 *
 *  - P-D3: the zone reports 22 °C and the forecast says the same.
 *  - P-D4: the forecast has no rain at all.
 *  - P-D5: the substrate reads 55 %.
 *  - The tanks of the target zone get stock (`ensureTankStock`), because an unregistered tank is not a full tank.
 *
 * It cannot move the clock: the application window (07–10 / 17–19) is a bypass of `ApplicationPreconditions` in the
 * rules panel (`window`), the same way the irrigation window is. The intervals between applications are cooldowns that
 * `needsCooldownReset` already takes to zero.
 *
 * Applied first (low `applyOrder`): a scenario that claims the same metric on purpose (a rainy day, a hot one) wins.
 */
export const applicationPreconditions: RuleScenario = {
  key: 'APPLICATION_PRECONDITIONS',
  label: 'Precondiciones de aplicación (P-D3…P-D5)',
  description:
    'Deja la lectura, el clima y los tanques en condiciones de aplicar un insumo; combinalo con la regla que lo pide',
  branch: 'INSUMO',
  backendRules: ['ApplicationPreconditions'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 5,
  apply: () => ({
    telemetry: {
      temp: { value: APPLICATION_AIR_TEMP, note: `temp = ${APPLICATION_AIR_TEMP} °C (P-D3: por debajo del máximo para aplicar)` },
      humSus: {
        value: APPLICATION_SUBSTRATE_HUMIDITY,
        note: `humSus = ${APPLICATION_SUBSTRATE_HUMIDITY} % (P-D5: sobre el mínimo para aplicar)`,
      },
    },
    weather: { tempC: APPLICATION_AIR_TEMP, probLluviaPct: 0, precipMm: 0 },
    ensureTankStock: true,
    notes: [
      'clima sin lluvia (P-D4) y los tanques de la zona con stock; si no hay uno registrado, se registra',
      'la ventana horaria (07–10 / 17–19) no se simula: activá el bypass «window» de ApplicationPreconditions en el panel de reglas',
    ],
  }),
};
