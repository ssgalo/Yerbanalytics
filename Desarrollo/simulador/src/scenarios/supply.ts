import type { DiagnosisPlan, RuleScenario } from './types';

/**
 * Ambient humidity (%) beyond the seeded critical range (crit 42-96). It is a state-driving metric that
 * no irrigation rule reads, so forcing it critical does not collide with the substrate humidity the water
 * rules need.
 *
 * It is NOT the air temperature (as it used to be): applying an input with more than 30 °C is exactly what
 * the application preconditions forbid (P-D3), so a critical-by-heat sector could never be dosed.
 *
 * Only `SupplyRule` needs it (it doses in a CRITICAL sector). It goes away with that rule, once the v2
 * application rules (N-*, F-*) replace it.
 */
export const CRITICAL_AMBIENT_HUMIDITY = 98;

export const supply: RuleScenario = {
  key: 'SUPPLY',
  label: 'Dosificación de insumo',
  description:
    'Diagnóstico IA crítico pendiente de actuar — requiere un diagnóstico previo y la regla encendida (arranca retirada)',
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
        humAmb: {
          value: CRITICAL_AMBIENT_HUMIDITY,
          note: `humAmb = ${CRITICAL_AMBIENT_HUMIDITY} % (estado crítico: la dosificación solo actúa en crítico)`,
        },
      },
      diagnosis,
      notes: [
        `diagnóstico IA previo: ${diagnosis.estado} · severidad ${diagnosis.sev} · conf. ${conf}% (mínimo ${thresholds.confianzaMinima}%)`,
      ],
    };
  },
};
