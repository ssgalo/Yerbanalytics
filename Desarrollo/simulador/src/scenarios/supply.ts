import type { DiagnosisPlan, RuleScenario } from './types';

/**
 * Air temperature (°C) beyond the seeded critical range (crit 11-35). Air temperature is a
 * state-driving metric that no irrigation rule reads, so forcing it critical does not
 * collide with the humidity the water rules need.
 *
 * Only `SupplyRule` needs it (it doses in a CRITICAL sector). It goes away with that rule
 * (see `add-dosificacion-base`).
 */
export const CRITICAL_AIR_TEMP = 40;

export const supply: RuleScenario = {
  key: 'SUPPLY',
  label: 'Dosificación de insumo',
  description: 'Diagnóstico IA crítico pendiente de actuar — requiere un diagnóstico previo',
  branch: 'INSUMO',
  backendRules: ['SupplyRule'],
  needsCooldownReset: false,
  needsDiagnosis: true,
  applyOrder: 50,
  apply: ({ thresholds }) => {
    // The diagnosis is born before the pulse: the rule only acts once per NEW diagnosis, and
    // the pulse is the only moment the engine decides. Score above the minimum confidence.
    const conf = Math.min(100, thresholds.confianzaMinima + 5);
    const diagnosis: DiagnosisPlan = { estado: 'Clorosis', sev: 'Alta', confidence: 'Alta', conf };
    return {
      // SupplyRule only doses in a CRITICAL sector, and that state comes from the sensed metrics,
      // not from the diagnosis: without this the sector stays healthy/warning and the rule idles.
      telemetry: {
        temp: {
          value: CRITICAL_AIR_TEMP,
          note: `temp = ${CRITICAL_AIR_TEMP} °C (estado crítico: la dosificación solo actúa en crítico)`,
        },
      },
      diagnosis,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${conf}% (mínimo ${thresholds.confianzaMinima}%)`,
      ],
    };
  },
};
