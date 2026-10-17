import { humidityDueForIrrigation, mid } from './shared';
import type { RuleScenario, ScenarioConflict } from './types';

/**
 * S-05, errors of the node (reglas_v2 §4). The real ESP32 detects the failure, cuts the actuator on its own and
 * publishes ONE error on `nursery/zone/{zona}/errores`; the backend marks the device as faulty and takes it out of the
 * autonomous control until somebody registers the repair. The simulator publishes the same payload on the broker, so the
 * backend reacts as it would to a real node. It does not simulate the hardware behind it (no flow meter exists yet).
 *
 * Each scenario belongs here because they share what makes them ambiguous: a lock, a silent node, an impossible
 * humidity or the incomplete-hardware scenario would stop the sector for another reason.
 */

/** Reason a fault cannot run next to a scenario that would stop (or change who answers for) the same sector. */
const AMBIGUOUS_REASON =
  'Una falla del nodo necesita que la causa de que no se riegue sea ella: con bloqueo, nodo mudo, una humedad imposible o hardware incompleto el sector tampoco regaría por otra razón. Ejecutalo por separado.';

const conflictsWith = (keys: string[], reason: string): ScenarioConflict[] =>
  keys.map((other) => ({ with: other, reason: () => reason }));

/** Scenarios that pin the soil humidity or make a sector stop for another reason. */
const AMBIGUOUS_WITH_FLOW = ['R02', 'R04', 'LOCK', 'NODE_OFFLINE', 'INVALID_READING', 'INCOMPLETE_HARDWARE'];

/** Flow of the overflow, in L/h: well above anything a valve lets through. */
const OVERFLOW_FLOW_LPH = 180;

/** Flow of a valve that is open and lets nothing through, in L/h. */
const NO_FLOW_LPH = 0;

export const faultNoFlow: RuleScenario = {
  key: 'FAULT_NO_FLOW',
  label: 'Sin caudal (S-05)',
  description:
    'El nodo informa SIN_CAUDAL en la electroválvula de un sector con el sustrato seco: queda en «Falla hidráulica» y el pulso no lo riega',
  branch: 'GLOBAL',
  backendRules: ['HydraulicFaultRule', 'HardwareFaultService'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 40,
  conflicts: [
    ...conflictsWith(AMBIGUOUS_WITH_FLOW, AMBIGUOUS_REASON),
    ...conflictsWith(
      ['FAULT_OVERFLOW'],
      'Sin caudal y caudal excesivo son dos fallas de la misma electroválvula: ejecutá una por vez.',
    ),
  ],
  apply: (ctx) => {
    const dry = humidityDueForIrrigation(ctx);
    return {
      pinnedReadings: {
        humSus: { value: dry.value, note: `${dry.note}: R-01 regaría este sector si no estuviera averiado` },
      },
      nodeError: { tipo: 'SIN_CAUDAL', valor: NO_FLOW_LPH },
      needsSectorWithValve: true,
      notes: [
        'el nodo publica SIN_CAUDAL en el tópico de errores del sector elegido; el backend marca su electroválvula como averiada',
        'el sustrato está bajo el umbral de riego: la alerta CRITICAL pide el riego manual del sector',
        'el pulso con motor no debe regarlo (ni R-02, que la v2 no exceptúa); mirá la traza en Motor de reglas → Inspector',
        'la emisión automática tiene que estar activa: el sustrato seco viaja en la respuesta al pulso',
        'al terminar, registrá la reparación (escenario «Reparación») y soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};

export const faultOverflow: RuleScenario = {
  key: 'FAULT_OVERFLOW',
  label: 'Caudal excesivo (S-05)',
  description:
    'El nodo informa CAUDAL_EXCESIVO en la electroválvula de un sector con el sustrato húmedo: «Falla hidráulica» sin pedido de riego manual',
  branch: 'GLOBAL',
  backendRules: ['HydraulicFaultRule', 'HardwareFaultService'],
  needsCooldownReset: true,
  needsDiagnosis: false,
  applyOrder: 41,
  conflicts: conflictsWith(AMBIGUOUS_WITH_FLOW, AMBIGUOUS_REASON),
  apply: ({ thresholds }) => {
    // Between the irrigation threshold and the saturation one: nothing is due and nothing blocks the irrigation.
    const wet = mid(thresholds.umbralHumedad, thresholds.saturacionBloqueo);
    return {
      pinnedReadings: {
        humSus: {
          value: wet,
          note: `humSus = ${wet}% (entre ${thresholds.umbralHumedad}% y ${thresholds.saturacionBloqueo}%): el sustrato no necesita riego`,
        },
      },
      nodeError: { tipo: 'CAUDAL_EXCESIVO', valor: OVERFLOW_FLOW_LPH },
      needsSectorWithValve: true,
      notes: [
        `el nodo publica CAUDAL_EXCESIVO (${OVERFLOW_FLOW_LPH} L/h) en el tópico de errores del sector elegido`,
        'con el sustrato húmedo la alerta es CRITICAL sin el pedido de riego manual',
        'la emisión automática tiene que estar activa: el sustrato húmedo viaja en la respuesta al pulso',
        'al terminar, registrá la reparación (escenario «Reparación») y soltá la zona en «Lecturas fijadas»',
      ],
    };
  },
};

export const faultShadeLimit: RuleScenario = {
  key: 'FAULT_SHADE_LIMIT',
  label: 'Falla de mediasombra (S-05)',
  description:
    'El nodo informa MEDIASOMBRA_FIN_DE_CARRERA (la malla no llegó a cerrar): «Falla mecánica en mediasombra» y la malla no recibe comandos',
  branch: 'MEDIASOMBRA',
  backendRules: ['ShadeFaultRule', 'HardwareFaultService'],
  needsCooldownReset: false,
  needsDiagnosis: false,
  applyOrder: 42,
  conflicts: conflictsWith(
    ['LOCK', 'NODE_OFFLINE', 'INCOMPLETE_HARDWARE'],
    'La falla de la mediasombra se nota en la rama de la malla: con un bloqueo manual, un nodo mudo o hardware incompleto esa rama tampoco actuaría por otra razón. Ejecutalo por separado.',
  ),
  apply: () => ({
    // The mesh is the macro-zone's: the error carries no sector.
    nodeError: { tipo: 'MEDIASOMBRA_FIN_DE_CARRERA', valor: 'CERRADA' },
    notes: [
      'el nodo publica MEDIASOMBRA_FIN_DE_CARRERA (final de «cerrada» sin activar) sin sector: la malla es de la macro-zona',
      'la alerta CRITICAL pide cubrir a mano; la malla de la zona no recibe comandos hasta registrar la reparación',
      'combinalo con «Mediasombra por pico UV» para ver que ShadingRule ya no mueve la malla',
    ],
  }),
};
