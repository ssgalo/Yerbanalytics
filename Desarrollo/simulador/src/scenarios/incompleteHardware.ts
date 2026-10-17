import { humidityDueForIrrigation } from './shared';
import type { RuleScenario } from './types';

/**
 * Scenarios whose cause would be ambiguous next to this one, or that change who answers: with saturated or critical
 * soil, a manual lock, a silent node or an impossible humidity the sector would not irrigate for another reason.
 */
const AMBIGUOUS_WITH = ['R02', 'R04', 'LOCK', 'NODE_OFFLINE', 'INVALID_READING'];

/**
 * S-04, incomplete hardware: the soil asks for irrigation in a zone that has a sector with no electrovalve registered.
 * The pulse must abort the irrigation of that sector ("hardware incompleto", no alert) while the sectors that do have
 * their valve irrigate.
 *
 * The simulator does NOT remove a valve: the registry has no such operation and the simulator only reads it. It looks
 * for sectors that already lack theirs (see `sectorsWithoutValve`); the seeded nursery has hundreds. The dry soil is
 * pinned on the answers of the pulse (`pinnedReadings`) because that is the reading the engine decides on.
 */
export const incompleteHardware: RuleScenario = {
  key: 'INCOMPLETE_HARDWARE',
  label: 'Hardware incompleto (S-04)',
  description:
    'Sustrato seco en una zona con un sector sin su electroválvula registrada: el pulso aborta el riego de ese sector y los que sí la tienen riegan',
  branch: 'GLOBAL',
  backendRules: ['IncompleteHardwareRule'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 35,
  conflicts: AMBIGUOUS_WITH.map((other) => ({
    with: other,
    reason: () =>
      'Hardware incompleto necesita el sustrato seco (sin saturar ni crítico) para que se note que la causa es el hardware: con bloqueo, nodo mudo o una humedad imposible el sector tampoco regaría por otra razón. Ejecutalo por separado.',
  })),
  apply: (ctx) => ({
    pinnedReadings: { humSus: humidityDueForIrrigation(ctx) },
    needsSectorWithoutValve: true,
    notes: [
      'el simulador no da de baja ninguna válvula: busca en el registro de hardware los sectores de la zona que ya no tienen electroválvula',
      'el pulso con motor debe abortar el riego de esos sectores con «hardware incompleto» (sin alerta); mirá su traza en Motor de reglas → Inspector',
      'los sectores de la zona que sí tienen su electroválvula riegan con ese mismo sustrato',
      'la emisión automática tiene que estar activa: el sustrato seco viaja en la respuesta al pulso',
      'al terminar, soltá la zona en «Lecturas fijadas»',
    ],
  }),
};
