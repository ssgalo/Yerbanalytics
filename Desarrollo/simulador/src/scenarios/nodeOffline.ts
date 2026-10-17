import type { RuleScenario } from './types';

/**
 * Consecutive pulses without an answer that declare a node out of service (reglas_v2 S-02). Mirrors
 * `EscalamientoNodoService.FUERA_DE_SERVICIO` in the backend: the simulator does not read the backend code,
 * so if that number changes, this one changes with it.
 */
export const PULSES_TO_DECLARE_OFFLINE = 3;

/** The other scenarios feed telemetry to the engine; a node that does not answer cannot make them fire. */
const OTHER_SCENARIOS = ['R01', 'R02', 'R03', 'R04', 'SHADING', 'SUPPLY', 'LOCK'];

export const nodeOffline: RuleScenario = {
  key: 'NODE_OFFLINE',
  label: 'Nodo sin respuesta (S-02)',
  description:
    'La zona deja de contestar la lectura — tres pulsos seguidos la declaran fuera de servicio y ordenan el estado seguro',
  branch: 'GLOBAL',
  backendRules: ['EscalamientoNodo'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 5,
  conflicts: OTHER_SCENARIOS.map((other) => ({
    with: other,
    reason: () =>
      'Nodo sin respuesta silencia la zona: no llega telemetría para que otra regla dispare. Ejecutalo por separado.',
  })),
  apply: () => ({
    silenceZone: true,
    pulses: PULSES_TO_DECLARE_OFFLINE,
    notes: [
      'la zona destino deja de responder LEER_AHORA (la emisión automática tiene que estar activa para notar la diferencia)',
      `se piden ${PULSES_TO_DECLARE_OFFLINE} pulsos con motor, uno tras otro, esperando que termine cada uno`,
      'para acortar la espera: bajá sensado.espera-respuesta y sensado.reintentos en Motor de reglas → Parámetros',
    ],
  }),
};
