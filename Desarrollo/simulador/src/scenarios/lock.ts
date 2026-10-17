import { humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

/** Reason the simulator gives the lock it creates; it shows up in the dashboard and in the history. */
export const LOCK_REASON = 'Bloqueo de prueba desde el simulador';

/**
 * Scenarios that make the lock's effect impossible to read: with saturated soil the zone would not irrigate
 * either way. The silent node declares its conflict with this scenario on its own side (conflicts are
 * declared once; the detection is symmetric).
 */
const AMBIGUOUS_WITH = ['R04'];

export const lock: RuleScenario = {
  key: 'LOCK',
  label: 'Bloqueo manual (S-01)',
  description:
    'Bloquea la zona (o un sector) por la API de bloqueos con el sustrato seco: el pulso no debe ordenar riego',
  branch: 'GLOBAL',
  backendRules: ['ManualLockRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 30,
  conflicts: AMBIGUOUS_WITH.map((other) => ({
    with: other,
    reason: () =>
      'Bloqueo manual necesita el sustrato seco para que se note que el bloqueo es lo que frena el riego. Ejecutalo por separado.',
  })),
  apply: (ctx) => ({
    // Dry enough for R-01 to irrigate if nothing stopped it.
    telemetry: { humSus: humidityDueForIrrigation(ctx) },
    manualLock: { reason: LOCK_REASON },
    notes: [
      'se crea el bloqueo antes del pulso (POST /api/bloqueos); el pulso con motor no debe ordenar riego',
      'la traza del sector muestra el bloqueo como causa; al terminar, levantalo en «Bloqueos vigentes»',
    ],
  }),
};
